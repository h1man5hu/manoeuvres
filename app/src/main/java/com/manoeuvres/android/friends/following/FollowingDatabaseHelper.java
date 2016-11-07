package com.manoeuvres.android.friends.following;


import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.followers.FollowersDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class FollowingDatabaseHelper {

    /*
     * 1. Decrement by 1 the count of the number of friends the user is following.
     * 2. Remove the Firebase Id of the friend from the list of friends the user is following.
     * 3. Decrement by 1 the count of the number of followers of the friend.
     * 4. Remove the Firebase Id of the user from the list of followers of the friend.
     */
    static void unfollowFriend(@NonNull final Friend friend) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference userFollowingCountReference = getFollowingCountReference(userId);
        userFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    if (count > 0) userFollowingCountReference.setValue(count - 1);
                    else userFollowingCountReference.setValue(0);
                }

                final DatabaseReference userFollowingReference = getFollowingDataReference(userId);
                userFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChildren())
                            for (DataSnapshot snapshot : dataSnapshot.getChildren())
                                if (snapshot.getValue().equals(friend.getFirebaseId()))
                                    userFollowingReference.child(snapshot.getKey()).setValue(null);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        final DatabaseReference friendFollowersCountReference = FollowersDatabaseHelper.getFollowersCountReference(friend.getFirebaseId());
        friendFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    if (count > 0) friendFollowersCountReference.setValue(count - 1);
                    else friendFollowersCountReference.setValue(0);

                    final DatabaseReference friendFollowersReference = FollowersDatabaseHelper.getFollowersDataReference(friend.getFirebaseId());
                    friendFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChildren())
                                for (DataSnapshot snapshot : dataSnapshot.getChildren())
                                    if (snapshot.getValue().equals(userId))
                                        friendFollowersReference.child(snapshot.getKey()).setValue(null);
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

    /* To avoid notifications for already seen accepted requests. */
    public static void pushSeenFollowing(List<Friend> following) {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        List<String> followingIds = new ArrayList<>();
        for (int i = 0; i < following.size(); i++) {
            Friend friend = following.get(i);
            followingIds.add(friend.getFirebaseId());
        }

        getSeenFollowingReference(userId).setValue(followingIds);
    }

    public static DatabaseReference getFollowingCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT));
    }

    public static DatabaseReference getFollowingDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING)
                .child(userId));
    }

    public static DatabaseReference getSeenFollowingReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_SEEN)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_FOLLOWING)
                .child(userId));
    }

}
