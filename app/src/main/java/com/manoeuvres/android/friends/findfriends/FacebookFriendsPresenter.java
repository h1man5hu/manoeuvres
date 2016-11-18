package com.manoeuvres.android.friends.findfriends;

import android.content.Context;
import android.support.annotation.NonNull;

import com.facebook.FacebookSdk;
import com.google.firebase.database.DatabaseReference;
import com.manoeuvres.android.friends.AbstractFriendsPresenter;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.requests.RequestsDatabaseHelper;
import com.manoeuvres.android.database.CompletionListeners.GetBooleanListener;
import com.manoeuvres.android.database.CompletionListeners.GetStringListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;
import com.manoeuvres.android.util.Constants;

import java.util.List;

public class FacebookFriendsPresenter extends AbstractFriendsPresenter {

    private static FacebookFriendsPresenter ourInstance;

    private FacebookFriendsPresenter(Context applicationContext) {
        super(
                Constants.INITIAL_COLLECTION_CAPACITY_FACEBOOK_FRIENDS,
                Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT,
                applicationContext
        );
        FacebookSdk.sdkInitialize(applicationContext);
    }

    public static FacebookFriendsPresenter getInstance(@NonNull Context applicationContext) {
        if (ourInstance == null) {
            ourInstance = new FacebookFriendsPresenter(applicationContext);
        }
        return ourInstance;
    }

    public void getUserName(String userId, final GetStringListener listener) {
        FacebookFriendsDatabaseHelper.getName(userId, listener);
    }

    void followFriend(Friend friend, final OnCompleteListener listener) {
        RequestsDatabaseHelper.sendRequest(friend.getFirebaseId(), listener);
    }

    void cancelRequest(Friend friend, final OnCompleteListener listener) {
        RequestsDatabaseHelper.cancelRequest(friend.getFirebaseId(), listener);
    }

    void isRequested(Friend friend, final GetBooleanListener listener) {
        RequestsDatabaseHelper.isFriendRequested(friend.getFirebaseId(), listener);
    }

    @Override
    public void sync() {
        updateFacebookFriends();
    }

    @Override
    public void stopSync() {

    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return null;
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return null;
    }

    @Override
    protected void notifyStartLoading(Object listener) {
        FacebookFriendsListener observer = (FacebookFriendsListener) listener;
        observer.onStartFacebookFriendsLoading();
    }

    @Override
    protected void notifyInitialLoading(Object listener) {
        FacebookFriendsListener observer = (FacebookFriendsListener) listener;
        observer.onFacebookFriendsInitialization();
    }

    @Override
    protected void notifyAddData(Object listener, int index, Friend friend) {

    }

    @Override
    protected void notifyChangeData(Object listener, int index, Friend friend) {

    }

    @Override
    protected void notifyRemoveData(Object listener, int index, Friend friend) {

    }

    @Override
    protected void notifyCompleteLoading(Object listener) {
        FacebookFriendsListener observer = (FacebookFriendsListener) listener;
        observer.onCompleteFacebookFriendsLoading();
    }

    @Override
    protected boolean isCachingEnabled() {
        return Constants.CACHE_FACEBOOK_FRIENDS;
    }

    @Override
    protected String getCacheKeyString() {
        return Constants.KEY_SHARED_PREF_DATA_FRIENDS;
    }

    @Override
    protected int getInitialListCapacity() {
        return Constants.INITIAL_COLLECTION_CAPACITY_FACEBOOK_FRIENDS;
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    private void updateFacebookFriends() {
        notifyObservers(Constants.CALLBACK_START_LOADING);
        List<Friend> friends = super.getAll();
        if (friends.size() == 0) {
            notifyObservers(Constants.CALLBACK_INITIAL_LOADING);
        }
        FacebookFriendsDatabaseHelper.updateFriends(friends, new OnCompleteListener() {
            @Override
            public void onComplete() {
                notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                saveCache();
            }

            @Override
            public void onFailed() {

            }
        });
    }

    public Friend get(String userId) {
        List<Friend> friends = super.getAll();
        return friends.get(friends.indexOf(new Friend(userId)));
    }

    public Friend get(Friend friend) {
        List<Friend> friends = super.getAll();
        return friends.get(friends.indexOf(friend));
    }

    public boolean contains(Friend friend) {
        List<Friend> friends = super.getAll();
        return friends.contains(friend);
    }

    public int indexOf(Friend friend) {
        List<Friend> friends = super.getAll();
        return friends.indexOf(friend);
    }

    interface FacebookFriendsListener {
        void onStartFacebookFriendsLoading();

        void onFacebookFriendsInitialization();

        void onCompleteFacebookFriendsLoading();
    }
}
