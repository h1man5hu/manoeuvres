package com.manoeuvres.android.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.manoeuvres.android.timeline.logs.Log;
import com.manoeuvres.android.timeline.logs.LogsDatabaseHelper;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.util.Collection;
import java.util.Set;

public class LatestLogPresenter {
    private static LatestLogPresenter ourInstance;

    private SimpleArrayMap<String, Log> mLatestLogs;
    private ArrayMap<String, LatestLogListener[]> mObservers;
    private ArrayMap<String, DatabaseReference> mReferences;
    private SimpleArrayMap<String, ValueEventListener> mListeners;
    private SharedPreferences mSharedPreferences;

    private LatestLogPresenter(Context applicationContext) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        int capacity = Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1;
        mObservers = new ArrayMap<>(capacity);
        mLatestLogs = new SimpleArrayMap<>(capacity);
        mListeners = new SimpleArrayMap<>(capacity);
        mReferences = new ArrayMap<>(capacity);
    }

    public static LatestLogPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) {
            ourInstance = new LatestLogPresenter(applicationContext);
        }
        return ourInstance;
    }

    public LatestLogPresenter attach(Object component, String userId) {
        LatestLogListener listener = (LatestLogListener) component;
        LatestLogListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            /* If the observer is already attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                LatestLogListener observer = listeners[i];
                if (observer != null && observer.equals(listener)) {
                    return ourInstance;
                }
            }

            /* Insert the observer at the first available slot. */
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] == null) {
                    listeners[i] = listener;
                    return ourInstance;
                }
            }

        } else {
            listeners = new LatestLogListener[Constants.MAX_LATEST_LOG_LISTENERS_COUNT];
            listeners[0] = listener;
            mObservers.put(userId, listeners);
        }
        return ourInstance;
    }

    public LatestLogPresenter detach(Object component, String userId) {
        LatestLogListener listener = (LatestLogListener) component;
        LatestLogListener[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] != null && listeners[i].equals(listener)) {
                    listeners[i] = null;
                }
            }

            /* If there is at least one observer attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] != null) {
                    return ourInstance;
                }
            }

            mObservers.remove(userId);
            stopSync(userId);

            /* If there are no observers, free the memory for garbage collection. */
            if (mObservers.size() == 0) {
                stopSync();
                ourInstance = null;
            } else {
                Collection<LatestLogListener[]> listenerArrays = mObservers.values();
                if (listenerArrays.size() > 0) {
                    for (LatestLogListener[] listenerArray : listenerArrays) {
                        for (int i = 0; i < listenerArray.length; i++) {
                            LatestLogListener observer = listenerArray[i];
                            if (observer != null) {
                                return ourInstance;
                            }
                        }
                    }
                }
                stopSync();
                ourInstance = null;
            }
        }
        return ourInstance;
    }

    public LatestLogPresenter addFriend(String userId) {
        DatabaseReference databaseReference = mReferences.get(userId);
        if (databaseReference == null) {
            mReferences.put(userId, LogsDatabaseHelper.getDataReference(userId));
        }

        String latestLogString = mSharedPreferences.getString(UniqueId.getLatestLogKey(userId), "");
        Log cachedLog = new Gson().fromJson(latestLogString, Log.class);
        if (cachedLog != null) {
            mLatestLogs.put(userId, cachedLog);
        }
        return ourInstance;
    }

    LatestLogPresenter removeFriend(String userId) {
        stopSync(userId);
        if (mObservers.containsKey(userId)) {
            mObservers.remove(userId);
        }
        if (mListeners.containsKey(userId)) {
            mListeners.remove(userId);
        }
        if (mReferences.containsKey(userId)) {
            mReferences.remove(userId);
        }
        if (mLatestLogs.containsKey(userId)) {
            mLatestLogs.remove(userId);
        }
        return ourInstance;
    }

    public LatestLogPresenter sync(final String userId) {
        if (userId == null) {
            return ourInstance;
        }

        ValueEventListener listener = mListeners.get(userId);
        if (listener == null) {
            DatabaseReference reference = mReferences.get(userId);
            if (reference == null) {
                reference = LogsDatabaseHelper.getDataReference(userId);
                mReferences.put(userId, reference);
            }
            listener = reference.limitToLast(1).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.hasChildren()) {

                        /*
                         * If the endTime field of the latest log has a default value (0), the move
                         * is still in progress.
                         */
                        Log log = dataSnapshot.getChildren().iterator().next().getValue(Log.class);
                        mLatestLogs.put(userId, log);
                        boolean inProgress = log.getEndTime() == 0;
                        notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, log, inProgress);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
            mListeners.put(userId, listener);
        }
        return ourInstance;
    }

    private LatestLogPresenter stopSync(String userId) {
        DatabaseReference databaseReference = mReferences.get(userId);
        ValueEventListener listener = mListeners.get(userId);
        if (databaseReference != null && listener != null) {
            databaseReference.removeEventListener(listener);
            mListeners.put(userId, null);
        }
        return ourInstance;
    }

    public LatestLogPresenter sync() {
        Set<String> keys = mReferences.keySet();
        if (keys.size() > 0) {
            for (String userId : keys) {
                sync(userId);
            }
        }
        return ourInstance;
    }

    private LatestLogPresenter stopSync() {
        Set<String> keys = mReferences.keySet();
        if (keys.size() > 0) {
            for (String userId : keys) {
                stopSync(userId);
            }
        }
        return ourInstance;
    }

    public boolean isInProgress(String userId) {
        Log log = mLatestLogs.get(userId);
        return log != null && log.getEndTime() == 0;
    }

    public Log get(String userId) {
        return mLatestLogs.get(userId);
    }

    public void stopLatestMove() {
        LatestLogDatabaseHelper.stopLatestMove();
    }

    private void notifyObservers(String userId, String event, Log log, boolean inProgress) {
        LatestLogListener[] observers = mObservers.get(userId);
        if (observers != null) {
            for (int i = 0; i < observers.length; i++) {
                LatestLogListener observer = observers[i];
                if (observer != null) {
                    switch (event) {
                        case Constants.CALLBACK_CHANGE_DATA:
                            observer.onLatestLogChanged(userId, log, inProgress);
                            break;
                        default:
                    }
                }
            }
        }
    }

    public interface LatestLogListener {
        void onLatestLogChanged(String userId, Log log, boolean inProgress);
    }
}
