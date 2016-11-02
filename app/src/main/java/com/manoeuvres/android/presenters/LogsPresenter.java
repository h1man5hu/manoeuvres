package com.manoeuvres.android.presenters;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.models.Log;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogsPresenter {
    private static LogsPresenter ourInstance;

    private Map<String, List<Log>> mLogs;

    private Map<String, DatabaseReference> mCountReferences;
    private Map<String, DatabaseReference> mDataReferences;

    private Map<String, ValueEventListener> mCountListeners;
    private Map<String, ChildEventListener> mDataListeners;

    private Map<String, LogsListener[]> mObservers;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private Map<String, Boolean> mIsLoaded;

    private LogsPresenter(Context applicationContext) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        mGson = new Gson();

        int capacity = Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1;
        mLogs = new HashMap<>(capacity);
        mCountReferences = new HashMap<>(capacity);
        mDataReferences = new HashMap<>(capacity);
        mCountListeners = new HashMap<>(capacity);
        mDataListeners = new HashMap<>(capacity);
        mObservers = new HashMap<>(capacity);
        mIsLoaded = new HashMap<>(capacity);
    }

    public static LogsPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) ourInstance = new LogsPresenter(applicationContext);
        return ourInstance;
    }

    public LogsPresenter attach(Object component, String userId) {
        LogsListener listener = (LogsListener) component;
        LogsListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            /* If the observer is already attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                LogsListener observer = listeners[i];
                if (observer != null && observer.equals(listener)) return ourInstance;
            }

            /* Insert the observer at the first available slot. */
            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] == null) {
                    listeners[i] = listener;
                    return ourInstance;
                }
        } else {
            listeners = new LogsListener[Constants.MAX_LOGS_LISTENERS_COUNT];
            listeners[0] = listener;
            mObservers.put(userId, listeners);
        }

        return ourInstance;
    }

    public LogsPresenter detach(Object component, String userId) {
        LogsListener listener = (LogsListener) component;
        LogsListener[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] != null && listeners[i].equals(listener))
                    listeners[i] = null;

            /* If there is at least one observer attached, return. */
            for (int i = 0; i < listeners.length; i++) if (listeners[i] != null) return ourInstance;

            mObservers.remove(userId);
            stopSync(userId);

            /* If there are no observers, free the memory for garbage collection. */
            if (mObservers.size() == 0) ourInstance = null;
            else {
                Collection<LogsListener[]> listenerArrays = mObservers.values();
                if (listenerArrays.size() > 0)
                    for (LogsListener[] listenerArray : listenerArrays)
                        for (int i = 0; i < listenerArray.length; i++) {
                            LogsListener observer = listenerArray[i];
                            if (observer != null) return ourInstance;
                        }
                ourInstance = null;
            }
        }

        return ourInstance;
    }

    public LogsPresenter addFriend(String userId) {

        /*
         * Caching: Load the logs associated to the current user from the shared preferences file.
         * Update the list when network can be accessed.
         */
        List<Log> logs = mLogs.get(userId);
        if (logs == null || logs.size() == 0) {
            String logsList = mSharedPreferences.getString(UniqueId.getLogsDataKey(userId), "");
            Type type = new TypeToken<List<Log>>() {
            }.getType();
            logs = mGson.fromJson(logsList, type);
            if (logs == null) logs = new ArrayList<>();

            mLogs.put(userId, logs);
        }

        DatabaseReference countReference = mCountReferences.get(userId);
        if (countReference == null) mCountReferences.put(userId
                , DatabaseHelper.mMetaLogsReference.child(userId).child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT));
        DatabaseReference dataReference = mDataReferences.get(userId);
        if (dataReference == null) mDataReferences.put(userId
                , DatabaseHelper.mLogsReference.child(userId));

        return ourInstance;
    }

    public LogsPresenter removeFriend(String userId) {
        stopSync(userId);
        if (mCountReferences.containsKey(userId)) mCountReferences.remove(userId);
        if (mDataReferences.containsKey(userId)) mDataReferences.remove(userId);
        if (mLogs.containsKey(userId)) mLogs.remove(userId);
        if (mObservers.containsKey(userId)) mObservers.remove(userId);
        if (mIsLoaded.containsKey(userId)) mIsLoaded.remove(userId);
        mSharedPreferences.edit().remove(UniqueId.getLogsDataKey(userId)).apply();

        return ourInstance;
    }

    public LogsPresenter sync(final String userId) {
        ValueEventListener logsCountListener = mCountListeners.get(userId);
        if (logsCountListener == null) {
            notifyObservers(userId, Constants.CALLBACK_START_LOADING);

            final List<Log> logs = mLogs.get(userId);
             /* If there is no cache, display progress until the data is loaded from the network. */
            if (logs.size() == 0) notifyObservers(userId, Constants.CALLBACK_INITIAL_LOADING);

            logsCountListener = mCountReferences.get(userId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());


                    /*
                     * If the count on the Firebase database is zero but there are logs in the cached list,
                     * remove all of them.
                     */
                    if (count == 0) {
                        for (int i = 0; i < logs.size(); i++) logs.remove(i);
                        notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                    } else if (count > 0) {

                        /*
                         * In case not all but some of the logs were removed, this list will
                         * be subtracted from the cached list to get the logs which were removed.
                         * Each log on the Firebase database is added to this list.
                         * The subtraction will be done when all the moves have been retrieved from the
                         * Firebase database.
                         */
                        final List<Log> updatedLogs = new ArrayList<>();

                        ChildEventListener logsListener = mDataListeners.get(userId);
                        if (logsListener == null) {
                            logsListener = mDataReferences.get(userId).limitToLast(Constants.LIMIT_LOG_COUNT).addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Log newLog = dataSnapshot.getValue(Log.class);
                                    int index = logs.indexOf(newLog);
                                    if (index == -1) {
                                        if (logs.size() >= Constants.LIMIT_LOG_COUNT) {
                                            logs.remove(logs.size() - 1);
                                        }
                                        logs.add(0, newLog);
                                        notifyObservers(userId, Constants.CALLBACK_ADD_DATA, 0, newLog);
                                    } else {    // If due to any bug, a previous log wasn't updated, update it now.
                                        Log oldLog = logs.get(index);
                                        if (oldLog.getEndTime() == 0 && newLog.getEndTime() != 0) {
                                            oldLog.setEndTime(newLog.getEndTime());
                                            notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, index, oldLog);
                                        }
                                    }

                                    updatedLogs.add(newLog);

                                   /*
                                    * All the logs have been retrieved, subtract the list and notify
                                    * removed logs to listeners, if any.
                                    */
                                    int limit = Constants.LIMIT_LOG_COUNT;
                                    if (count < limit) {
                                        limit = count;
                                    }
                                    if (updatedLogs.size() == limit) {
                                        if (logs.size() > limit) {
                                            List<Log> removedLogs = new ArrayList<>(logs);
                                            for (int i = 0; i < removedLogs.size(); i++) {
                                                Log removedLog = removedLogs.get(i);
                                                Log log = new Log(removedLog);
                                                int removedIndex = logs.indexOf(removedLog);
                                                logs.remove(removedLog);
                                                notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, removedIndex, log);
                                            }
                                        }
                                        notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                                    }
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                    Log updatedLog = dataSnapshot.getValue(Log.class);
                                    Log oldLog = logs.get(0);  //Changes are only allowed to the latest log, if it's in progress.
                                    if ((oldLog.getMoveId().equals(updatedLog.getMoveId())) && (oldLog.getStartTime() == updatedLog.getStartTime())) {
                                        oldLog.setEndTime(updatedLog.getEndTime());
                                        notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, logs.indexOf(oldLog), oldLog);
                                    }
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Log removedLog = dataSnapshot.getValue(Log.class);
                                    if (logs.contains(removedLog)) {
                                        Log log = new Log(removedLog);
                                        int index = logs.indexOf(removedLog);
                                        logs.remove(removedLog);
                                        notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, index, log);
                                    }
                                }

                                @Override
                                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                            mDataListeners.put(userId, logsListener);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
            mCountListeners.put(userId, logsCountListener);
        }

        return ourInstance;
    }

    public LogsPresenter stopSync(String userId) {
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

        return ourInstance;
    }

    public LogsPresenter sync() {
        Set<String> keys = mCountReferences.keySet();
        if (keys.size() > 0) for (String userId : keys) sync(userId);
        return ourInstance;
    }

    public LogsPresenter stopSync() {
        Set<String> keys = mCountReferences.keySet();
        if (keys.size() > 0) for (String userId : keys) stopSync(userId);
        return ourInstance;
    }

    public Log get(String userId, int index) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null) return logs.get(index);
        return null;
    }

    public List<Log> getAll(String userId) {
        return mLogs.get(userId);
    }

    public int size(String userId) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null) return logs.size();
        return 0;
    }

    public Boolean isLoaded(String userId) {
        Boolean isLoaded = mIsLoaded.get(userId);
        if (isLoaded != null) return isLoaded;
        else return false;
    }

    private void notifyObservers(String userId, String event, int index, Log log) {
        LogsListener[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                LogsListener listener = listeners[i];
                if (listener != null)
                    switch (event) {
                        case Constants.CALLBACK_INITIAL_LOADING:
                            listener.onLogsInitialization(userId);
                            break;
                        case Constants.CALLBACK_START_LOADING:
                            listener.onStartLogsLoading(userId);
                            break;
                        case Constants.CALLBACK_ADD_DATA:
                            listener.onLogAdded(userId, index, log);
                            break;
                        case Constants.CALLBACK_CHANGE_DATA:
                            listener.onLogChanged(userId, index, log);
                            break;
                        case Constants.CALLBACK_REMOVE_DATA:
                            listener.onLogRemoved(userId, index, log);
                        case Constants.CALLBACK_COMPLETE_LOADING:
                            mIsLoaded.put(userId, true);
                            listener.onCompleteLogsLoading(userId);
                            break;
                    }
            }
        }
    }

    private void notifyObservers(String userId, String event) {
        notifyObservers(userId, event, 0, null);
    }

    public interface LogsListener {
        void onStartLogsLoading(String userId);

        void onLogsInitialization(String userId);

        void onLogAdded(String userId, int index, Log log);

        void onLogChanged(String userId, int index, Log log);

        void onLogRemoved(String userId, int index, Log log);

        void onCompleteLogsLoading(String userId);
    }
}
