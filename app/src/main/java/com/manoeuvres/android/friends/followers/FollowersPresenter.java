package com.manoeuvres.android.friends.followers;


import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class FollowersPresenter {
    private static FollowersPresenter ourInstance;

    private ChildEventListener mDataListener;
    private ValueEventListener mCountListener;

    private List<Friend> mFollowers;

    private FollowersListener[] mObservers;

    private boolean mIsLoaded;

    private FollowersPresenter() {
        mFollowers = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWERS);
        mObservers = new FollowersListener[Constants.MAX_FOLLOWERS_LISTENERS_COUNT];
    }

    public static FollowersPresenter getInstance() {
        if (ourInstance == null) ourInstance = new FollowersPresenter();
        return ourInstance;
    }

    public FollowersPresenter attach(Object component) {
        FollowersListener listener = (FollowersListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            FollowersListener observer = mObservers[i];
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

    public FollowersPresenter detach(Object component) {
        FollowersListener listener = (FollowersListener) component;

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

    public FollowersPresenter sync() {
        if (mCountListener == null) {
            notifyObservers(Constants.CALLBACK_START_LOADING);

            if (mFollowers.size() == 0) notifyObservers(Constants.CALLBACK_INITIAL_LOADING);

            mCountListener = DatabaseHelper.mUserFollowersCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    if (count == 0) notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);

                    if (count > 0) {
                        if (mDataListener == null) {

                            mDataListener = DatabaseHelper.mUserFollowersReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    if (!mFollowers.contains(friend)) {
                                        mFollowers.add(friend);
                                        notifyObservers(Constants.CALLBACK_ADD_DATA, mFollowers.size() - 1, friend);
                                    }

                                    if (mFollowers.size() == count)
                                        notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mFollowers.indexOf(friend);
                                    if (index != -1) {
                                        mFollowers.remove(index);
                                        notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
                                    }

                                    if (mFollowers.size() == count)
                                        notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
        return ourInstance;
    }

    public FollowersPresenter stopSync() {
        if (mDataListener != null) {
            DatabaseHelper.mUserFollowersReference.removeEventListener(mDataListener);
            mDataListener = null;
        }
        if (mCountListener != null) {
            DatabaseHelper.mUserFollowersCountReference.removeEventListener(mCountListener);
            mCountListener = null;
        }
        return ourInstance;
    }

    public Friend get(int index) {
        return mFollowers.get(index);
    }

    public List<Friend> getAll() {
        return mFollowers;
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    public int size() {
        return mFollowers.size();
    }

    public void clear() {
        mFollowers.clear();
    }

    private void notifyObservers(String event, int index, Friend friend) {
        if (event.equals(Constants.CALLBACK_COMPLETE_LOADING)) mIsLoaded = true;
        for (int i = 0; i < mObservers.length; i++) {
            FollowersListener listener = mObservers[i];
            if (listener != null)
                switch (event) {
                    case Constants.CALLBACK_START_LOADING:
                        listener.onStartFollowersLoading();
                        break;
                    case Constants.CALLBACK_INITIAL_LOADING:
                        listener.onFollowersInitialization();
                        break;
                    case Constants.CALLBACK_ADD_DATA:
                        listener.onFollowerAdded(index, friend);
                        break;
                    case Constants.CALLBACK_REMOVE_DATA:
                        listener.onFollowerRemoved(index, friend);
                        break;
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        listener.onCompleteFollowersLoading();
                        break;
                }
        }
    }

    private void notifyObservers(String event) {
        notifyObservers(event, 0, null);
    }

    public interface FollowersListener {
        void onStartFollowersLoading();

        void onFollowersInitialization();

        void onFollowerAdded(int index, Friend friend);

        void onFollowerRemoved(int index, Friend friend);

        void onCompleteFollowersLoading();
    }
}
