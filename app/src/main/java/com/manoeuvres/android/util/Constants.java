package com.manoeuvres.android.util;


public class Constants {

    public static final int FRAGMENT_FOLLOWERS = 101;
    public static final int FRAGMENT_FOLLOWING = 102;
    public static final int FRAGMENT_FIND_FRIENDS = 103;
    public static final int FRAGMENT_REQUESTS = 104;

    public static final String TAG_LOG_ACTIVITY_MAIN = "MainActivityLog";
    public static final String TAG_LOG_FRAGMENT_FRIENDS = "FriendsFragmentLog";
    public static final String TAG_LOG_FRAGMENT_TIMELINE = "TimelineFragmentLog";
    public static final String TAG_LOG_ACTIVITY_LOGIN = "LoginActivityLog";
    public static final String TAG_LOG_ACTIVITY_LAUNCH_SCREEN = "LaunchScreenActivityLog";
    public static final String TAG_LOG_SERVICE_NOTIFICATION = "NotificationServiceLog";

    public static final String TAG_FRAGMENT_TIMELINE = "timelineFragment";
    public static final String TAG_FRAGMENT_FOLLOWING = "followingFragment";
    public static final String TAG_FRAGMENT_FOLLOWERS = "followersFragment";
    public static final String TAG_FRAGMENT_FIND_FRIENDS = "findFriendsFragment";
    public static final String TAG_FRAGMENT_REQUESTS = "requestsFragment";

    public static final String FIREBASE_DATABASE_REFERENCE_USERS = "users";
    public static final String FIREBASE_DATABASE_REFERENCE_USERS_NAME = "name";
    public static final String FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID = "facebookId";
    public static final String FIREBASE_DATABASE_REFERENCE_USERS_ONLINE = "online";
    public static final String FIREBASE_DATABASE_REFERENCE_USERS_LAST_SEEN = "lastSeenAt";
    public static final String FIREBASE_DATABASE_REFERENCE_FOLLOWERS = "followers";
    public static final String FIREBASE_DATABASE_REFERENCE_FOLLOWING = "following";
    public static final String FIREBASE_DATABASE_REFERENCE_MOVES = "moves";
    public static final String FIREBASE_DATABASE_REFERENCE_LOGS = "logs";
    public static final String FIREBASE_DATABASE_REFERENCE_LOGS_ENDTIME = "endTime";
    public static final String FIREBASE_DATABASE_REFERENCE_REQUESTS = "requests";
    public static final String FIREBASE_DATABASE_REFERENCE_META = "meta";
    public static final String FIREBASE_DATABASE_REFERENCE_META_LOGS = "logs";
    public static final String FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT = "count";
    public static final String FIREBASE_DATABASE_REFERENCE_META_FOLLOWING = "following";
    public static final String FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT = "count";
    public static final String FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS = "followers";
    public static final String FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT = "count";
    public static final String FIREBASE_DATABASE_REFERENCE_META_REQUESTS = "requests";
    public static final String FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT = "count";
    public static final String FIREBASE_DATABASE_REFERENCE_META_MOVES = "moves";
    public static final String FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT = "count";

    public static final String KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE_ = "firebaseUserId";
    public static final String KEY_ARGUMENTS_USER_NAME_FRAGMENT_TIMELINE = "username";
    public static final String KEY_ARGUMENTS_FRAGMENT_BEHAVIOR_FRIENDS = "FRAGMENT_BEHAVIOR";

    public static final String FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS = "fields";
    public static final String FACEBOOK_FIELD_GRAPH_REQUEST_DATA = "data";
    public static final String FACEBOOK_FIELD_GRAPH_REQUEST_ID = "id";
    public static final String FACEBOOK_FIELD_GRAPH_REQUEST_NAME = "name";
    public static final String FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS = "friends";

    public static final String FACEBOOK_PERMISSION_EMAIL = "email";
    public static final String FACEBOOK_PERMISSION_PUBLIC_PROFILE = "public_profile";
    public static final String FACEBOOK_PERMISSION_USER_FRIENDS = "user_friends";

    public static final String KEY_SHARED_PREF_COUNT_FRIENDS = "friendsCount";
    public static final String KEY_SHARED_PREF_COUNT_FOLLOWERS = "followersCount";
    public static final String KEY_SHARED_PREF_COUNT_FOLLOWING = "followingCount";
    public static final String KEY_SHARED_PREF_COUNT_REQUESTS = "requestsCount";
    public static final String KEY_SHARED_PREF_DATA_FOLLOWING = "followingData";
    public static final String KEY_SHARED_PREF_DATA_FRIENDS = "friendsData";
    public static final String KEY_SHARED_PREF_IS_MOVE_IN_PROGRESS = "isMoveInProgress";

    public static final String KEY_EXTRA_FRAGMENT_REQUESTS = "requestsFragmentKey";
    public static final String KEY_EXTRA_FRAGMENT_TIMELINE = "timelineFragmentKey";
    public static final String KEY_EXTRA_FRAGMENT_TIMELINE_FRIEND_ID = "friendIdKey";

    public static final int LIMIT_LOG_COUNT = 20;
}
