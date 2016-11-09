package com.manoeuvres.android.friends.following;


import android.app.Activity;
import android.app.NotificationManager;
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
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.UniqueId;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.views.DividerItemDecoration;


public class FollowingFragment extends Fragment implements FollowingListener {

    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private FollowingPresenter mFollowingPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mBackgroundTextView;

    private NotificationManager mNotificationManager;


    public FollowingFragment() {
        // Required empty public constructor
    }

    public static FollowingFragment newInstance() {
        return new FollowingFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mMainActivity = (MainActivity) context;
        mNotificationManager = (NotificationManager) mMainActivity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_friends, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_friends);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new FollowingViewAdapter();
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

        mMainActivity.setTitle(R.string.title_activity_main_following);
        mNavigationMenu.findItem(R.id.nav_following).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter
                .getInstance(mMainActivity.getApplicationContext())
                .loadCache(PreferenceManager.getDefaultSharedPreferences(mMainActivity.getApplicationContext()))
                .sync();

        mFollowingPresenter = FollowingPresenter.getInstance(mMainActivity.getApplicationContext())
                .attach(this)
                .sync();

        if (mFollowingPresenter.isLoaded()) onCompleteFollowingLoading();
    }

    private void showProgress() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mBackgroundTextView.setText(getString(R.string.textview_loading_following));
        mBackgroundTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        if (mFollowingPresenter.size() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mBackgroundTextView.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundTextView.setText(R.string.no_following);
            mBackgroundTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStartFollowingLoading() {
        showProgress();
    }

    @Override
    public void onFollowingInitialization() {

    }

    @Override
    public void onFollowingAdded(int index, Friend friend) {
        mAdapter.notifyItemInserted(index);
        mNotificationManager.cancel(UniqueId.getFollowingId(friend));
    }

    @Override
    public void onFollowingChanged(int index, Friend friend) {
        mAdapter.notifyItemChanged(index);
    }

    @Override
    public void onFollowingRemoved(int index, Friend friend) {
        mAdapter.notifyItemRemoved(index);
        mNotificationManager.cancel(UniqueId.getFollowingId(friend));
    }

    @Override
    public void onCompleteFollowingLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mFollowingPresenter.detach(this);
    }

    public class FollowingViewAdapter extends RecyclerView.Adapter<FollowingViewAdapter.ViewHolder> {

        @Override
        public FollowingViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(FollowingViewAdapter.ViewHolder holder, int position) {
            if (mFollowingPresenter.size() > position) {
                Friend friend = mFollowingPresenter.get(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    holder.mFriendName.setText(friend.getName());
                }
            }
        }

        @Override
        public int getItemCount() {
            return mFollowingPresenter.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView mFriendName;
            Button mButton;

            ViewHolder(View view) {
                super(view);
                mFriendName = (TextView) view.findViewById(R.id.textView_list_item_friend_name);
                mButton = (Button) view.findViewById(R.id.button_list_item_friend);
                mButton.setOnClickListener(this);
                mButton.setText(R.string.button_text_unfollow);
            }

            @Override
            public void onClick(View view) {
                mButton.setText(R.string.button_text_unfollowing);
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mFollowingPresenter.get(getAdapterPosition())));
                mFollowingPresenter.unfollowFriend(friend);
            }
        }
    }
}
