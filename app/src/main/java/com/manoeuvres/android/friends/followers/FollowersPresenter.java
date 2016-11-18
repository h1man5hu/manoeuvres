package com.manoeuvres.android.friends.followers;

import com.google.firebase.database.DatabaseReference;
import com.manoeuvres.android.friends.AbstractFriendsPresenter;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.Constants;

class FollowersPresenter extends AbstractFriendsPresenter {

    private static FollowersPresenter ourInstance;

    private FollowersPresenter() {
        super(
                Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWERS,
                Constants.MAX_FOLLOWERS_LISTENERS_COUNT,
                null
        );
    }

    public static FollowersPresenter getInstance() {
        if (ourInstance == null) {
            ourInstance = new FollowersPresenter();
        }
        return ourInstance;
    }

    void removeFollower(Friend friend) {
        FollowersDatabaseHelper.remove(friend.getFirebaseId());
    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return FollowersDatabaseHelper.getDataReference(userId);
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return FollowersDatabaseHelper.getCountReference(userId);
    }

    @Override
    protected int getInitialListCapacity() {
        return Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWERS;
    }

    @Override
    protected boolean isCachingEnabled() {
        return Constants.CACHE_FOLLOWERS;
    }

    @Override
    protected String getCacheKeyString() {
        return Constants.KEY_SHARED_PREF_DATA_FOLLOWERS;
    }

    @Override
    protected void notifyStartLoading(Object listener) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onStartFollowersLoading();
    }

    @Override
    protected void notifyInitialLoading(Object listener) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onFollowersInitialization();
    }

    @Override
    protected void notifyAddData(Object listener, int index, Friend friend) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onFollowerAdded(index, friend);
    }

    @Override
    protected void notifyChangeData(Object listener, int index, Friend friend) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onFollowerChanged(index, friend);
    }

    @Override
    protected void notifyRemoveData(Object listener, int index, Friend friend) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onFollowerRemoved(index, friend);
    }

    @Override
    protected void notifyCompleteLoading(Object listener) {
        FollowersListener observer = (FollowersListener) listener;
        observer.onCompleteFollowersLoading();
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    interface FollowersListener {
        void onStartFollowersLoading();

        void onFollowersInitialization();

        void onFollowerAdded(int index, Friend friend);

        void onFollowerChanged(int index, Friend friend);

        void onFollowerRemoved(int index, Friend friend);

        void onCompleteFollowersLoading();
    }
}
