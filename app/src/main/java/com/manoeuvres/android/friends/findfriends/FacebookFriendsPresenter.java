package com.manoeuvres.android.friends.findfriends;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FacebookFriendsPresenter {
    private static FacebookFriendsPresenter ourInstance;

    private List<Friend> mFriends;
    private FacebookFriendsListener[] mObservers;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private boolean isLoaded;

    private FacebookFriendsPresenter(Context applicationContext) {

        /* Initialize Facebook SDK. Should be called as early as possible. */
        FacebookSdk.sdkInitialize(applicationContext);

        /* Caching: Load the details of the Facebook friends of the user from the shared preferences file.
         * Update the list when network can be accessed.
         */
        mGson = new Gson();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        String allFriendsList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "");
        Type type = new TypeToken<List<Friend>>() {
        }.getType();
        mFriends = mGson.fromJson(allFriendsList, type);
        if (mFriends == null) {
            mFriends = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_FACEBOOK_FRIENDS);
        }

        mObservers = new FacebookFriendsListener[Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT];
    }

    public static FacebookFriendsPresenter getInstance(Context applicationContext) {

        if (ourInstance == null) ourInstance = new FacebookFriendsPresenter(applicationContext);
        return ourInstance;
    }

    public FacebookFriendsPresenter attach(Object component) {
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

    public FacebookFriendsPresenter detach(Object component) {
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

    public FacebookFriendsPresenter updateFacebookFriends() {
        notifyObservers(Constants.CALLBACK_START_LOADING);

        if (mFriends.size() == 0) notifyObservers(Constants.CALLBACK_INITIAL_LOADING);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference usersReference = database.getReference().child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);

        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        JSONArray friends;
                        try {
                            if (object != null) {
                                friends = object.getJSONObject(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS).getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                                final int numberOfFriends = friends.length();
                                for (int i = 0; i < numberOfFriends; i++) {
                                    JSONObject friendJSONObject = friends.getJSONObject(i);
                                    final Friend friend = new Friend(friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID),
                                            friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME));
                                    usersReference.orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).
                                            equalTo(friend.getFacebookId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            friend.setFirebaseId(dataSnapshot.getChildren().iterator().next().getKey());
                                            int index = mFriends.indexOf(friend);
                                            if (index != -1) {
                                                mFriends.remove(index);
                                                mFriends.add(index, friend);
                                            } else {
                                                mFriends.add(friend);
                                            }


                                            /* If all the friends have been loaded, update the cache. */
                                            if (mFriends.size() == numberOfFriends) {
                                                notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                                mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, mGson.toJson(mFriends)).apply();
                                            }

                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS, Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS);
        request.setParameters(parameters);
        request.executeAsync();

        return ourInstance;
    }

    public Friend get(int index) {
        return mFriends.get(index);
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

    public List<Friend> getAll() {
        return mFriends;
    }

    public int size() {
        return mFriends.size();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public int indexOf(Friend friend) {
        return mFriends.indexOf(friend);
    }

    private void notifyObservers(String event) {
        if (event.equals(Constants.CALLBACK_COMPLETE_LOADING)) isLoaded = true;
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
                        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, mGson.toJson(mFriends)).apply();
                        listener.onCompleteFacebookFriendsLoading();
                        break;
                }
        }
    }

    public interface FacebookFriendsListener {
        void onStartFacebookFriendsLoading();

        void onFacebookFriendsInitialization();

        void onCompleteFacebookFriendsLoading();
    }
}
