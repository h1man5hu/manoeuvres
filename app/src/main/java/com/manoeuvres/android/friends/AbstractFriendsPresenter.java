package com.manoeuvres.android.friends;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;
import com.manoeuvres.android.util.Constants;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFriendsPresenter implements FriendsPresenter {

    private ChildEventListener mDataListener;
    private ValueEventListener mCountListener;
    private List<Friend> mFriends;
    private Object[] mObservers;
    private boolean mIsLoaded;
    private SharedPreferences mSharedPreferences;

    public AbstractFriendsPresenter(int friendsInitialCapacity,
                                    int observersCount,
                                    @Nullable Context applicationContext) {
        mFriends = new ArrayList<>(friendsInitialCapacity);
        mObservers = new Object[observersCount];
        if (isCachingEnabled() && applicationContext != null) {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
            loadCache();
        }
    }

    @Override
    public void attach(@NonNull Object component) {
        /* If the observer is already attached, return. */
        for (int i = 0; i < mObservers.length; i++) {
            Object observer = mObservers[i];
            if (observer != null && observer.equals(component)) {
                return;
            }
        }

        /* Insert the observer at the first available slot. */
        for (int i = 0; i < mObservers.length; i++) {
            if (mObservers[i] == null) {
                mObservers[i] = component;
                return;
            }
        }
    }

    @Override
    public FriendsPresenter detach(@NonNull Object component) {
        /* If there are no observers, free the memory for garbage collection. */
        if (mObservers.length == 0) {
            stopSync();
            destroy();
            return null;
        }

        for (int i = 0; i < mObservers.length; i++) {
            if (mObservers[i] != null && mObservers[i].equals(component)) {
                mObservers[i] = null;
                return this;
            }
        }

        return this;
    }

    @Override
    public void sync() {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }
        setListeners(userId);
    }

    @Override
    public void stopSync() {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }
        removeListeners(userId);
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

    private void setListeners(final String userId) {
        setCountListener(userId, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                if (count == 0) {
                    if (mFriends.size() > 0) {
                        clearFriendsList(mFriends);
                    }
                    notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                } else if (count > 0) {
                    setDataListener(userId, count);
                }
            }

            @Override
            public void onFailed() {

            }
        });
    }

    private void removeListeners(final String userId) {
        if (mDataListener != null) {
            getDataReference(userId).removeEventListener(mDataListener);
            mDataListener = null;
        }
        if (mCountListener != null) {
            getCountReference(userId).removeEventListener(mCountListener);
            mCountListener = null;
        }
    }

    private void setCountListener(final String userId, final GetIntListener listener) {
        if (mCountListener != null) {
            return;
        }

        notifyObservers(Constants.CALLBACK_START_LOADING);
        if (mFriends.size() == 0) {
            notifyObservers(Constants.CALLBACK_INITIAL_LOADING);
        }

        mCountListener = getCountReference(userId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        int count = 0;
                        Object value = dataSnapshot.getValue();
                        if (value != null) {
                            count = Integer.valueOf(value.toString());
                        }
                        listener.onComplete(count);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

    }

    private void setDataListener(final String userId, final int count) {
        if (mDataListener != null) {
            return;
        }

        /*
         * In case not all but some of the friends were removed, this list will
         * be subtracted from the cached list to get the friends which were removed.
         * Each friend on the Firebase database is added to this list.
         * The subtraction will be done when all the friends have been retrieved from the
         * database.
         */
        final List<Friend> updatedFriendsList = createFriendsList();

        mDataListener = getDataReference(userId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        Friend friend = new Friend(dataSnapshot.getValue().toString());

                        if (isCachingEnabled() && updatedFriendsList != null) {
                            addFriend(friend, updatedFriendsList, new OnCompleteListener() {
                                @Override
                                public void onComplete() {
                                    /*
                                     * All the friends have been retrieved, subtract the list to get
                                     * the removed cached friends, if any.
                                     */
                                    if (updatedFriendsList.size() == count) {
                                        if (mFriends.size() > count) {
                                            List<Friend> removedFollowing =
                                                    new ArrayList<>(mFriends);
                                            removedFollowing.removeAll(updatedFriendsList);
                                            clearFriendsList(removedFollowing);
                                        }
                                        notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                                    }
                                }

                                @Override
                                public void onFailed() {

                                }
                            });
                        } else {
                            addFriend(friend);
                        }

                        if (!isCachingEnabled() && mFriends.size() == count) {
                            notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
                        }
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        Friend friend = new Friend(dataSnapshot.getValue().toString());
                        if (isCachingEnabled()) {
                            removeFriend(friend, updatedFriendsList);
                        } else {
                            removeFriend(friend);
                        }

                        if (mFriends.size() == count || mFriends.size() == 0) {
                            notifyObservers(Constants.CALLBACK_COMPLETE_LOADING);
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

    protected void addFriend(Friend friend,
                             @Nullable List<Friend> updatedFriendsList,
                             OnCompleteListener listener) {
        int index = mFriends.indexOf(friend);
        if (index == -1) {
            mFriends.add(friend);
            notifyObservers(
                    Constants.CALLBACK_ADD_DATA,
                    mFriends.size() - 1,
                    friend
            );
        } else {
            updateFriend(friend);
        }

        if (isCachingEnabled() && updatedFriendsList != null) {
            index = updatedFriendsList.indexOf(friend);
            if (index == -1) {
                updatedFriendsList.add(friend);
            }
            listener.onComplete();
        }
    }

    private void addFriend(Friend friend) {
        addFriend(friend, null, null);
    }

    private void updateFriend(Friend friend) {
        int index = mFriends.indexOf(friend);
        if (index != -1) {
            Friend oldFriend = mFriends.get(index);
            String oldName = oldFriend.getName();
            String newName = friend.getName();
            if (oldName != null && !oldName.equals(newName)) {
                oldFriend.setName(newName);
                notifyObservers(
                        Constants.CALLBACK_CHANGE_DATA,
                        mFriends.indexOf(oldFriend),
                        oldFriend
                );
            }
        }
    }

    private void removeFriend(Friend friend, @Nullable List<Friend> updatedFriendsList) {
        int index = mFriends.indexOf(friend);
        if (index != -1) {
            mFriends.remove(index);
            notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
        }

        if (updatedFriendsList != null) {
            index = updatedFriendsList.indexOf(friend);
            if (index != -1) {
                updatedFriendsList.remove(index);
            }
        }
    }

    private void removeFriend(Friend friend) {
        removeFriend(friend, null);
    }

    private void clearFriendsList(List<Friend> friendsList) {
        Friend friend = new Friend();
        for (int i = 0; i < friendsList.size(); i++) {
            Friend removedFriend = friendsList.get(i);
            friend.setFirebaseId(removedFriend.getFirebaseId());
            int index = mFriends.indexOf(removedFriend);
            friendsList.remove(removedFriend);
            notifyObservers(Constants.CALLBACK_REMOVE_DATA, index, friend);
        }
    }

    private AbstractFriendsPresenter loadCache() {
        List<Friend> cachedFriends = getCachedFriends();
        if (cachedFriends == null) {
            cachedFriends = new ArrayList<>(getInitialListCapacity());
        }
        mFriends = cachedFriends;
        return this;
    }

    private List<Friend> getCachedFriends() {
        String key = getCacheKey();
        String friendsList;
        if (key != null) {
            friendsList = mSharedPreferences.getString(getCacheKey(), "");
            Type type = new TypeToken<List<Friend>>() {
            }.getType();
            return new Gson().fromJson(friendsList, type);
        } else {
            return null;
        }
    }

    protected AbstractFriendsPresenter saveCache() {
        String key = getCacheKey();
        if (key != null) {
            mSharedPreferences.edit().putString(
                    getCacheKey(),
                    new Gson().toJson(mFriends)
            ).apply();
        }

        return this;
    }

    protected SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    private String getCacheKey() {
        if (isCachingEnabled()) {
            return getCacheKeyString();
        } else {
            return null;
        }
    }

    private List<Friend> createFriendsList() {
        if (isCachingEnabled()) {
            return new ArrayList<>(getInitialListCapacity());
        } else {
            return null;
        }
    }

    private void notifyObservers(String event, int index, Friend friend) {
        if (event.equals(Constants.CALLBACK_COMPLETE_LOADING)) {
            mIsLoaded = true;
        }
        for (int i = 0; i < mObservers.length; i++) {
            Object listener = mObservers[i];
            if (listener != null) {
                switch (event) {
                    case Constants.CALLBACK_START_LOADING:
                        notifyStartLoading(listener);
                        break;
                    case Constants.CALLBACK_INITIAL_LOADING:
                        notifyInitialLoading(listener);
                        break;
                    case Constants.CALLBACK_ADD_DATA:
                        notifyAddData(listener, index, friend);
                        break;
                    case Constants.CALLBACK_CHANGE_DATA:
                        notifyChangeData(listener, index, friend);
                        break;
                    case Constants.CALLBACK_REMOVE_DATA:
                        notifyRemoveData(listener, index, friend);
                        break;
                    case Constants.CALLBACK_COMPLETE_LOADING:
                        notifyCompleteLoading(listener);
                        break;
                    default:
                }
            }
        }
    }

    protected void notifyObservers(String event) {
        notifyObservers(event, 0, null);
    }

    protected abstract DatabaseReference getDataReference(String userId);

    protected abstract DatabaseReference getCountReference(String userId);

    protected abstract boolean isCachingEnabled();

    protected abstract String getCacheKeyString();

    protected abstract int getInitialListCapacity();

    protected abstract void notifyStartLoading(Object listener);

    protected abstract void notifyInitialLoading(Object listener);

    protected abstract void notifyAddData(Object listener, int index, Friend friend);

    protected abstract void notifyChangeData(Object listener, int index, Friend friend);

    protected abstract void notifyRemoveData(Object listener, int index, Friend friend);

    protected abstract void notifyCompleteLoading(Object listener);

    protected abstract void destroy();
}
