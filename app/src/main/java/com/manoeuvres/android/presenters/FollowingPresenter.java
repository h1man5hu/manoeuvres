package com.manoeuvres.android.presenters;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FollowingPresenter {
    private static FollowingPresenter ourInstance;

    private ChildEventListener mDataListener;
    private ValueEventListener mCountListener;

    private List<Friend> mFollowing;

    private FollowingListener[] mObservers;

    private boolean mIsLoaded;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private Type mType;

    private FollowingPresenter(Context applicationContext) {
        /*
         * Caching: Load the friends which the user is following from the shared preferences file.
         * Update the list when network can be accessed.
         */
        mGson = new Gson();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        String friendList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, "");
        mType = new TypeToken<List<Friend>>() {
        }.getType();
        mFollowing = mGson.fromJson(friendList, mType);
        if (mFollowing == null) {
            mFollowing = new ArrayList<>(Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING);
        }

        mObservers = new FollowingListener[Constants.MAX_FOLLOWING_LISTENERS_COUNT];
    }

    public static FollowingPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) ourInstance = new FollowingPresenter(applicationContext);
        return ourInstance;
    }

    public FollowingPresenter attach(Object component) {
        FollowingListener listener = (FollowingListener) component;

        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            FollowingListener observer = mObservers[i];
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

    public FollowingPresenter detach(Object component) {
        FollowingListener listener = (FollowingListener) component;

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

    public FollowingPresenter sync() {
        if (mCountListener == null) {
            notifyObservers(Constants.CALLBACK_START_LOADING);

            if (mFollowing.size() == 0) notifyObservers(Constants.CALLBACK_INITIAL_LOADING);

            mCountListener = DatabaseHelper.mUserFollowingCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    /*
                     * If the count on the Firebase database is zero but there are friends in the cached list,
                     * remove all of them.
                     */
                    if (count == 0) {
                        if (mFollowing.size() > 0) {
                            for (int i = 0; i < mFollowing.size(); i++) {
                                Friend removedFriend = mFollowing.get(i);
                                Friend friend = new Friend(removedFriend.getFirebaseId());
                                mFollowing.remove(removedFriend);
                                notifyObservers(Constants.CALLBACK_REMOVE_DATA, mFollowing.indexOf(friend), friend);
                            }
                        }
                        notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                    } else if (count > 0) {
                        if (mDataListener == null) {

                            /*
                            * In case not all but some of the friends were removed, this list will
                            * be subtracted from the cached list to get the friends which were removed.
                            * Each friend on the Firebase database is added to this list.
                            * The subtraction will be done when all the friends have been retrieved from the
                            * Firebase database.
                            */
                            final List<Friend> updatedFollowing = new ArrayList<>();

                            mDataListener = DatabaseHelper.mUserFollowingReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    final String firebaseId = dataSnapshot.getValue().toString();

                                    /* Get the details of the added friend from the users reference using the firebase id. */
                                    DatabaseHelper.mUsersReference.child(firebaseId).addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            Friend friend = dataSnapshot.getValue(Friend.class);
                                            friend.setFirebaseId(firebaseId);
                                            int index = mFollowing.indexOf(friend);
                                            if (index == -1) {
                                                mFollowing.add(friend);
                                                notifyObservers(Constants.CALLBACK_ADD_DATA, index, friend);
                                            } else {
                                                Friend oldFriend = mFollowing.get(mFollowing.indexOf(friend));
                                                String oldName = oldFriend.getName();
                                                String newName = friend.getName();
                                                if (oldName != null && !oldName.equals(newName)) {
                                                    oldFriend.setName(newName);
                                                    notifyObservers(Constants.CALLBACK_CHANGE_DATA, mFollowing.indexOf(oldFriend), oldFriend);
                                                }
                                            }

                                            index = updatedFollowing.indexOf(friend);
                                            if (index == -1) updatedFollowing.add(friend);

                                            /*
                                             * All the friends have been retrieved, subtract the list and remove menu items
                                             * for removed friends, if any.
                                             */
                                            if (updatedFollowing.size() == count) {
                                                if (mFollowing.size() > count) {
                                                    List<Friend> removedFollowing = new ArrayList<>(mFollowing);
                                                    removedFollowing.removeAll(updatedFollowing);
                                                    for (int i = 0; i < removedFollowing.size(); i++) {
                                                        Friend removedFriend = removedFollowing.get(i);
                                                        friend = new Friend(removedFriend.getFirebaseId());
                                                        index = mFollowing.indexOf(removedFriend);
                                                        mFollowing.remove(removedFriend);
                                                        notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
                                                    }
                                                }
                                                notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mFollowing.indexOf(friend);
                                    if (index != -1) {
                                        mFollowing.remove(index);
                                        notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
                                    }
                                    index = updatedFollowing.indexOf(friend);
                                    if (index != -1) {
                                        updatedFollowing.remove(index);
                                    }
                                    if (updatedFollowing.size() == count)
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

    public FollowingPresenter stopSync() {
        if (mDataListener != null) {
            DatabaseHelper.mUserFollowingReference.removeEventListener(mDataListener);
            mDataListener = null;
        }
        if (mCountListener != null) {
            DatabaseHelper.mUserFollowingCountReference.removeEventListener(mCountListener);
            mCountListener = null;
        }
        return ourInstance;
    }

    /*
     * The MainActivity updates the cache when it is stopped. If a friend is removed in the background,
     * this method compares the new friends with the cached list, and returns a list of friends which
     * were removed, if any.
     */
    public List<Friend> getRemovedFollowing() {
        String friendList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_MENU_ITEMS_FOLLOWING, "");
        List<Friend> following = mGson.fromJson(friendList, mType);
        if (following != null && mFollowing != null) {
            following.removeAll(mFollowing);
        }
        return following;
    }

    public Friend get(int index) {
        return mFollowing.get(index);
    }

    public List<Friend> getAll() {
        return mFollowing;
    }

    public int size() {
        return mFollowing.size();
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    private void notifyObservers(String event, int index, Friend friend) {
        for (int i = 0; i < mObservers.length; i++) {
            FollowingListener listener = mObservers[i];
            if (listener != null)
                switch (event) {
                    case Constants.CALLBACK_START_LOADING:
                        listener.onStartFollowingLoading();
                        break;
                    case Constants.CALLBACK_INITIAL_LOADING:
                        listener.onFollowingInitialization();
                        break;
                    case Constants.CALLBACK_ADD_DATA:
                        listener.onFollowingAdded(index, friend);
                        break;
                    case Constants.CALLBACK_CHANGE_DATA:
                        listener.onFollowingChanged(index, friend);
                        break;
                    case Constants.CALLBACK_REMOVE_DATA:
                        listener.onFollowingRemoved(index, friend);
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        mIsLoaded = true;
                        listener.onCompleteFollowingLoading();
                        break;
                }
        }
    }

    private void notifyObservers(String event) {
        notifyObservers(event, 0, null);
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
