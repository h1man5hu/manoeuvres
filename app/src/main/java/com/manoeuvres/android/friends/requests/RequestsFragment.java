package com.manoeuvres.android.friends.requests;


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
import com.manoeuvres.android.friends.requests.RequestsPresenter.RequestsListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.UniqueId;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.views.DividerItemDecoration;

import java.util.List;


public class RequestsFragment extends Fragment implements RequestsListener {

    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private RequestsPresenter mRequestsPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mBackgroundTextView;

    private NotificationManager mNotificationManager;


    public RequestsFragment() {
        // Required empty public constructor
    }

    public static RequestsFragment newInstance() {
        return new RequestsFragment();
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
        mAdapter = new RequestsViewAdapter();
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

        mMainActivity.setTitle(R.string.title_activity_main_requests);
        mNavigationMenu.findItem(R.id.nav_requests).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext())
                .loadCache(PreferenceManager.getDefaultSharedPreferences(mMainActivity.getApplicationContext()))
                .sync();

        mRequestsPresenter = RequestsPresenter.getInstance()
                .attach(this)
                .sync();

        if (mRequestsPresenter.isLoaded()) {
            List<Friend> requests = mRequestsPresenter.getAll();
            for (int i = 0; i < requests.size(); i++) {
                Friend friend = requests.get(i);
                mNotificationManager.cancel(UniqueId.getRequestId(friend));
            }
            onCompleteRequestsLoading();
            mAdapter.notifyDataSetChanged();
        }
    }

    private void showProgress() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mBackgroundTextView.setText(String.format(getString(R.string.textview_loading_friends), getString(R.string.text_loading_friends_requests)));
        mBackgroundTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        if (mRequestsPresenter.size() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mBackgroundTextView.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundTextView.setText(R.string.no_requests);
            mBackgroundTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStartRequestsLoading() {

    }

    @Override
    public void onRequestsInitialization() {
        showProgress();
    }

    @Override
    public void onRequestAdded(int index, Friend friend) {
        mAdapter.notifyItemInserted(index);
        mNotificationManager.cancel(UniqueId.getRequestId(friend));
    }

    @Override
    public void onRequestRemoved(int index, Friend friend) {
        mAdapter.notifyItemRemoved(index);
        mNotificationManager.cancel(UniqueId.getRequestId(friend));
    }

    @Override
    public void onCompleteRequestsLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mRequestsPresenter.pushSeenRequests(mRequestsPresenter.getAll());
        mRequestsPresenter.detach(this);
    }

    public class RequestsViewAdapter extends RecyclerView.Adapter<RequestsViewAdapter.ViewHolder> {

        @Override
        public RequestsViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(final RequestsViewAdapter.ViewHolder holder, int position) {
            if (mRequestsPresenter.size() > position) {
                Friend friend = mRequestsPresenter.get(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    String name = friend.getName();
                    if (!name.equals("")) holder.mFriendName.setText(friend.getName());
                    else
                        mFacebookFriendsPresenter.getUserName(friend.getFirebaseId(), new FacebookFriendsPresenter.GetNameListener() {
                            @Override
                            public void onLoaded(String name) {
                                holder.mFriendName.setText(name);
                            }

                            @Override
                            public void onFailed() {

                            }
                        });
                }
            }
        }

        @Override
        public int getItemCount() {
            return mRequestsPresenter.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView mFriendName;
            Button mButton;

            ViewHolder(View view) {
                super(view);
                mFriendName = (TextView) view.findViewById(R.id.textView_list_item_friend_name);
                mButton = (Button) view.findViewById(R.id.button_list_item_friend);
                mButton.setOnClickListener(this);
                mButton.setText(R.string.button_text_accept);
            }

            @Override
            public void onClick(View view) {
                mButton.setText(R.string.button_text_accepting);
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mRequestsPresenter.get(getAdapterPosition())));
                mRequestsPresenter.acceptRequest(friend);
            }
        }
    }
}
