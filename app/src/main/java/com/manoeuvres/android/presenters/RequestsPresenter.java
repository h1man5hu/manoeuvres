package com.manoeuvres.android.presenters;


import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class RequestsPresenter {
    private static RequestsPresenter ourInstance;

    private ChildEventListener mDataListener;
    private ValueEventListener mCountListener;

    private List<Friend> mRequests;

    private RequestsListener[] mObservers;

    private boolean mIsLoaded;

    private RequestsPresenter() {
        mRequests = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_REQUESTS);
        mObservers = new RequestsListener[Constants.MAX_REQUESTS_LISTENERS_COUNT];
    }

    public static RequestsPresenter getInstance() {
        if (ourInstance == null) ourInstance = new RequestsPresenter();
        return ourInstance;
    }

    public RequestsPresenter attach(Object component) {
        RequestsListener listener = (RequestsListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            RequestsListener observer = mObservers[i];
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

    public RequestsPresenter detach(Object component) {
        RequestsListener listener = (RequestsListener) component;

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

    public RequestsPresenter sync() {
        if (mCountListener == null) {
            notifyObservers(Constants.CALLBACK_START_LOADING);
            if (mRequests.size() == 0) notifyObservers(Constants.CALLBACK_INITIAL_LOADING);
            mCountListener = DatabaseHelper.mUserRequestsCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    if (count == 0) notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);

                    else if (count > 0) {
                        if (mDataListener == null) {

                            mDataListener = DatabaseHelper.mUserRequestsReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    if (!mRequests.contains(friend)) {
                                        mRequests.add(friend);
                                        notifyObservers(Constants.CALLBACK_ADD_DATA, mRequests.size() - 1, friend);
                                    }

                                    if (mRequests.size() == count) {
                                        mIsLoaded = true;
                                        notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                    }
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mRequests.indexOf(friend);
                                    if (index != -1) {
                                        mRequests.remove(index);
                                        notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
                                    }

                                    if (mRequests.size() == count) {
                                        mIsLoaded = true;
                                        notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
                                    }
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

    public RequestsPresenter stopSync() {
        if (mDataListener != null) {
            DatabaseHelper.mUserRequestsReference.removeEventListener(mDataListener);
            mDataListener = null;
        }
        if (mCountListener != null) {
            DatabaseHelper.mUserRequestsCountReference.removeEventListener(mCountListener);
            mCountListener = null;
        }
        return ourInstance;
    }

    public Friend get(int index) {
        return mRequests.get(index);
    }

    public List<Friend> getAll() {
        return mRequests;
    }

    public int size() {
        return mRequests.size();
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    public int indexOf(Friend friend) {
        return mRequests.indexOf(friend);
    }

    public void clear() {
        mRequests.clear();
    }

    private void notifyObservers(String event, int index, Friend friend) {
        for (int i = 0; i < mObservers.length; i++) {
            RequestsListener listener = mObservers[i];
            if (listener != null)
                switch (event) {
                    case Constants.CALLBACK_START_LOADING:
                        listener.onStartRequestsLoading();
                        break;
                    case Constants.CALLBACK_INITIAL_LOADING:
                        listener.onRequestsInitialization();
                        break;
                    case Constants.CALLBACK_ADD_DATA:
                        listener.onRequestAdded(index, friend);
                        break;
                    case Constants.CALLBACK_REMOVE_DATA:
                        listener.onRequestRemoved(index, friend);
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        mIsLoaded = true;
                        listener.onCompleteRequestsLoading();
                        break;
                }
        }
    }

    private void notifyObservers(String event) {
        notifyObservers(event, 0, null);
    }

    public interface RequestsListener {
        void onStartRequestsLoading();

        void onRequestsInitialization();

        void onRequestAdded(int index, Friend friend);

        void onRequestRemoved(int index, Friend friend);

        void onCompleteRequestsLoading();
    }
}
