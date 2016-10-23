package com.manoeuvres.android.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.R;
import com.manoeuvres.android.activities.MainActivity;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.models.Log;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.TextHelper;
import com.manoeuvres.android.util.UniqueId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;


public class NotificationService extends Service {

    /* Start the service only if it's not already started. */
    private boolean mIsStarted;

    private DatabaseReference mUsersReference;
    private DatabaseReference mLogsReference;
    private DatabaseReference mMovesReference;
    private DatabaseReference mUserFollowingReference;
    private DatabaseReference mUserRequestsReference;
    private DatabaseReference mUserFollowingCountReference;
    private DatabaseReference mUserRequestsCountReference;

    private ChildEventListener mUserFollowingListener;
    private ChildEventListener mUserRequestsListener;
    private ValueEventListener mUserFollowingCountListener;
    private ValueEventListener mUserRequestsCountListener;

    private List<Friend> mAllFriends;
    private List<Friend> mFollowing;
    private List<Friend> mRequests;

    /*
     * The number of friends that the user follows is not already known.
     * The database references and listeners for each friend are created dynamically.
     * These maps map them with the friend that they belong to.
     * The listeners can then be attached or detached based on network availability.
     */
    private Map<Friend, DatabaseReference> mReferenceMap;
    private Map<Friend, ValueEventListener> mEventListenerMap;

    private SharedPreferences mSharedPreferences;

    private NotificationManager mNotificationManager;

    private Gson mGson;

