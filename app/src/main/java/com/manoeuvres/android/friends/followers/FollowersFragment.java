package com.manoeuvres.android.friends.followers;


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
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.followers.FollowersPresenter.FollowersListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.views.DividerItemDecoration;


public class FollowersFragment extends Fragment implements FollowersListener {

    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private FollowersPresenter mFollowersPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private NavigationView mNavigationView;
    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mLoadingTextView;


    public FollowersFragment() {
        // Required empty public constructor
    }

    public static FollowersFragment newInstance() {
        return new FollowersFragment();
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
        mAdapter = new FollowersViewAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mMainActivity));

        mNavigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = mNavigationView.getMenu();

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_friends);
        mLoadingTextView.setText(String.format(getString(R.string.textview_loading_friends), getString(R.string.text_loading_friends_followers)));

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(R.string.title_activity_main_followers);
        mNavigationMenu.findItem(R.id.nav_followers).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext())
                .updateFacebookFriends();

        mFollowersPresenter = FollowersPresenter.getInstance()
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
    public void onStartFollowersLoading() {

    }

    @Override
    public void onFollowersInitialization() {
        showProgress();
    }

    @Override
    public void onFollowerAdded(int index, Friend friend) {
        mAdapter.notifyItemInserted(index);
    }

    @Override
    public void onFollowerRemoved(int index, Friend friend) {
        mAdapter.notifyItemRemoved(index);
    }

    @Override
    public void onCompleteFollowersLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mFollowersPresenter.detach(this);
    }

    public class FollowersViewAdapter extends RecyclerView.Adapter<FollowersViewAdapter.ViewHolder> {

        @Override
        public FollowersViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(FollowersViewAdapter.ViewHolder holder, int position) {
            if (mFollowersPresenter.size() > position) {
                Friend friend = mFollowersPresenter.get(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    holder.mFriendName.setText(friend.getName());
                }
            }
        }

        @Override
        public int getItemCount() {
            return mFollowersPresenter.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView mFriendName;
            Button mButton;

            ViewHolder(View view) {
                super(view);
                mFriendName = (TextView) view.findViewById(R.id.textView_list_item_friend_name);
                mButton = (Button) view.findViewById(R.id.button_list_item_friend);
                mButton.setOnClickListener(this);
                mButton.setText(R.string.button_text_remove);
            }

            @Override
            public void onClick(View view) {
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mFollowersPresenter.get(getAdapterPosition())));
                DatabaseHelper.removeFollower(friend);
            }
        }
    }
}
