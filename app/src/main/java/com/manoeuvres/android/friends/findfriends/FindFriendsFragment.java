package com.manoeuvres.android.friends.findfriends;

import android.widget.Button;

import com.manoeuvres.android.R;
import com.manoeuvres.android.database.CompletionListeners.GetBooleanListener;
import com.manoeuvres.android.friends.AbstractFriendsFragment;
import com.manoeuvres.android.friends.following.FollowingPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;

import java.util.ArrayList;
import java.util.List;

public class FindFriendsFragment extends AbstractFriendsFragment implements FollowingListener {

    private FollowingPresenter mFollowingPresenter;
    private List<Friend> mUnfollowedFriends;

    public static FindFriendsFragment newInstance() {
        return new FindFriendsFragment();
    }

    @Override
    public void onStart() {
        super.onStart();

        mFollowingPresenter =
                FollowingPresenter.getInstance(getApplicationContext());
        mFollowingPresenter.attach(this);
        mFollowingPresenter.sync();

        if (mUnfollowedFriends == null || mUnfollowedFriends.size() == 0) {
            showProgress();
        }
        if (mFollowingPresenter.isLoaded()) {
            showData();
        }
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_find_friends;
    }

    @Override
    protected int getActivityTitleId() {
        return R.string.title_activity_main_find_friends;
    }

    private void showData() {
        mUnfollowedFriends = new ArrayList<>(getFacebookFriendsPresenter().getAll());
        mUnfollowedFriends.removeAll(mFollowingPresenter.getAll());
        hideProgress();
        getAdapter().notifyDataSetChanged();
    }

    @Override
    protected String getLoadingText() {
        return String.format(
                getString(R.string.textview_loading_friends),
                getString(R.string.text_loading_friends_find_friends)
        );
    }

    @Override
    protected int getNoFriendsTextId() {
        if (getFacebookFriendsPresenter().size() == 0) {
            return R.string.no_friends;
        } else {
            return R.string.no_new_friends;
        }
    }

    @Override
    protected void updateViewHolder(final ViewAdapter.RecyclerViewHolder holder, Friend friend) {
        getFacebookFriendsPresenter().isRequested(friend, new GetBooleanListener() {
            @Override
            public void onComplete(boolean isRequested) {
                if (isRequested) {
                    holder.mButton.setText(R.string.button_text_cancel_request);
                } else {
                    holder.mButton.setText(R.string.button_text_follow);
                }
                holder.mButton.setClickable(true);
            }

            @Override
            public void onFailed() {
                holder.mButton.setText(R.string.button_text_network_error);
                holder.mButton.setClickable(false);
            }
        });
    }

    @Override
    protected void initializeButton(Button button) {
        button.setText(R.string.button_text_loading);
        button.setClickable(false);
    }

    @Override
    protected void onClick(final Button button, int adapterPosition) {
        FacebookFriendsPresenter facebookFriendsPresenter = getFacebookFriendsPresenter();
        final Friend friend = facebookFriendsPresenter.get(
                facebookFriendsPresenter.indexOf(
                        mUnfollowedFriends.get(adapterPosition)
                )
        );
        if (button.getText().equals(getString(R.string.button_text_follow))) {
            button.setText(R.string.button_text_following);
            button.setClickable(false);
            facebookFriendsPresenter.followFriend(friend, new OnCompleteListener() {
                @Override
                public void onComplete() {
                    button.setText(R.string.button_text_cancel_request);
                    button.setClickable(true);
                }

                @Override
                public void onFailed() {
                    button.setText(R.string.button_text_follow);
                    button.setClickable(true);
                }
            });
        } else if (button.getText().equals(getString(R.string.button_text_cancel_request))) {
            button.setText(R.string.button_text_cancelling);
            button.setClickable(false);
            facebookFriendsPresenter.cancelRequest(friend, new OnCompleteListener() {
                @Override
                public void onComplete() {
                    button.setText(R.string.button_text_follow);
                    button.setClickable(true);
                }

                @Override
                public void onFailed() {
                    button.setText(R.string.button_text_cancel_request);
                    button.setClickable(true);
                }
            });
        }
    }

    @Override
    protected Friend getFriend(int position) {
        if (position >= 0) {
            Friend friend = mUnfollowedFriends.get(position);
            if (friend != null) {
                return friend;
            }
        }
        return null;
    }

    @Override
    protected int getFriendsCount() {
        if (mUnfollowedFriends != null) {
            return mUnfollowedFriends.size();
        } else {
            return 0;
        }
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
                getAdapter().notifyItemRemoved(removedIndex);
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

        mFollowingPresenter = (FollowingPresenter) mFollowingPresenter.detach(this);
    }
}
