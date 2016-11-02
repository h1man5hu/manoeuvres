package com.manoeuvres.android.presenters;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class SeenFollowingPresenter {
    private static SeenFollowingPresenter ourInstance;

    private ValueEventListener mListener;

    private List<Friend> mSeenFollowing;

    private SeenFollowingListener[] mObservers;

    private SeenFollowingPresenter() {
        mSeenFollowing = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_SEEN_FOLLOWING);
        mObservers = new SeenFollowingListener[Constants.MAX_SEEN_FOLLOWING_LISTENERS_COUNT];
    }

    public static SeenFollowingPresenter getInstance() {
        if (ourInstance == null) ourInstance = new SeenFollowingPresenter();
        return ourInstance;
    }

    public SeenFollowingPresenter attach(Object component) {
        SeenFollowingListener listener = (SeenFollowingListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            SeenFollowingListener observer = mObservers[i];
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

    public SeenFollowingPresenter detach(Object component) {
        SeenFollowingListener listener = (SeenFollowingListener) component;

        /* If there are no observers, free the memory for garbage collection. */
        if (mObservers.length == 0) {
            stopSync();
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

    public SeenFollowingPresenter sync() {
        if (mListener == null) {
            mListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mSeenFollowing.clear();
                    if (dataSnapshot.hasChildren())
                        for (DataSnapshot snapshot : dataSnapshot.getChildren())
                            mSeenFollowing.add(new Friend(snapshot.getValue().toString()));
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            DatabaseHelper.mUserSeenFollowingReference.addValueEventListener(mListener);
        }
        return ourInstance;
    }

    public SeenFollowingPresenter stopSync() {
        if (mListener != null) {
            DatabaseHelper.mUserSeenFollowingReference.removeEventListener(mListener);
            mListener = null;
        }
        return ourInstance;
    }

    public boolean contains(Friend friend) {
        return mSeenFollowing.contains(friend);
    }

    private void notifyObservers(String event) {
        for (int i = 0; i < mObservers.length; i++) {
            SeenFollowingListener observer = mObservers[i];
            if (observer != null)
                switch (event) {
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        observer.onSeenFollowingLoaded();
                        break;
                }
        }
    }

    public interface SeenFollowingListener {
        void onSeenFollowingLoaded();
    }
}
