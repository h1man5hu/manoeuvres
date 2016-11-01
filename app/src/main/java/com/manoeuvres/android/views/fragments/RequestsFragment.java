package com.manoeuvres.android.views.fragments;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.gson.Gson;
import com.manoeuvres.android.R;
import com.manoeuvres.android.presenters.DatabaseHelper;
import com.manoeuvres.android.presenters.FacebookFriendsPresenter;
import com.manoeuvres.android.presenters.RequestsPresenter;
import com.manoeuvres.android.presenters.RequestsPresenter.RequestsListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.UniqueId;
import com.manoeuvres.android.views.activities.MainActivity;
import com.manoeuvres.android.views.viewdecorators.DividerItemDecoration;


public class RequestsFragment extends Fragment implements RequestsListener {

    private Activity mMainActivity;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private RequestsPresenter mRequestsPresenter;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private NavigationView mNavigationView;
    private Menu mNavigationMenu;

    private ProgressBar mProgressBar;
    private TextView mLoadingTextView;

    private NotificationManager mNotificationManager;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

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

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mMainActivity.getApplicationContext());
        mGson = new Gson();
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

        mNavigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = mNavigationView.getMenu();

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_friends);
        mLoadingTextView.setText(String.format(getString(R.string.textview_loading_friends), getString(R.string.text_loading_friends_requests)));

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(R.string.title_activity_main_requests);
        mNavigationMenu.findItem(R.id.nav_requests).setChecked(true);

        mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(mMainActivity.getApplicationContext())
                .updateFacebookFriends();

        mRequestsPresenter = RequestsPresenter.getInstance()
                .attach(this)
                .sync();

        if (mRequestsPresenter.isLoaded()) {
            for (Friend friend : mRequestsPresenter.getAll())
                mNotificationManager.cancel(UniqueId.getRequestId(friend));
        }

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
        //mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_REQUESTS, mGson.toJson(mRequestsPresenter.getAll())).apply();
    }

    @Override
    public void onStop() {
        super.onStop();

        mRequestsPresenter.detach(this);
        DatabaseHelper.pushSeenRequests(mRequestsPresenter.getAll());
    }

    public class RequestsViewAdapter extends RecyclerView.Adapter<RequestsViewAdapter.ViewHolder> {

        @Override
        public RequestsViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(RequestsViewAdapter.ViewHolder holder, int position) {
            if (mRequestsPresenter.size() > position) {
                Friend friend = mRequestsPresenter.get(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    holder.mFriendName.setText(friend.getName());
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
                final Friend friend = mFacebookFriendsPresenter.get(mFacebookFriendsPresenter.indexOf(mRequestsPresenter.get(getAdapterPosition())));
                DatabaseHelper.acceptRequest(friend);
            }
        }
    }
}
