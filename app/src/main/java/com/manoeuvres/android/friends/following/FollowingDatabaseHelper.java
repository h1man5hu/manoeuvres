package com.manoeuvres.android.friends.following;

import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.FriendsDatabaseHelper;
import com.manoeuvres.android.friends.followers.FollowersDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.util.Constants;

import java.util.List;

public class FollowingDatabaseHelper {

    /*
     * 1. Decrement by 1 the count of the number of friends the user is following.
     * 2. Remove the Firebase Id of the friend from the list of friends the user is following.
     * 3. Decrement by 1 the count of the number of followers of the friend.
     * 4. Remove the Firebase Id of the user from the list of followers of the friend.
     */
    static void remove(@NonNull final String friendId) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        removeFollowing(userId, friendId);
        FollowersDatabaseHelper.removeFollower(friendId, userId);
    }

    public static void addFollowing(final String userId, final String followingId) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(countReference, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.incrementCount(count, countReference);
                DatabaseHelper.addListItem(followingId, dataReference, null);
            }

            @Override
            public void onFailed() {

            }
        });
    }

    public static void removeFollowing(final String userId, final String followingId) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(getCountReference(userId), new GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.decrementCount(count, countReference);
                DatabaseHelper.removeListItem(followingId, dataReference, null);
            }

            @Override
            public void onFailed() {

            }
        });
    }


    /* To avoid notifications for already seen accepted requests. */
    public static void updateSeen(List<Friend> following) {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        FriendsDatabaseHelper.pushListOfFriends(following, getSeenReference(userId));
    }

    static DatabaseReference getCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT));
    }

    static DatabaseReference getDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING)
                .child(userId));
    }

    public static DatabaseReference getSeenReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_SEEN)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_FOLLOWING)
                .child(userId));
    }
}
