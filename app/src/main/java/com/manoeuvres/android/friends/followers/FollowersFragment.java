package com.manoeuvres.android.friends.followers;

import android.widget.Button;

import com.manoeuvres.android.R;
import com.manoeuvres.android.friends.AbstractFriendsFragment;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.followers.FollowersPresenter.FollowersListener;
import com.manoeuvres.android.friends.Friend;

public class FollowersFragment extends AbstractFriendsFragment implements FollowersListener {

    private FollowersPresenter mFollowersPresenter;

    public FollowersFragment() {
        // Required empty public constructor
    }

    public static FollowersFragment newInstance() {
        return new FollowersFragment();
    }

    @Override
    public void onStart() {
        super.onStart();

        mFollowersPresenter = FollowersPresenter.getInstance();
        mFollowersPresenter.attach(this);
        mFollowersPresenter.sync();
        if (mFollowersPresenter.isLoaded()) {
            onCompleteFollowersLoading();
        }
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_followers;
    }

    @Override
    protected int getActivityTitleId() {
        return R.string.title_activity_main_followers;
    }

    @Override
    protected String getLoadingText() {
        return String.format(
                getString(R.string.textview_loading_friends),
                getString(R.string.text_loading_friends_followers)
        );
    }

    @Override
    protected int getNoFriendsTextId() {
        return R.string.no_followers;
    }

    @Override
    protected void updateViewHolder(ViewAdapter.RecyclerViewHolder holder, Friend friend) {

    }

    @Override
    protected void initializeButton(Button button) {
        button.setText(R.string.button_text_remove);
    }

    @Override
    protected void onClick(Button button, int adapterPosition) {
        FacebookFriendsPresenter facebookFriendsPresenter = getFacebookFriendsPresenter();
        button.setText(R.string.button_text_removing);
        final Friend friend = facebookFriendsPresenter.get(
                facebookFriendsPresenter.indexOf(
                        mFollowersPresenter.get(adapterPosition)
                )
        );
        mFollowersPresenter.removeFollower(friend);
    }

    @Override
    protected Friend getFriend(int position) {
        return mFollowersPresenter.get(position);
    }

    @Override
    protected int getFriendsCount() {
        return mFollowersPresenter.size();
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
        getAdapter().notifyItemInserted(index);
    }

    @Override
    public void onFollowerChanged(int index, Friend friend) {
        getAdapter().notifyItemChanged(index);
    }

    @Override
    public void onFollowerRemoved(int index, Friend friend) {
        getAdapter().notifyItemRemoved(index);
    }

    @Override
    public void onCompleteFollowersLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mFollowersPresenter = (FollowersPresenter) mFollowersPresenter.detach(this);
    }
}
