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

    private List<SeenRequestsListener> mObservers;

    private SeenRequestsPresenter() {
        mSeenRequests = new ArrayList<>();
        mObservers = new ArrayList<>();
    }

    public static SeenRequestsPresenter getInstance() {
        if (ourInstance == null) ourInstance = new SeenRequestsPresenter();
        return ourInstance;
    }

    public SeenRequestsPresenter attach(Object component) {
        SeenRequestsListener observer = (SeenRequestsListener) component;
        if (!mObservers.contains(observer)) mObservers.add(observer);

        return ourInstance;
    }

    public SeenRequestsPresenter detach(Object component) {
        SeenRequestsListener observer = (SeenRequestsListener) component;
        if (mObservers.contains(observer)) mObservers.remove(observer);
        if (mObservers.size() == 0) {
            stopSync();
            ourInstance = null;
        }
        return ourInstance;
    }

    public SeenRequestsPresenter sync() {
        if (mListener == null) {
            mListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mSeenRequests.clear();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        mSeenRequests.add(new Friend(snapshot.getValue().toString()));
                    }
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
        for (SeenRequestsListener observer : mObservers) {
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
