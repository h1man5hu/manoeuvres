package com.manoeuvres.android.friends.following;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.AbstractFriendsPresenter;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners;
import com.manoeuvres.android.database.CompletionListeners.GetFriendListener;
import com.manoeuvres.android.util.Constants;

import java.lang.reflect.Type;
import java.util.List;

public class FollowingPresenter extends AbstractFriendsPresenter {

    private static FollowingPresenter ourInstance;

    private FollowingPresenter(Context applicationContext) {
        super(
                Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING,
                Constants.MAX_FOLLOWING_LISTENERS_COUNT,
                applicationContext
        );
    }

    public static FollowingPresenter getInstance(@NonNull Context applicationContext) {
        if (ourInstance == null) {
            ourInstance = new FollowingPresenter(applicationContext);
        }
        return ourInstance;
    }

    void unfollowFriend(Friend friend) {
        FollowingDatabaseHelper.remove(friend.getFirebaseId());
    }

    /*
     * The MainActivity updates the cache when it is stopped. If a friend is removed in the
     * background, this method compares the new friends with the cached list, and returns a list of
     * friends which were removed, if any.
     */
    public List<Friend> getRemovedFriendsWhenActivityWasStopped() {
        String friendList = super.getSharedPreferences().getString(
                Constants.KEY_SHARED_PREF_MENU_ITEMS_FOLLOWING,
                ""
        );
        Type type = new TypeToken<List<Friend>>() {
        }.getType();
        List<Friend> following = new Gson().fromJson(friendList, type);
        if (following != null && super.getAll() != null) {
            following.removeAll(super.getAll());
        }
        return following;
    }

    @Override
    protected void addFriend(Friend friend,
                             final List<Friend> updatedFollowingList,
                             final CompletionListeners.OnCompleteListener listener) {
        final String firebaseId = friend.getFirebaseId();

        /* Get the details of the added friend from the users reference using the firebase id. */
        AuthPresenter.getUserProfile(firebaseId, new GetFriendListener() {
            @Override
            public void onComplete(Friend friend) {
                friend.setFirebaseId(firebaseId);
                FollowingPresenter.super.addFriend(friend, updatedFollowingList, listener);
            }

            @Override
            public void onFailed() {

            }
        });
    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return FollowingDatabaseHelper.getDataReference(userId);
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return FollowingDatabaseHelper.getCountReference(userId);
    }

    @Override
    protected boolean isCachingEnabled() {
        return Constants.CACHE_FOLLOWING;
    }

    @Override
    protected String getCacheKeyString() {
        return Constants.KEY_SHARED_PREF_DATA_FOLLOWING;
    }

    @Override
    protected int getInitialListCapacity() {
        return Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING;
    }

    @Override
    protected void notifyStartLoading(Object listener) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onStartFollowingLoading();
    }

    @Override
    protected void notifyInitialLoading(Object listener) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onFollowingInitialization();
    }

    @Override
    protected void notifyAddData(Object listener, int index, Friend friend) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onFollowingAdded(index, friend);
    }

    @Override
    protected void notifyChangeData(Object listener, int index, Friend friend) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onFollowingChanged(index, friend);
    }

    @Override
    protected void notifyRemoveData(Object listener, int index, Friend friend) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onFollowingRemoved(index, friend);
    }

    @Override
    protected void notifyCompleteLoading(Object listener) {
        FollowingListener observer = (FollowingListener) listener;
        observer.onCompleteFollowingLoading();
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    public interface FollowingListener {
        void onStartFollowingLoading();

        void onFollowingInitialization();

        void onFollowingAdded(int index, Friend friend);

        void onFollowingChanged(int index, Friend friend);

        void onFollowingRemoved(int index, Friend friend);

        void onCompleteFollowingLoading();
    }
}
