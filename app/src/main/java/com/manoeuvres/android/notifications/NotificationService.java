package com.manoeuvres.android.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import com.google.gson.Gson;
import com.manoeuvres.android.R;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.notifications.LatestLogPresenter.LatestLogListener;
import com.manoeuvres.android.timeline.logs.LogsPresenter;
import com.manoeuvres.android.timeline.moves.MovesPresenter;
import com.manoeuvres.android.friends.requests.RequestsPresenter;
import com.manoeuvres.android.friends.requests.RequestsPresenter.RequestsListener;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.timeline.logs.Log;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.TextHelper;
import com.manoeuvres.android.util.UniqueId;

import java.util.List;


public class NotificationService extends Service implements RequestsListener, FollowingListener, LatestLogListener {

    /* Start the service only once. */
    private boolean mIsStarted;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private NotificationManager mNotificationManager;

    private FacebookFriendsPresenter mFacebookFriendsPresenter;
    private FollowingPresenter mFollowingPresenter;
    private MovesPresenter mMovesPresenter;
    private LatestLogPresenter mLatestLogPresenter;
    private SeenRequestsPresenter mSeenRequestsPresenter;
    private SeenFollowingPresenter mSeenFollowingPresenter;

    /*
     * Only used to remove any data in the heap, if a friend is removed. The service listens to the
     * latest log, not all the logs.
     */
    private LogsPresenter mLogsPresenter;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mIsStarted) {
            mIsStarted = true;

            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mGson = new Gson();

            mNotificationManager = (NotificationManager) getSystemService(NotificationService.NOTIFICATION_SERVICE);

            mSeenRequestsPresenter = SeenRequestsPresenter.getInstance().sync();
            mSeenFollowingPresenter = SeenFollowingPresenter.getInstance().sync();
            mFacebookFriendsPresenter = FacebookFriendsPresenter.getInstance(getApplicationContext()).updateFacebookFriends();
            mFollowingPresenter = FollowingPresenter.getInstance(getApplicationContext()).attach(this).sync();
            mMovesPresenter = MovesPresenter.getInstance(getApplicationContext());
            mLatestLogPresenter = LatestLogPresenter.getInstance(getApplicationContext());
            mLogsPresenter = LogsPresenter.getInstance(getApplicationContext());
            RequestsPresenter.getInstance().attach(this).sync();

            if (mFollowingPresenter.isLoaded()) onCompleteFollowingLoading();
        }
        return START_STICKY;
    }

    @Override
    public void onRequestAdded(int index, Friend friend) {
        if (!mSeenRequestsPresenter.contains(friend))
            displayNotification(friend, Constants.NOTIFICATION_TYPE_REQUEST);
    }

    @Override
    public void onRequestRemoved(int index, Friend friend) {
        mNotificationManager.cancel(UniqueId.getRequestId(friend));
    }

    @Override
    public void onFollowingAdded(int index, Friend friend) {
        if (!mSeenFollowingPresenter.contains(friend))
            displayNotification(friend, Constants.NOTIFICATION_TYPE_FOLLOWING);
        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowingPresenter.getAll())).apply();
        mLatestLogPresenter.addFriend(friend.getFirebaseId()).attach(this, friend.getFirebaseId()).sync(friend.getFirebaseId());
    }

    @Override
    public void onFollowingRemoved(int index, Friend friend) {
        mNotificationManager.cancel(UniqueId.getFollowingId(friend));
        mNotificationManager.cancel(UniqueId.getLogId(friend));
        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowingPresenter.getAll())).apply();
        mLatestLogPresenter.removeFriend(friend.getFirebaseId());
        mMovesPresenter.removeFriend(friend.getFirebaseId());
        mLogsPresenter.removeFriend(friend.getFirebaseId());
        mSharedPreferences.edit().remove(UniqueId.getLatestLogKey(friend.getFirebaseId()))
                .remove(UniqueId.getLogsDataKey(friend.getFirebaseId()))
                .remove(UniqueId.getMovesDataKey(friend.getFirebaseId()))
                .apply();
    }

    @Override
    public void onLatestLogChanged(String userId, Log newLog, boolean inProgress) {
        /*
         * The latest log which the user has seen for each friend is stored in the cache.
         * A notification is displayed based on the cached log and the latest log.
         */
        String oldLogCache = mSharedPreferences.getString(UniqueId.getLatestLogKey(userId), "");
        Log oldLog = mGson.fromJson(oldLogCache, Log.class);

        if (oldLog == null || (oldLog.getStartTime() != newLog.getStartTime() || oldLog.getEndTime() != newLog.getEndTime()))
            displayNotification(mFacebookFriendsPresenter.get(userId), inProgress, newLog);
    }

    /* Called from the overloaded methods to display a notification. */
    private void displayNotification(final Friend friend, String type, final boolean inProgress, final Log log) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Constants.KEY_EXTRA_NOTIFICATION_SERVICE, type);

        /*
         * If the user clicks on a notification for a log update from a friend, or a friend accepting the request,
         * display the friend's timeline. The Firebase Id of the friend is passed to the main activity.
         */
        if (type.equals(Constants.NOTIFICATION_TYPE_LOG) || (type.equals(Constants.NOTIFICATION_TYPE_FOLLOWING)))
            intent.putExtra(Constants.KEY_EXTRA_FRAGMENT_TIMELINE_FRIEND_ID, friend.getFirebaseId());


        PendingIntent pendingIntent = PendingIntent.getActivity(NotificationService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        final android.support.v4.app.NotificationCompat.Builder builder = new android.support.v4.app.NotificationCompat
                .Builder(NotificationService.this)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_SOCIAL);

        if (mFacebookFriendsPresenter.contains(friend))
            builder.setContentTitle(mFacebookFriendsPresenter.get(friend).getName());
        else
            builder.setContentTitle(getString(R.string.app_name));


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
                DatabaseHelper.loadMove(friend, log.getMoveId(), inProgress, new DatabaseHelper.LoadMoveListener() {
                    @Override
                    public void onMoveLoaded(String text) {
                        if (inProgress) {
                            text = getString(R.string.notification_text_log_present) + text.toLowerCase() + ".";
                        } else {
                            text = getString(R.string.notification_text_log_past) + text.toLowerCase() + " "
                                    + String.format(getString(R.string.log_sub_title_text_past), TextHelper.getDurationText(log.getStartTime(), log.getEndTime(), getResources())).trim()
                                    + ".";
                        }
                        displayLogNotification(builder, text, friend);
                    }
                });
                break;
        }
    }

    private void displayLogNotification(android.support.v4.app.NotificationCompat.Builder builder, String text, Friend friend) {
        builder.setContentText(text);
        mNotificationManager.notify(UniqueId.getLogId(friend), builder.build());
    }

    /* Displays a notification for new requests or accepted requests. */
    private void displayNotification(Friend friend, String type) {
        displayNotification(friend, type, false, null);
    }

    /* Displays a notification for a new or updated log. */
    private void displayNotification(Friend friend, boolean inProgress, Log log) {
        displayNotification(friend, Constants.NOTIFICATION_TYPE_LOG, inProgress, log);
    }

    @Override
    public void onStartRequestsLoading() {

    }

    @Override
    public void onRequestsInitialization() {

    }

    @Override
    public void onCompleteRequestsLoading() {

    }

    @Override
    public void onStartFollowingLoading() {

    }

    @Override
    public void onFollowingInitialization() {

    }

    @Override
    public void onFollowingChanged(int index, Friend friend) {

    }

    /*
     * A notification when a request is accepted is a low-priority notification. Once it has been seen,
     * update the seen notifications on the database to block any further notifications for this event.
     */
    @Override
    public void onCompleteFollowingLoading() {
        List<Friend> following = mFollowingPresenter.getAll();
        for (int i = 0; i < following.size(); i++) {
            Friend friend = following.get(i);
            mLatestLogPresenter.addFriend(friend.getFirebaseId()).attach(this, friend.getFirebaseId()).sync(friend.getFirebaseId());
        }
        DatabaseHelper.pushSeenFollowing(mFollowingPresenter.getAll());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
