package com.manoeuvres.android.views.fragments;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
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
import com.manoeuvres.android.presenters.DatabaseHelper;
import com.manoeuvres.android.presenters.FacebookFriendsPresenter;
import com.manoeuvres.android.presenters.FollowingPresenter;
import com.manoeuvres.android.presenters.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.UniqueId;
import com.manoeuvres.android.views.activities.MainActivity;
import com.manoeuvres.android.views.viewdecorators.DividerItemDecoration;


public class FollowingFragment extends Fragment implements FollowingListener {

    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private FollowingPresenter mFollowingPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private NavigationView mNavigationView;
    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mLoadingTextView;

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

        mNavigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = mNavigationView.getMenu();

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_friends);
        mLoadingTextView.setText(getString(R.string.textview_loading_following));

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(R.string.title_activity_main_following);
        mNavigationMenu.findItem(R.id.nav_following).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext());
        mFacebookFriendsPresenter.updateFacebookFriends();

        mFollowingPresenter = FollowingPresenter.getInstance(mMainActivity.getApplicationContext())
                .attach(this)
                .sync();
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
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mFollowingPresenter.get(getAdapterPosition())));
                DatabaseHelper.unfollowFriend(friend);
            }
        }
    }
}
