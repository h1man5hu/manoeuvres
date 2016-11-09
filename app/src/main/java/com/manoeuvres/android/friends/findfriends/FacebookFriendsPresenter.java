package com.manoeuvres.android.friends.findfriends;


import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.facebook.FacebookSdk;
import com.facebook.GraphResponse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.FriendsPresenter;
import com.manoeuvres.android.util.Constants;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FacebookFriendsPresenter implements FriendsPresenter {
    private static FacebookFriendsPresenter ourInstance;

    private List<Friend> mFriends;
    private FacebookFriendsListener[] mObservers;

    private boolean mIsLoaded;

    private FacebookFriendsPresenter(Context applicationContext) {

        /* Initialize Facebook SDK. Should be called as early as possible. */
        FacebookSdk.sdkInitialize(applicationContext);

        mFriends = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_FACEBOOK_FRIENDS);
        mObservers = new FacebookFriendsListener[Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT];
    }

    public static FacebookFriendsPresenter getInstance(@NonNull Context applicationContext) {
        if (ourInstance == null) ourInstance = new FacebookFriendsPresenter(applicationContext);
        return ourInstance;
    }

    @Override
    public FacebookFriendsPresenter attach(@NonNull Object component) {
        FacebookFriendsListener listener = (FacebookFriendsListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            FacebookFriendsListener observer = mObservers[i];
            if (observer != null && observer.equals(listener)) return ourInstance;
        }

        /* Insert the observer at the first available slot. */
        for (int i = 0; i < mObservers.length; i++)
            if (mObservers[i] == null) {
                mObservers[i] = listener;
                return ourInstance;
            }

        return ourInstance;
    }

    @Override
    public FacebookFriendsPresenter detach(@NonNull Object component) {
        FacebookFriendsListener listener = (FacebookFriendsListener) component;

        /* If there are no observers, free the memory for garbage collection. */
        if (mObservers.length == 0) {
            ourInstance = null;
            return null;
        }

        for (int i = 0; i < mObservers.length; i++)
            if (mObservers[i] != null && mObservers[i].equals(listener)) {
                mObservers[i] = null;
                return ourInstance;
            }

        return ourInstance;
    }

    @Override
    public FacebookFriendsPresenter sync() {
        notifyObservers(Constants.CALLBACK_START_LOADING);
        if (mFriends.size() == 0) notifyObservers(Constants.CALLBACK_INITIAL_LOADING);
        FacebookFriendsDatabaseHelper.updateFriends(mFriends, new FriendListUpdateListener() {
            @Override
            public void onUpdated(List<Friend> friends) {
                notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
            }
        });
        return ourInstance;
    }

    @Override
    public FacebookFriendsPresenter stopSync() {
        return ourInstance;
    }

    @Override
    public Friend get(int index) {
        return mFriends.get(index);
    }

    @Override
    public List<Friend> getAll() {
        return mFriends;
    }

    @Override
    public int size() {
        return mFriends.size();
    }

    @Override
    public boolean isLoaded() {
        return mIsLoaded;
    }

    public Friend get(String userId) {
        return mFriends.get(mFriends.indexOf(new Friend(userId)));
    }

    public Friend get(Friend friend) {
        return mFriends.get(mFriends.indexOf(friend));
    }

    public boolean contains(Friend friend) {
        return mFriends.contains(friend);
    }

    public int indexOf(Friend friend) {
        return mFriends.indexOf(friend);
    }

    private void notifyObservers(String event) {
        if (event.equals(Constants.CALLBACK_COMPLETE_LOADING)) mIsLoaded = true;
        for (int i = 0; i < mObservers.length; i++) {
            FacebookFriendsListener listener = mObservers[i];
            if (listener != null)
                switch (event) {
                    case Constants.CALLBACK_START_LOADING:
                        listener.onStartFacebookFriendsLoading();
                        break;
                    case Constants.CALLBACK_INITIAL_LOADING:
                        listener.onFacebookFriendsInitialization();
                        break;
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        listener.onCompleteFacebookFriendsLoading();
                        break;
                }
        }
    }

    public void getUserName(String userId, final GetNameListener listener) {
        FacebookFriendsDatabaseHelper.getUserName(userId, listener);
    }

    void followFriend(Friend friend, final FollowFriendListener listener) {
        FacebookFriendsDatabaseHelper.followFriend(friend, listener);
    }

    void cancelRequest(Friend friend, final CancelRequestListener listener) {
        FacebookFriendsDatabaseHelper.cancelRequest(friend, listener);
    }

    void isRequested(Friend friend, final RequestListener listener) {
        FacebookFriendsDatabaseHelper.isRequested(friend, listener);
    }

    boolean isAttached(Object component) {
        FacebookFriendsListener observer = (FacebookFriendsListener) component;
        for (int i = 0; i < mObservers.length; i++) {
            if (mObservers[i] == null) continue;
            if (mObservers[i].equals(observer)) return true;
        }
        return false;
    }

    public FacebookFriendsPresenter loadCache(SharedPreferences preferences) {
        if (mFriends.size() == 0) {
            String cachedFriendsList = preferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "");
            Type type = new TypeToken<List<Friend>>() {
            }.getType();
            List<Friend> cachedFriends = new Gson().fromJson(cachedFriendsList, type);
            if (cachedFriends != null) mFriends = cachedFriends;
        }

        return ourInstance;
    }

    public FacebookFriendsPresenter saveCache(SharedPreferences preferences) {
        preferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, new Gson().toJson(mFriends)).apply();
        return ourInstance;
    }

    FacebookFriendsPresenter destroy() {
        ourInstance = null;
        return ourInstance;
    }

    interface FacebookFriendsListener {
        void onStartFacebookFriendsLoading();

        void onFacebookFriendsInitialization();

        void onCompleteFacebookFriendsLoading();
    }

    interface FirebaseIdListener {
        void onLoaded(String firebaseId);

        void onFailed();
    }

    /*
     * When the FindFriends fragment is initialized, it needs to check if a friend has already been
     * requested to update the button text.
     */
    interface RequestListener {
        void onComplete(boolean isRequested);

        void onFailed();
    }

    /*
    * The follow button in the FindFriends fragment changes its text upon completion to allow the
    * user to cancel the request.
    */
    interface FollowFriendListener {
        void onRequested();

        void onFailed();
    }

    /*
     * The cancel request button in the FindFriends fragment changes its text upon completion to allow
     * the user to follow the friend.
     */
    interface CancelRequestListener {
        void onRequestCancelled();

        void onFailed();
    }

    interface GraphRequestListener {
        void onRequestComplete(JSONObject object, GraphResponse response);
    }

    public interface GetNameListener {
        void onLoaded(String name);

        void onFailed();
    }

    interface FriendListUpdateListener {
        void onUpdated(List<Friend> friends);
    }
}
