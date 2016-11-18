package com.manoeuvres.android.friends.followers;

import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.friends.following.FollowingDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.util.Constants;

public class FollowersDatabaseHelper {

    /*
     * 1. Decrement by 1 the count of the number of followers of the user.
     * 2. Remove the Firebase Id of the friend from the list of followers of the user.
     * 3. Decrement by 1 the count of the number of friends the friend is following.
     * 4. Remove the Firebase Id of the user from the list of the friends the friend is following.
     */
    static void remove(@NonNull final String friendId) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        removeFollower(userId, friendId);
        FollowingDatabaseHelper.removeFollowing(friendId, userId);
    }

    public static void addFollower(final String userId, final String followerId) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(countReference, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.incrementCount(count, countReference);
                DatabaseHelper.addListItem(followerId, dataReference, null);
            }

            @Override
            public void onFailed() {

            }
        });
    }

    public static void removeFollower(final String userId, final String followerId) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(countReference, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.decrementCount(count, countReference);
                DatabaseHelper.removeListItem(followerId, dataReference, null);
            }

            @Override
            public void onFailed() {

            }
        });
    }

    static DatabaseReference getCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT));
    }

    static DatabaseReference getDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWERS)
                .child(userId));
    }
}
