package com.manoeuvres.android.friends.requests;


import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.followers.FollowersDatabaseHelper;
import com.manoeuvres.android.friends.following.FollowingDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class RequestsDatabaseHelper {

    /* To avoid notifications for already seen requests. */
    static void pushSeenRequests(List<Friend> requests) {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Friend request = requests.get(i);
            requestIds.add(request.getFirebaseId());
        }

        getSeenRequestsReference(userId).setValue(requestIds);
    }

    /*
     * 1. Decrement by 1 the count of the number of requests the user has.
     * 2. Remove the Firebase Id of the friend from the list of requests the user has.
     * 3. Increment by 1 the count of the number of followers the user has.
     * 4. Add the Firebase Id of the friend to the list of followers the user has.
     * 5. Increment by 1 the count of the number of friends the friend is following.
     * 6. Add the Firebase Id of the user to the list of friends the friend is following.
     */
    static void acceptRequest(final Friend friend) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference userRequestsCountReference = getRequestsCountReference(userId);

        userRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    int count = Integer.valueOf(value.toString());
                    if (count > 0) userRequestsCountReference.setValue(count - 1);
                    else userRequestsCountReference.setValue(0);

                    final DatabaseReference userRequestsReference = getRequestsDataReference(userId);
                    userRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChildren())
                                for (DataSnapshot snapshot : dataSnapshot.getChildren())
                                    if (snapshot.getValue().equals(friend.getFirebaseId()))
                                        userRequestsReference.child(snapshot.getKey()).setValue(null);
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

        final DatabaseReference userFollowersCountReference = FollowersDatabaseHelper.getFollowersCountReference(userId);
        userFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                userFollowersCountReference.setValue(count + 1);

                final DatabaseReference userFollowersReference = FollowersDatabaseHelper.getFollowersDataReference(userId);
                userFollowersReference.orderByKey().limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String key = "0";
                        if (dataSnapshot.getValue() != null) {
                            key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                        }
                        userFollowersReference.child(key).setValue(friend.getFirebaseId());
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

        final DatabaseReference friendFollowingCountReference = FollowingDatabaseHelper.getFollowingCountReference(friend.getFirebaseId());
        friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                friendFollowingCountReference.setValue(count + 1);

                final DatabaseReference friendFollowingReference = FollowingDatabaseHelper.getFollowingDataReference(friend.getFirebaseId());
                friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getKey() != null) {
                            friendFollowingReference.child(String.valueOf(dataSnapshot.getChildrenCount())).setValue(userId);
                        }
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
    }

    public static DatabaseReference getSeenRequestsReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_SEEN)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_REQUESTS)
                .child(userId));
    }

    public static DatabaseReference getRequestsCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT));
    }

    public static DatabaseReference getRequestsDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS)
                .child(userId));
    }

}
