package com.manoeuvres.android.friends;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.manoeuvres.android.R;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.database.CompletionListeners;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.views.DividerItemDecoration;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.content.Context.NOTIFICATION_SERVICE;

public abstract class AbstractFriendsFragment extends Fragment {

    @BindView(R.id.recycler_view_friends)
    RecyclerView mRecyclerView;
    @BindView(R.id.progress_bar_friends)
    ProgressBar mProgressBar;
    @BindView(R.id.textView_background_friends)
    TextView mBackgroundTextView;
    private MainActivity mParentActivity;
    private RecyclerView.Adapter mAdapter;
    private NotificationManager mNotificationManager;
    private Menu mNavigationMenu;
    private FacebookFriendsPresenter mFacebookFriendsPresenter;

    public AbstractFriendsFragment() {

    }

    public RecyclerView.Adapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mParentActivity = (MainActivity) context;
        mNotificationManager =
                (NotificationManager) mParentActivity.getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_friends, container, false);
        ButterKnife.bind(this, rootView);

        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new ViewAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mParentActivity));

        NavigationView navigationView = ButterKnife.findById(mParentActivity, R.id.nav_view);
        mNavigationMenu = navigationView.getMenu();

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mParentActivity.setTitle(getActivityTitleId());
        mNavigationMenu.findItem(getNavigationMenuItemId()).setChecked(true);

        mFacebookFriendsPresenter =
                FacebookFriendsPresenter.getInstance(mParentActivity.getApplicationContext());
        mFacebookFriendsPresenter.sync();
    }

    protected abstract int getNavigationMenuItemId();

    protected abstract int getActivityTitleId();

    protected void showProgress() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mBackgroundTextView.setText(getLoadingText());
        mBackgroundTextView.setVisibility(View.VISIBLE);
    }

    protected abstract String getLoadingText();

    protected void hideProgress() {
        if (getFriendsCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mBackgroundTextView.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundTextView.setText(getNoFriendsTextId());
            mBackgroundTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    protected abstract int getNoFriendsTextId();

    protected abstract void updateViewHolder(ViewAdapter.RecyclerViewHolder holder, Friend friend);

    protected FacebookFriendsPresenter getFacebookFriendsPresenter() {
        return mFacebookFriendsPresenter;
    }

    protected Context getApplicationContext() {
        return mParentActivity.getApplicationContext();
    }

    protected void cancelNotification(int notificationId) {
        mNotificationManager.cancel(notificationId);
    }

    protected abstract void initializeButton(Button button);

    protected abstract void onClick(Button button, int adapterPosition);

    protected abstract Friend getFriend(int position);

    protected abstract int getFriendsCount();

    public class ViewAdapter extends RecyclerView.Adapter<ViewAdapter.RecyclerViewHolder> {

        @Override
        public RecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.list_item_friend, parent, false
            );
            return (new RecyclerViewHolder(v));
        }

        @Override
        public void onBindViewHolder(final RecyclerViewHolder holder, int position) {
            if (getFriendsCount() > position) {
                Friend friend = getFriend(position);
                int index = mFacebookFriendsPresenter.indexOf(friend);
                if (index != -1) {
                    friend = mFacebookFriendsPresenter.get(index);
                    String name = friend.getName();
                    if (!name.equals("")) {
                        holder.mFriendName.setText(friend.getName());
                    } else {
                        mFacebookFriendsPresenter.getUserName(
                                friend.getFirebaseId(),
                                new CompletionListeners.GetStringListener() {
                                    @Override
                                    public void onComplete(String name) {
                                        holder.mFriendName.setText(name);
                                    }

                                    @Override
                                    public void onFailed() {

                                    }
                                });
                    }
                    updateViewHolder(holder, friend);
                }
            }
        }

        @Override
        public int getItemCount() {
            return getFriendsCount();
        }

        public class RecyclerViewHolder extends ViewHolder {
            @BindView(R.id.button_list_item_friend)
            public Button mButton;
            @BindView(R.id.textView_list_item_friend_name)
            TextView mFriendName;

            RecyclerViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
                initializeButton(mButton);
            }

            @OnClick(R.id.button_list_item_friend)
            void onClick() {
                AbstractFriendsFragment.this.onClick(mButton, getAdapterPosition());
            }
        }
    }

}
