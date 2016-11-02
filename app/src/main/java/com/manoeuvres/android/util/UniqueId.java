package com.manoeuvres.android.util;


import com.manoeuvres.android.friends.Friend;


public class UniqueId {

    public UniqueId() {

    }

    public static int getRequestId(Friend friend) {
        String key = friend.getFirebaseId() + "request";
        return key.hashCode();
    }

    public static int getFollowingId(Friend friend) {
        String key = friend.getFirebaseId() + "following";
        return key.hashCode();
    }

    public static int getLogId(Friend friend) {
        String key = friend.getFirebaseId() + "log";
        return key.hashCode();
    }

    public static int getMenuId(Friend friend) {
        String key = friend.getFirebaseId() + "menu";
        return key.hashCode();
    }

    public static String getLatestLogKey(String firebaseId) {
        String key = firebaseId + "latestLog";
        return String.valueOf(key.hashCode());
    }

    public static String getLogsDataKey(String firebaseId) {
        String key = firebaseId + "logsData";
        return String.valueOf(key.hashCode());
    }

    public static String getMovesDataKey(String firebaseId) {
        String key = firebaseId + "movesData";
        return String.valueOf(key.hashCode());
    }

    public static String getTimelineFragmentTag(Friend friend) {
        String tag = friend.getFirebaseId() + "fragmentTag";
        return tag;
    }
}
