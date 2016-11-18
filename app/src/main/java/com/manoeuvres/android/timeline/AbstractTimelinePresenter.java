package com.manoeuvres.android.timeline;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.util.Constants;

import java.util.Collection;
import java.util.Set;

public abstract class AbstractTimelinePresenter implements TimelinePresenter {

    private ArrayMap<String, DatabaseReference> mCountReferences;
    private SimpleArrayMap<String, DatabaseReference> mDataReferences;
    private SimpleArrayMap<String, ValueEventListener> mCountListeners;
    private SimpleArrayMap<String, ChildEventListener> mDataListeners;
    private ArrayMap<String, Object[]> mObservers;
    private SharedPreferences mSharedPreferences;
    private SimpleArrayMap<String, Boolean> mIsLoaded;

    protected AbstractTimelinePresenter(Context applicationContext) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        int initialCapacity = Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1;
        mCountReferences = new ArrayMap<>(initialCapacity);
        mCountListeners = new SimpleArrayMap<>(initialCapacity);
        mDataReferences = new SimpleArrayMap<>(initialCapacity);
        mDataListeners = new SimpleArrayMap<>(initialCapacity);
        mObservers = new ArrayMap<>(initialCapacity);
        mIsLoaded = new SimpleArrayMap<>(initialCapacity);
    }

    @Override
    public void attach(Object component, String userId) {
        Object[] listeners = mObservers.get(userId);
        if (listeners != null) {

            /* If the observer is already attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                Object observer = listeners[i];
                if (observer != null && observer.equals(component)) {
                    return;
                }
            }

            /* Insert the observer at the first available slot. */
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] == null) {
                    listeners[i] = component;
                    return;
                }
            }
        } else {
            listeners = new Object[getMaxListenersCount()];
            listeners[0] = component;
            mObservers.put(userId, listeners);
        }
    }

    @Override
    public TimelinePresenter detach(Object component, String userId) {
        Object[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] != null && listeners[i].equals(component)) {
                    listeners[i] = null;
                }
            }

            /* If there is at least one observer attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] != null) {
                    return this;
                }
            }

            mObservers.remove(userId);
            stopSync(userId);

            /* If there are no observers, free the memory for garbage collection. */
            if (mObservers.size() == 0) {
                destroy();
                return null;
            } else {
                Collection<Object[]> listenerArrays = mObservers.values();
                if (listenerArrays.size() > 0) {
                    for (Object[] listenerArray : listenerArrays) {
                        for (int i = 0; i < listenerArray.length; i++) {
                            Object observer = listenerArray[i];
                            if (observer != null) {
                                return this;
                            }
                        }
                    }
                }
                destroy();
                return null;
            }
        }
        return this;
    }

    @Override
    public void addFriend(String userId) {

        loadCache(userId);

        DatabaseReference countReference = mCountReferences.get(userId);
        if (countReference == null) {
            mCountReferences.put(
                    userId, getCountReference(userId)
            );
        }
        DatabaseReference dataReference = mDataReferences.get(userId);
        if (dataReference == null) {
            mDataReferences.put(
                    userId, getDataReference(userId)
            );
        }
    }

    @Override
    public void removeFriend(String userId) {
        stopSync(userId);
        if (mCountReferences.containsKey(userId)) {
            mCountReferences.remove(userId);
        }
        if (mDataReferences.containsKey(userId)) {
            mDataReferences.remove(userId);
        }
        if (mObservers.containsKey(userId)) {
            mObservers.remove(userId);
        }
        removeCollectionForUser(userId);
        mSharedPreferences.edit().remove(getCacheKey(userId)).apply();
    }

    @Override
    public void sync(final String userId) {
        setCountListener(userId, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                /*
                 * If the count on the Firebase database is zero but there data in the cache, remove
                 * all of it.
                 */
                if (count == 0) {
                    if (hasData(userId)) {
                        clearDataForUser(userId);
                    }
                    notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                } else if (count > 0) {
                    setDataListener(userId, count);
                }
            }

            @Override
            public void onFailed() {

            }
        });
    }

    private void notifyObservers(String userId, String event) {
        notifyObservers(userId, event, null, null);
    }

    protected void notifyObservers(String userId, String event, Object key, Object data) {
        if (event.equals(Constants.CALLBACK_COMPLETE_LOADING)) {
            mIsLoaded.put(userId, true);
        }

        Object[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                Object listener = listeners[i];
                if (listener != null) {
                    switch (event) {
                        case Constants.CALLBACK_START_LOADING:
                            notifyStartLoading(userId, listener);
                            break;
                        case Constants.CALLBACK_INITIAL_LOADING:
                            notifyInitialLoading(userId, listener);
                            break;
                        case Constants.CALLBACK_ADD_DATA:
                            notifyAddData(userId, listener, key, data);
                            break;
                        case Constants.CALLBACK_CHANGE_DATA:
                            notifyChangeData(userId, listener, key, data);
                            break;
                        case Constants.CALLBACK_REMOVE_DATA:
                            notifyRemoveData(userId, listener, key, data);
                            break;
                        case Constants.CALLBACK_COMPLETE_LOADING:
                            notifyCompleteLoading(userId, listener);
                            break;
                        default:
                    }
                }
            }
        }
    }

    @Override
    public void stopSync(String userId) {
        DatabaseReference countReference = mCountReferences.get(userId);
        DatabaseReference dataReference = mDataReferences.get(userId);
        ValueEventListener countListener = mCountListeners.get(userId);
        ChildEventListener dataListener = mDataListeners.get(userId);

        if (countReference != null && countListener != null) {
            countReference.removeEventListener(countListener);
            mCountListeners.put(userId, null);
        }
        if (dataReference != null && dataListener != null) {
            dataReference.removeEventListener(dataListener);
            mDataListeners.put(userId, null);
        }
    }

    @Override
    public void sync() {
        Set<String> keys = mCountReferences.keySet();
        if (keys.size() > 0) {
            for (String userId : keys) {
                sync(userId);
            }
        }
    }

    @Override
    public Boolean isLoaded(String userId) {
        Boolean isLoaded = mIsLoaded.get(userId);
        if (isLoaded != null) {
            return isLoaded;
        } else {
            return false;
        }
    }

    private void setCountListener(String userId, final GetIntListener listener) {
        ValueEventListener countListener = mCountListeners.get(userId);
        if (countListener == null) {
            notifyObservers(userId, Constants.CALLBACK_START_LOADING);

            if (!hasData(userId)) {
                notifyObservers(userId, Constants.CALLBACK_INITIAL_LOADING);
            }

            DatabaseReference countReference = mCountReferences.get(userId);
            if (countReference != null) {
                countListener = countReference.addValueEventListener(
                        new ValueEventListener() {
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
                mCountListeners.put(userId, countListener);
            }
        }
    }

    private void setDataListener(final String userId, final int count) {
        /*
         * In case not all but some of the moves were removed, this map will
         * be subtracted from the cached map to get the moves which were removed.
         * Each move on the Firebase database is added to this map.
         * The subtraction will be done when all the moves have been retrieved from the
         * Firebase database.
         */
        final Object updatedCollection = getNewCollection(userId);

        ChildEventListener dataListener = mDataListeners.get(userId);
        if (dataListener == null) {
            DatabaseReference dataReference = mDataReferences.get(userId);
            if (dataReference != null) {
                dataListener = dataReference.limitToLast(getDataLimit())
                        .addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                addDataToCachedAndUpdatedCollections(
                                        userId, dataSnapshot, updatedCollection
                                );

                                int limit = getDataLimit();
                                if (count < limit) {
                                    limit = count;
                                }
                                if (getCollectionSize(updatedCollection) == limit) {
                                    if (getUserCollectionSize(userId) > limit) {
                                        removeRedundantDataFromUserCollection(
                                                userId, updatedCollection
                                        );
                                    }
                                    notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                                }
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                updateData(userId, dataSnapshot);
                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                removeData(userId, dataSnapshot);
                            }

                            @Override
                            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                mDataListeners.put(userId, dataListener);
            }
        }
    }

    protected abstract int getDataLimit();

    protected abstract Object getNewCollection(String userId);

    protected abstract void addDataToCachedAndUpdatedCollections(
            String userId, DataSnapshot dataSnapshot, Object updatedCollection
    );

    protected abstract int getCollectionSize(Object updatedCollection);

    protected abstract int getUserCollectionSize(String userId);

    protected abstract void removeRedundantDataFromUserCollection(
            String userId, Object updatedCollection
    );

    protected abstract void updateData(String userId, DataSnapshot dataSnapshot);

    protected abstract void removeData(String userId, DataSnapshot dataSnapshot);

    protected abstract void removeCollectionForUser(String userId);

    protected abstract String getCacheKey(String userId);

    protected abstract void loadCache(String userId);

    protected abstract DatabaseReference getCountReference(String userId);

    protected abstract DatabaseReference getDataReference(String userId);

    protected abstract void destroy();

    protected abstract int getMaxListenersCount();

    protected SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    protected abstract void notifyCompleteLoading(String userId, Object listenerObject);

    protected abstract void notifyRemoveData(
            String userId, Object listenerObject, Object keyObject, Object dataObject
    );

    protected abstract void notifyChangeData(
            String userId, Object listenerObject, Object keyObject, Object dataObject
    );

    protected abstract void notifyAddData(
            String userId, Object listenerObject, Object keyObject, Object dataObject
    );

    protected abstract void notifyInitialLoading(String userId, Object listenerObject);

    protected abstract void notifyStartLoading(String userId, Object listenerObject);

    protected abstract boolean hasData(String userId);

    protected abstract void clearDataForUser(String userId);
}
