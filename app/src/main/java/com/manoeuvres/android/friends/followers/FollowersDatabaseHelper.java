package com.manoeuvres.android.friends.followers;


import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.following.FollowingDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.util.Constants;

public class FollowersDatabaseHelper {

    /*
     * 1. Decrement by 1 the count of the number of followers of the user.
     * 2. Remove the Firebase Id of the friend from the list of followers of the user.
     * 3. Decrement by 1 the count of the number of friends the friend is following.
     * 4. Remove the Firebase Id of the user from the list of the friends the friend is following.
     */
    static void removeFollower(@NonNull final Friend friend) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference userFollowersCountReference = getFollowersCountReference(userId);
        userFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    int count = Integer.valueOf(value.toString());
                    if (count > 0) userFollowersCountReference.setValue(count - 1);
                    else userFollowersCountReference.setValue(0);

                    final DatabaseReference userFollowersReference = getFollowersDataReference(userId);
                    userFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChildren())
                                for (DataSnapshot snapshot : dataSnapshot.getChildren())
                                    if (snapshot.getValue().toString().equals(friend.getFirebaseId())) {
                                        userFollowersReference.child(snapshot.getKey()).setValue(null);
                                    }
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

        final DatabaseReference friendFollowingCountReference = FollowingDatabaseHelper.getFollowingCountReference(friend.getFirebaseId());
        friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    int count = Integer.valueOf(value.toString());
                    if (count > 0) friendFollowingCountReference.setValue(count - 1);
                    else friendFollowingCountReference.setValue(0);

                    final DatabaseReference friendFollowingReference = FollowingDatabaseHelper.getFollowingDataReference(friend.getFirebaseId());
                    friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChildren())
                                for (DataSnapshot snapshot : dataSnapshot.getChildren())
                                    if (snapshot.getValue().equals(userId))
                                        friendFollowingReference.child(snapshot.getKey()).setValue(null);
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

    public static DatabaseReference getFollowersCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT));
    }

    public static DatabaseReference getFollowersDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWERS)
                .child(userId));
    }
}
