package com.manoeuvres.android.friends.findfriends;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.manoeuvres.android.R;
import com.manoeuvres.android.friends.following.FollowingPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.views.DividerItemDecoration;

import java.util.ArrayList;
import java.util.List;


public class FindFriendsFragment extends Fragment implements FollowingListener {


    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private FollowingPresenter mFollowingPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mBackgroundTextView;

    private List<Friend> mUnfollowedFriends;


    public FindFriendsFragment() {
        // Required empty public constructor
    }

    public static FindFriendsFragment newInstance() {
        return new FindFriendsFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mMainActivity = (MainActivity) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_friends, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_friends);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new FindFriendsViewAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mMainActivity));

        NavigationView navigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = navigationView.getMenu();

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mBackgroundTextView = (TextView) rootView.findViewById(R.id.textView_background_friends);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(R.string.title_activity_main_find_friends);
        mNavigationMenu.findItem(R.id.nav_find_friends).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext())
                .loadCache(PreferenceManager.getDefaultSharedPreferences(mMainActivity.getApplicationContext()))
                .sync();

        mFollowingPresenter = FollowingPresenter.getInstance(mMainActivity.getApplicationContext())
                .attach(this)
                .sync();

        if (mUnfollowedFriends == null || mUnfollowedFriends.size() == 0) showProgress();
        if (mFollowingPresenter.isLoaded()) showData();
    }

    private void showData() {
        hideProgress();
        mUnfollowedFriends = new ArrayList<>(mFacebookFriendsPresenter.getAll());
        mUnfollowedFriends.removeAll(mFollowingPresenter.getAll());
        mAdapter.notifyDataSetChanged();
    }

    private void showProgress() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mBackgroundTextView.setText(String.format(getString(R.string.textview_loading_friends), getString(R.string.text_loading_friends_find_friends)));
        mBackgroundTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        int unfollowedFriends = mFacebookFriendsPresenter.size() - mFollowingPresenter.size();
        if (unfollowedFriends > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mBackgroundTextView.setVisibility(View.INVISIBLE);
        } else {
            if (mFacebookFriendsPresenter.size() == 0) {
                mBackgroundTextView.setText(R.string.no_friends);
            } else {
                mBackgroundTextView.setText(R.string.no_new_friends);
            }
            mBackgroundTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStartFollowingLoading() {

    }

    @Override
    public void onFollowingInitialization() {
        showProgress();
    }

    @Override
    public void onFollowingAdded(int index, Friend friend) {
        if (mUnfollowedFriends != null) {
            int removedIndex = mUnfollowedFriends.indexOf(friend);
            if (removedIndex != -1) {
                mUnfollowedFriends.remove(friend);
                mAdapter.notifyItemRemoved(removedIndex);
            }
        }
    }

    @Override
    public void onFollowingChanged(int index, Friend friend) {

    }

    @Override
    public void onFollowingRemoved(int index, Friend friend) {

    }

    @Override
    public void onCompleteFollowingLoading() {
        showData();
    }

    @Override
    public void onStop() {
        super.onStop();

        mFollowingPresenter.detach(this);
    }

    public class FindFriendsViewAdapter extends RecyclerView.Adapter<FindFriendsViewAdapter.ViewHolder> {

        @Override
        public FindFriendsViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(final FindFriendsViewAdapter.ViewHolder holder, int position) {
            if (mUnfollowedFriends.size() > position) {
                Friend friend = mUnfollowedFriends.get(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    holder.mFriendName.setText(friend.getName());
                    mFacebookFriendsPresenter.isRequested(friend, new FacebookFriendsPresenter.RequestListener() {
                        @Override
                        public void onComplete(boolean isRequested) {
                            if (isRequested)
                                holder.mButton.setText(R.string.button_text_cancel_request);
                            else holder.mButton.setText(R.string.button_text_follow);
                            holder.mButton.setClickable(true);
                        }

                        @Override
                        public void onFailed() {
                            holder.mButton.setText(R.string.button_text_network_error);
                            holder.mButton.setClickable(false);
                        }
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            if (mUnfollowedFriends != null) return mUnfollowedFriends.size();
            else return 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView mFriendName;
            Button mButton;

            ViewHolder(View view) {
                super(view);
                mFriendName = (TextView) view.findViewById(R.id.textView_list_item_friend_name);
                mButton = (Button) view.findViewById(R.id.button_list_item_friend);
                mButton.setOnClickListener(this);
                mButton.setText(R.string.button_text_loading);
                mButton.setClickable(false);
            }

            @Override
            public void onClick(View view) {
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mUnfollowedFriends.get(getAdapterPosition())));
                if (mButton.getText().equals(getString(R.string.button_text_follow))) {
                    mButton.setText(R.string.button_text_following);
                    mButton.setClickable(false);
                    mFacebookFriendsPresenter.followFriend(friend, new FacebookFriendsPresenter.FollowFriendListener() {
                        @Override
                        public void onRequested() {
                            mButton.setText(R.string.button_text_cancel_request);
                            mButton.setClickable(true);
                        }

                        @Override
                        public void onFailed() {
                            mButton.setText(R.string.button_text_follow);
                            mButton.setClickable(true);
                        }
                    });
                } else if (mButton.getText().equals(getString(R.string.button_text_cancel_request))) {
                    mButton.setText(R.string.button_text_cancelling);
                    mButton.setClickable(false);
                    mFacebookFriendsPresenter.cancelRequest(friend, new FacebookFriendsPresenter.CancelRequestListener() {
                        @Override
                        public void onRequestCancelled() {
                            mButton.setText(R.string.button_text_follow);
                            mButton.setClickable(true);
                        }

                        @Override
                        public void onFailed() {
                            mButton.setText(R.string.button_text_cancel_request);
                            mButton.setClickable(true);
                        }
                    });
                }
            }
        }
    }
}
