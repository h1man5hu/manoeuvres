package com.manoeuvres.android.friends.findfriends;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
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
import com.manoeuvres.android.database.DatabaseHelper;
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

    private NavigationView mNavigationView;
    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mLoadingTextView;

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

        mNavigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = mNavigationView.getMenu();

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_friends);
        mLoadingTextView.setText(String.format(getString(R.string.textview_loading_friends), getString(R.string.text_loading_friends_find_friends)));

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(R.string.title_activity_main_find_friends);
        mNavigationMenu.findItem(R.id.nav_find_friends).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext())
                .updateFacebookFriends();

        mFollowingPresenter = FollowingPresenter.getInstance(mMainActivity.getApplicationContext())
                .attach(this)
                .sync();

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
        mLoadingTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mProgressBar.setVisibility(View.INVISIBLE);
        mLoadingTextView.setVisibility(View.INVISIBLE);
        mRecyclerView.setVisibility(View.VISIBLE);
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
                    holder.mButton.setText(R.string.button_text_follow);
                    DatabaseHelper.isRequested(friend, new DatabaseHelper.RequestListener() {
                        @Override
                        public void onComplete(boolean isRequested) {
                            if (isRequested)
                                holder.mButton.setText(R.string.button_text_cancel_request);
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
                mButton.setText(R.string.button_text_follow);
            }

            @Override
            public void onClick(View view) {
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mUnfollowedFriends.get(getAdapterPosition())));
                if (mButton.getText().equals(getString(R.string.button_text_follow))) {
                    DatabaseHelper.followFriend(friend, new DatabaseHelper.FollowFriendListener() {
                        @Override
                        public void onRequested() {
                            mButton.setText(R.string.button_text_cancel_request);
                        }
                    });
                } else if (mButton.getText().equals(getString(R.string.button_text_cancel_request))) {
                    DatabaseHelper.cancelRequest(friend, new DatabaseHelper.CancelRequestListener() {
                        @Override
                        public void onCancelled() {
                            mButton.setText(R.string.button_text_follow);
                        }
                    });
                }
            }
        }
    }
}
