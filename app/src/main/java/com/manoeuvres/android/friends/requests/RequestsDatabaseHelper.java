package com.manoeuvres.android.friends.requests;

import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.FriendsDatabaseHelper;
import com.manoeuvres.android.friends.followers.FollowersDatabaseHelper;
import com.manoeuvres.android.friends.following.FollowingDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners;
import com.manoeuvres.android.database.CompletionListeners.GetBooleanListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;
import com.manoeuvres.android.util.Constants;

import java.util.List;

public class RequestsDatabaseHelper {

    /*
     * 1. Decrement by 1 the count of the number of requests the user has.
     * 2. Remove the Firebase Id of the friend from the list of requests the user has.
     * 3. Increment by 1 the count of the number of followers the user has.
     * 4. Add the Firebase Id of the friend to the list of followers the user has.
     * 5. Increment by 1 the count of the number of friends the friend is following.
     * 6. Add the Firebase Id of the user to the list of friends the friend is following.
     */
    static void accept(final String friendId) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        removeRequest(userId, friendId, new OnCompleteListener() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onFailed() {

            }
        });
        FollowersDatabaseHelper.addFollower(userId, friendId);
        FollowingDatabaseHelper.addFollowing(friendId, userId);
    }

    /*
     * 1. Increment by 1 the count of the number of requests the friend has.
     * 2. Add the Firebase Id of the user to the list of the requests of the friend.
     */
    public static void sendRequest(String friendId, final OnCompleteListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        addRequest(friendId, userId, listener);
    }

    /*
     * 1. Decrement by 1 the count of the number of requests the friend has.
     * 2. Remove the Firebase Id of the user from the list of the requests of the friend.
     */
    public static void cancelRequest(final String friendId, final OnCompleteListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        removeRequest(friendId, userId, listener);
    }

    private static void addRequest(final String userId,
                                   final String requestId,
                                   final OnCompleteListener listener) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(countReference, new CompletionListeners.GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.incrementCount(count, countReference);
                DatabaseHelper.addListItem(requestId, dataReference, listener);
            }

            @Override
            public void onFailed() {
                listener.onFailed();
            }
        });
    }

    private static void removeRequest(final String userId,
                                      final String requestId,
                                      final OnCompleteListener listener) {
        final DatabaseReference countReference = getCountReference(userId);
        final DatabaseReference dataReference = getDataReference(userId);
        DatabaseHelper.getCount(countReference, new CompletionListeners.GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.decrementCount(count, countReference);
                DatabaseHelper.removeListItem(requestId, dataReference, listener);
            }

            @Override
            public void onFailed() {
                listener.onFailed();
            }
        });
    }

    public static void isFriendRequested(final String friendId, final GetBooleanListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        getDataReference(friendId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChildren()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue().equals(userId)) {
                                    listener.onComplete(true);
                                }
                            }
                        } else {
                            listener.onComplete(false);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        listener.onFailed();
                    }
                });
    }

    /* To avoid notifications for already seen requests. */
    public static void updateSeen(List<Friend> requests) {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        FriendsDatabaseHelper.pushListOfFriends(requests, getSeenReference(userId));
    }

    public static DatabaseReference getSeenReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_SEEN)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_REQUESTS)
                .child(userId));
    }

    static DatabaseReference getCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT));
    }

    static DatabaseReference getDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS)
                .child(userId));
    }
}