    private ConnectivityManager mConnectivityManager;
    private boolean mIsConnected;
    private BroadcastReceiver mNetworkReceiver;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mIsStarted) {
            mIsStarted = true;

            FacebookSdk.sdkInitialize(this);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference rootReference = database.getReference();
            mUsersReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);
            DatabaseReference followingReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING);
            DatabaseReference requestsReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS);
            mLogsReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS);
            mMovesReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES);
            DatabaseReference metaReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
            DatabaseReference metaFollowingReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING);
            DatabaseReference metaRequestsReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS);
            if (user != null) {
                mUserFollowingReference = followingReference.child(user.getUid());
                mUserRequestsReference = requestsReference.child(user.getUid());
                mUserRequestsCountReference = metaRequestsReference.child(user.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
                mUserFollowingCountReference = metaFollowingReference.child(user.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
            }

            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            mGson = new Gson();

            /* Caching: Load the details of the Facebook friends of the user from the shared preferences file.
             * Update the list when network can be accessed. */
            String allFriendsList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "");
            Type type = new TypeToken<List<Friend>>() {
            }.getType();
            mAllFriends = mGson.fromJson(allFriendsList, type);
            if (mAllFriends == null) {
                mAllFriends = new ArrayList<>();
            }

            mNotificationManager = (NotificationManager) getSystemService(NotificationService.NOTIFICATION_SERVICE);

            mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

            checkNetworkAndSyncData();

            if (mNetworkReceiver == null) {
                mNetworkReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getAction().equals(CONNECTIVITY_ACTION)) {
                            checkNetworkAndSyncData();
                        }
                    }
                };
                registerReceiver(mNetworkReceiver, new IntentFilter(CONNECTIVITY_ACTION));
            }
        }

        return START_STICKY;
    }

    private void checkNetworkAndSyncData() {
        isConnected();
        if (mIsConnected) {
            startDataSync();
        } else {
            stopDataSync();
        }
    }

    private void isConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        mIsConnected = networkInfo != null && networkInfo.isConnected();
    }

    private void startDataSync() {
        updateAllFriends();
        listenRequests();

        /* This method also attaches a listener to the logs of each friend the user is following. */
        listenFollowing();

        /*
         * If there are friends in the cache, or there was a network connectivity change, and the
         * friends the user is following are already loaded, attach a listener to their logs reference.
         */
        if (mFollowing != null) {
            for (Friend friend : mFollowing) {
                DatabaseReference reference = mReferenceMap.get(friend);
                ValueEventListener listener = mEventListenerMap.get(friend);

                if (reference != null && listener != null) {
                    reference.addValueEventListener(listener);
                }
            }
        }
    }

    private void updateAllFriends() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        JSONArray friends;
                        try {
                            friends = object.getJSONObject(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS).getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                            final int numberOfFriends = friends.length();
                            for (int i = 0; i < numberOfFriends; i++) {
                                JSONObject friendJSONObject = friends.getJSONObject(i);
                                final Friend friend = new Friend(friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID),
                                        friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME));
                                mUsersReference.orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).
                                        equalTo(friend.getFacebookId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        friend.setFirebaseId(dataSnapshot.getChildren().iterator().next().getKey());
                                        int index = mAllFriends.indexOf(friend);
                                        if (index != -1) {
                                            mAllFriends.remove(index);
                                            mAllFriends.add(index, friend);
                                        } else {
                                            mAllFriends.add(friend);
                                        }


                                        /* If all the friends have been loaded, update the cache. */
                                        if (mAllFriends.size() == numberOfFriends) {
                                            mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, mGson.toJson(mAllFriends)).apply();
                                        }

                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {

                                    }
                                });
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS, Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS);
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void listenRequests() {
        if (mUserRequestsCountListener == null) {
            mUserRequestsCountListener = mUserRequestsCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    /*
                     * The requests are not cached to be displayed. They are too dynamic to be displayed like this.
                     * Instead, the requests cache is used to find any difference between the Firebase database
                     * and the local app state. If any request is added and it is not there in the cache,
                     * a notification is displayed for it.
                     */
                    String requestsData = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_REQUESTS, "");
                    Type type = new TypeToken<List<Friend>>() {
                    }.getType();
                    mRequests = mGson.fromJson(requestsData, type);

                    if (mRequests == null) {
                        mRequests = new ArrayList<>();
                    }

                    if (count > 0) {
                        mUserRequestsListener = mUserRequestsReference.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                Friend friend = new Friend(dataSnapshot.getValue().toString());
                                if (!mRequests.contains(friend)) {
                                    mRequests.add(friend);
                                    int index = mAllFriends.indexOf(friend);
                                    if (index != -1) {
                                        friend = mAllFriends.get(index);
                                        displayNotification(friend, Constants.NOTIFICATION_TYPE_REQUEST);
                                    }
                                }
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                Friend friend = new Friend(dataSnapshot.getValue().toString());
                                if (mRequests.contains(friend)) {
                                    mRequests.remove(friend);
                                }
                                mNotificationManager.cancel(UniqueId.getRequestId(friend));
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

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void listenFollowing() {
        if (mUserFollowingCountListener == null) {
            mUserFollowingCountListener = mUserFollowingCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    /*
                     * The following friends are not cached to be displayed. They are too dynamic to be displayed like this.
                     * Instead, the following friends cache is used to find any difference between the Firebase database
                     * and the local app state. If any friend approves the follow request and it is not there in the cache,
                     * a notification is displayed for it.
                     */
                    String followingData = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, "");
                    Type type = new TypeToken<List<Friend>>() {
                    }.getType();
                    mFollowing = mGson.fromJson(followingData, type);

                    if (mFollowing == null) {
                        mFollowing = new ArrayList<>();
                    }

                    if (mReferenceMap == null) {
                        mReferenceMap = new HashMap<>();
                    }

                    if (mEventListenerMap == null) {
                        mEventListenerMap = new HashMap<>();
                    }

                    if (count > 0) {
                        mUserFollowingListener = mUserFollowingReference.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                Friend friend = new Friend(dataSnapshot.getValue().toString());
                                if (!mFollowing.contains(friend)) {
                                    mFollowing.add(friend);
                                    int index = mAllFriends.indexOf(friend);
                                    if (index != -1) {
                                        friend = mAllFriends.get(index);
                                        displayNotification(friend, Constants.NOTIFICATION_TYPE_FOLLOWING);

                                        /*
                                         * This is a low priority notification. Update the cache when the notification is displayed
                                         * so that it appears only once.
                                         */
                                        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowing)).apply();
                                    }
                                }
                                setLogListener(friend);
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                Friend friend = new Friend(dataSnapshot.getValue().toString());
                                if (mFollowing.contains(friend)) {
                                    mFollowing.remove(friend);
                                }
                                mNotificationManager.cancel(UniqueId.getFollowingId(friend));
                                mNotificationManager.cancel(UniqueId.getLogId(friend));

                                mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowing)).apply();

                                /*
                                 * A removed listener can be attached again, for example, when network connectivity is restored.
                                 * A friend which the user has stopped following, won't need to have a listener attached again.
                                 * Therefore, remove the reference and the listener for the friend from the map as well.
                                 */
                                removeLogListener(friend);
                                mReferenceMap.remove(friend);
                                mEventListenerMap.remove(friend);
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

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void setLogListener(final Friend friend) {
        DatabaseReference reference = mReferenceMap.get(friend);
        ValueEventListener listener = mEventListenerMap.get(friend);

        /*
         * If the reference and the listener are already there, but were detached due to network connectivity change,
         * attach them again.
         */
        if (reference != null && listener != null) {
            reference.addValueEventListener(listener);
            return;
        }

        if (reference == null && listener == null) {
            reference = mLogsReference.child(friend.getFirebaseId());
            listener = reference.limitToLast(1).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log newLog = dataSnapshot.getChildren().iterator().next().getValue(Log.class);

                    /*
                     * The latest log which the user has seen for each friend is stored in the cache.
                     * A notification is displayed based on the cached log and the latest log.
                     */
                    String oldLogCache = mSharedPreferences.getString(UniqueId.getLatestLogKey(friend.getFirebaseId()), "");
                    Log oldLog = mGson.fromJson(oldLogCache, Log.class);

                    /* If there is no cache, display the notification for the latest log. */
                    if ((oldLog == null && newLog != null)
                            || (oldLog != null && newLog != null
                            && newLog.getStartTime() > oldLog.getStartTime())) {
                        boolean inProgress = true;
                        if (newLog.getEndTime() > 0) {
                            inProgress = false;
                        }
                        displayNotification(friend, inProgress, newLog);
                    }

                    /* If the log is same but it has ended, display a notification. */
                    else if (oldLog != null && newLog != null
                            && oldLog.getStartTime() == newLog.getStartTime()
                            && oldLog.getMoveId().equals(newLog.getMoveId())
                            && oldLog.getEndTime() == 0
                            && newLog.getEndTime() > 0) {
                        displayNotification(friend, false, newLog);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
        mReferenceMap.put(friend, reference);
        mEventListenerMap.put(friend, listener);
    }

    private void removeLogListener(Friend friend) {
        DatabaseReference reference = mReferenceMap.get(friend);
        ValueEventListener listener = mEventListenerMap.get(friend);

        if (reference != null && listener != null) {
            reference.removeEventListener(listener);
        }
    }

    /* Called from the overloaded methods to display a notification. */
    private void displayNotification(final Friend friend, String type, boolean inProgress, final Log log) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Constants.KEY_EXTRA_NOTIFICATION_SERVICE, type);

        /*
         * If the user clicks on a notification for a log update from a friend, or a friend accepting the request,
         * display the friend's timeline. The Firebase Id of the friend is passed to the main activity.
         */
        if (type.equals(Constants.NOTIFICATION_TYPE_LOG) || (type.equals(Constants.NOTIFICATION_TYPE_FOLLOWING))) {
            intent.putExtra(Constants.KEY_EXTRA_FRAGMENT_TIMELINE_FRIEND_ID, friend.getFirebaseId());
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(NotificationService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        final android.support.v4.app.NotificationCompat.Builder builder = new android.support.v4.app.NotificationCompat
                .Builder(NotificationService.this)
                .setSmallIcon(R.drawable.com_facebook_profile_picture_blank_square)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SOCIAL);
        }

        if (mAllFriends.contains(friend)) {
            builder.setContentTitle(mAllFriends.get(mAllFriends.indexOf(friend)).getName());
        } else {
            builder.setContentTitle(getString(R.string.app_name));
        }

        switch (type) {
            case Constants.NOTIFICATION_TYPE_REQUEST:
                builder.setContentText(getString(R.string.notification_text_new_request));
                mNotificationManager.notify(UniqueId.getRequestId(friend), builder.build());
                break;
            case Constants.NOTIFICATION_TYPE_FOLLOWING:
                builder.setContentText(getString(R.string.notification_text_request_accepted));
                mNotificationManager.notify(UniqueId.getFollowingId(friend), builder.build());
                break;
            case Constants.NOTIFICATION_TYPE_LOG:
                DatabaseReference reference = mMovesReference.child(friend.getFirebaseId()).child(log.getMoveId());
                if (inProgress) {
                    reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PRESENT).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            builder.setContentText(getString(R.string.notification_text_log_present) + dataSnapshot.getValue().toString().toLowerCase() + ".");
                            mNotificationManager.notify(UniqueId.getLogId(friend), builder.build());
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                } else {
                    reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PAST).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            builder.setContentText(getString(R.string.notification_text_log_past) + dataSnapshot.getValue().toString().toLowerCase() + " "
                                    + String.format(getString(R.string.log_sub_title_text_past), TextHelper.getDurationText(log.getStartTime(), log.getEndTime())).trim()
                                    + ".");
                            mNotificationManager.notify(UniqueId.getLogId(friend), builder.build());
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
                break;
        }
    }

    /* Displays a notification for new requests or accepted requests. */
    private void displayNotification(Friend friend, String type) {
        displayNotification(friend, type, false, null);
    }

    /* Displays a notification for a new or updated log. */
    private void displayNotification(Friend friend, boolean inProgress, Log log) {
        displayNotification(friend, Constants.NOTIFICATION_TYPE_LOG, inProgress, log);
    }

    private void stopDataSync() {
        if (mUserRequestsCountReference != null && mUserRequestsCountListener != null) {
            mUserRequestsCountReference.removeEventListener(mUserRequestsCountListener);
        }
        if (mUserRequestsReference != null && mUserRequestsListener != null) {
            mUserRequestsReference.removeEventListener(mUserRequestsListener);
        }
        if (mUserFollowingCountReference != null && mUserFollowingCountListener != null) {
            mUserFollowingCountReference.removeEventListener(mUserFollowingCountListener);
        }
        if (mUserFollowingReference != null && mUserFollowingListener != null) {
            mUserFollowingReference.removeEventListener(mUserFollowingListener);
        }

        for (Friend friend : mFollowing) {
            DatabaseReference reference = mReferenceMap.get(friend);
            ValueEventListener listener = mEventListenerMap.get(friend);

            if (reference != null && listener != null) {
                reference.removeEventListener(listener);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
