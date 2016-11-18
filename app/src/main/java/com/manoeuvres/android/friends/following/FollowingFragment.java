package com.manoeuvres.android.friends.following;

import android.widget.Button;

import com.manoeuvres.android.R;
import com.manoeuvres.android.friends.AbstractFriendsFragment;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.UniqueId;

public class FollowingFragment extends AbstractFriendsFragment implements FollowingListener {

    private FollowingPresenter mFollowingPresenter;

    public FollowingFragment() {
        // Required empty public constructor
    }

    public static FollowingFragment newInstance() {
        return new FollowingFragment();
    }

    @Override
    public void onStart() {
        super.onStart();

        mFollowingPresenter =
                FollowingPresenter.getInstance(getApplicationContext());
        mFollowingPresenter.attach(this);
        mFollowingPresenter.sync();

        if (mFollowingPresenter.isLoaded()) {
            onCompleteFollowingLoading();
        }
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_following;
    }

    @Override
    protected int getActivityTitleId() {
        return R.string.title_activity_main_following;
    }

    @Override
    protected String getLoadingText() {
        return getString(R.string.textview_loading_following);
    }

    @Override
    protected int getNoFriendsTextId() {
        return R.string.no_following;
    }

    @Override
    protected void updateViewHolder(ViewAdapter.RecyclerViewHolder holder, Friend friend) {

    }

    @Override
    protected void initializeButton(Button button) {
        button.setText(R.string.button_text_unfollow);
    }

    @Override
    protected void onClick(Button button, int adapterPosition) {
        FacebookFriendsPresenter facebookFriendsPresenter = getFacebookFriendsPresenter();
        button.setText(R.string.button_text_unfollowing);
        final Friend friend = facebookFriendsPresenter.get(
                facebookFriendsPresenter.indexOf(mFollowingPresenter.get(
                        adapterPosition)
                )
        );
        mFollowingPresenter.unfollowFriend(friend);
    }

    @Override
    protected Friend getFriend(int position) {
        return mFollowingPresenter.get(position);
    }

    @Override
    protected int getFriendsCount() {
        return mFollowingPresenter.size();
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
        getAdapter().notifyItemInserted(index);
        cancelNotification(UniqueId.getFollowingId(friend));
    }

    @Override
    public void onFollowingChanged(int index, Friend friend) {
        getAdapter().notifyItemChanged(index);
    }

    @Override
    public void onFollowingRemoved(int index, Friend friend) {
        getAdapter().notifyItemRemoved(index);
        cancelNotification(UniqueId.getFollowingId(friend));
    }

    @Override
    public void onCompleteFollowingLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mFollowingPresenter = (FollowingPresenter) mFollowingPresenter.detach(this);
    }
}
