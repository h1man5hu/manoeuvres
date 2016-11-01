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

    private List<SeenFollowingListener> mObservers;

    private SeenFollowingPresenter() {
        mSeenFollowing = new ArrayList<>();
        mObservers = new ArrayList<>();
    }

    public static SeenFollowingPresenter getInstance() {
        if (ourInstance == null) ourInstance = new SeenFollowingPresenter();
        return ourInstance;
    }

    public SeenFollowingPresenter attach(Object component) {
        SeenFollowingListener observer = (SeenFollowingListener) component;
        if (!mObservers.contains(observer)) mObservers.add(observer);

        return ourInstance;
    }

    public SeenFollowingPresenter detach(Object component) {
        SeenFollowingListener observer = (SeenFollowingListener) component;
        if (mObservers.contains(observer)) mObservers.remove(observer);
        if (mObservers.size() == 0) {
            stopSync();
            ourInstance = null;
        }
        return ourInstance;
    }

    public SeenFollowingPresenter sync() {
        if (mListener == null) {
            mListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mSeenFollowing.clear();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        mSeenFollowing.add(new Friend(snapshot.getValue().toString()));
                    }
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
        for (SeenFollowingListener observer : mObservers) {
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
