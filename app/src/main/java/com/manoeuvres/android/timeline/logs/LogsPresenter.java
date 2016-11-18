package com.manoeuvres.android.timeline.logs;

import android.content.Context;
import android.support.v4.util.SimpleArrayMap;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.timeline.AbstractTimelinePresenter;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LogsPresenter extends AbstractTimelinePresenter {

    private static LogsPresenter ourInstance;

    private SimpleArrayMap<String, List<Log>> mLogs;

    private LogsPresenter(Context applicationContext) {
        super(applicationContext);

        mLogs = new SimpleArrayMap<>(Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1);
    }

    public static LogsPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) {
            ourInstance = new LogsPresenter(applicationContext);
        }
        return ourInstance;
    }

    @Override
    public int size(String userId) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null) {
            return logs.size();
        }
        return 0;
    }

    @Override
    protected void removeCollectionForUser(String userId) {
        if (mLogs.containsKey(userId)) {
            mLogs.remove(userId);
        }
    }

    @Override
    protected String getCacheKey(String userId) {
        return UniqueId.getLogsDataKey(userId);
    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return LogsDatabaseHelper.getDataReference(userId);
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return LogsDatabaseHelper.getCountReference(userId);
    }

    @Override
    protected void loadCache(String userId) {
        List<Log> logs = mLogs.get(userId);
        if (logs == null || logs.size() == 0) {
            String logsList = super.getSharedPreferences().getString(getCacheKey(userId), "");
            Type type = new TypeToken<List<Log>>() {
            }.getType();
            logs = new Gson().fromJson(logsList, type);
            if (logs == null) {
                logs = new ArrayList<>();
            }

            mLogs.put(userId, logs);
        }
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    @Override
    protected int getMaxListenersCount() {
        return Constants.MAX_LOGS_LISTENERS_COUNT;
    }

    @Override
    public Log get(String userId, String index) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null && logs.size() > 0) {
            return logs.get(Integer.valueOf(index));
        }
        return null;
    }

    @Override
    public List<Log> getAll(String userId) {
        return mLogs.get(userId);
    }

    @Override
    protected void notifyCompleteLoading(String userId, Object listenerObject) {
        LogsListener listener = (LogsListener) listenerObject;
        listener.onCompleteLogsLoading(userId);
    }

    @Override
    protected void notifyRemoveData(String userId, Object listenerObject,
                                    Object keyObject, Object dataObject) {
        LogsListener listener = (LogsListener) listenerObject;
        int index = (int) keyObject;
        Log log = (Log) dataObject;
        listener.onLogRemoved(userId, index, log);
    }

    @Override
    protected void notifyChangeData(String userId, Object listenerObject,
                                    Object keyObject, Object dataObject) {
        LogsListener listener = (LogsListener) listenerObject;
        int index = (int) keyObject;
        Log log = (Log) dataObject;
        listener.onLogChanged(userId, index, log);
    }

    @Override
    protected void notifyAddData(String userId, Object listenerObject,
                                 Object keyObject, Object dataObject) {
        LogsListener listener = (LogsListener) listenerObject;
        int index = (int) keyObject;
        Log log = (Log) dataObject;
        listener.onLogAdded(userId, index, log);
    }

    @Override
    protected void notifyInitialLoading(String userId, Object listenerObject) {
        LogsListener listener = (LogsListener) listenerObject;
        listener.onLogsInitialization(userId);
    }

    @Override
    protected void notifyStartLoading(String userId, Object listenerObject) {
        LogsListener listener = (LogsListener) listenerObject;
        listener.onStartLogsLoading(userId);
    }

    @Override
    protected boolean hasData(String userId) {
        List<Log> logs = mLogs.get(userId);
        return (logs != null && logs.size() > 0);
    }

    @Override
    protected void clearDataForUser(String userId) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null) {
            clearLogsInThisList(userId, logs);
        }
    }

    private void clearLogsInThisList(String userId, List<Log> logs) {
        for (int i = 0; i < logs.size(); i++) {
            removeLogFromThisList(userId, logs.get(i), logs);
        }
    }

    @Override
    protected int getDataLimit() {
        return Constants.LIMIT_LOG_COUNT;
    }

    @Override
    protected Object getNewCollection(String userId) {
        return new ArrayList<Log>() {
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void addDataToCachedAndUpdatedCollections(String userId,
                                                        DataSnapshot dataSnapshot,
                                                        Object updatedCollection) {
        List<Log> logs = mLogs.get(userId);
        Log newLog = dataSnapshot.getValue(Log.class);
        if (logs != null) {
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
        }

        List<Log> updatedLogs = (List<Log>) updatedCollection;
        updatedLogs.add(newLog);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected int getCollectionSize(Object updatedCollection) {
        List<Log> updatedLogs = (List<Log>) updatedCollection;
        return updatedLogs.size();
    }

    @Override
    protected int getUserCollectionSize(String userId) {
        List<Log> logs = mLogs.get(userId);
        if (logs != null) {
            return logs.size();
        }
        return 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void removeRedundantDataFromUserCollection(String userId, Object updatedCollection) {
        List<Log> logs = mLogs.get(userId);
        List<Log> removedLogs = new ArrayList<>(logs);
        List<Log> updatedLogs = (List<Log>) updatedCollection;
        removedLogs.removeAll(updatedLogs);
        for (int i = 0; i < removedLogs.size(); i++) {
            removeLogFromThisList(userId, removedLogs.get(i), logs);
        }
    }

    @Override
    protected void updateData(String userId, DataSnapshot dataSnapshot) {
        List<Log> logs = mLogs.get(userId);
        Log updatedLog = dataSnapshot.getValue(Log.class);
        Log oldLog = null;
        if (logs != null) {
            oldLog = logs.get(0);  //Changes are only allowed to the latest log, if it's in progress.
        }
        if (oldLog != null
                && (oldLog.getMoveId().equals(updatedLog.getMoveId()))
                && (oldLog.getStartTime() == updatedLog.getStartTime())) {
            oldLog.setEndTime(updatedLog.getEndTime());
            notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, logs.indexOf(oldLog), oldLog);
        }
    }

    @Override
    protected void removeData(String userId, DataSnapshot dataSnapshot) {
        List<Log> logs = mLogs.get(userId);
        Log removedLog = dataSnapshot.getValue(Log.class);
        removeLogFromThisList(userId, removedLog, logs);
    }

    private void removeLogFromThisList(String userId, Log removedLog, List<Log> logs) {
        if (logs != null && logs.contains(removedLog)) {
            Log log = new Log(removedLog);
            int index = logs.indexOf(removedLog);
            logs.remove(removedLog);
            notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, index, log);
        }
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
