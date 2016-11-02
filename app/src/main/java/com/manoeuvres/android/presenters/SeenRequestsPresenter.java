package com.manoeuvres.android.presenters;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class SeenRequestsPresenter {
    private static SeenRequestsPresenter ourInstance;

    private ValueEventListener mListener;

    private List<Friend> mSeenRequests;

    private SeenRequestsListener[] mObservers;

    private SeenRequestsPresenter() {
        mSeenRequests = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_SEEN_REQUESTS);
        mObservers = new SeenRequestsListener[Constants.MAX_SEEN_REQUESTS_LISTENERS_COUNT];
    }

    public static SeenRequestsPresenter getInstance() {
        if (ourInstance == null) ourInstance = new SeenRequestsPresenter();
        return ourInstance;
    }

    public SeenRequestsPresenter attach(Object component) {
        SeenRequestsListener listener = (SeenRequestsListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            SeenRequestsListener observer = mObservers[i];
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

    public SeenRequestsPresenter detach(Object component) {
        SeenRequestsListener listener = (SeenRequestsListener) component;

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

    public SeenRequestsPresenter sync() {
        if (mListener == null) {
            mListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mSeenRequests.clear();
                    if (dataSnapshot.hasChildren())
                        for (DataSnapshot snapshot : dataSnapshot.getChildren())
                            mSeenRequests.add(new Friend(snapshot.getValue().toString()));
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            DatabaseHelper.mUserSeenRequestsReference.addValueEventListener(mListener);
        }
        return ourInstance;
    }

    public SeenRequestsPresenter stopSync() {
        if (mListener != null) {
            DatabaseHelper.mUserSeenRequestsReference.removeEventListener(mListener);
            mListener = null;
        }
        return ourInstance;
    }

    public boolean contains(Friend friend) {
        return mSeenRequests.contains(friend);
    }

    private void notifyObservers(String event) {
        for (int i = 0; i < mObservers.length; i++) {
            SeenRequestsListener observer = mObservers[i];
            if (observer != null)
                switch (event) {
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        observer.onSeenRequestsLoaded();
                        break;
                }
        }
    }

    public interface SeenRequestsListener {
        void onSeenRequestsLoaded();
    }
}
