package com.manoeuvres.android.presenters;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.models.Log;
import com.manoeuvres.android.util.Constants;

import java.util.ArrayList;
import java.util.List;

/*
 * A helper class to perform operations on the Firebase database. Note that the methods defined here
 * use ValueEventListener only for a single event. To listen to changes on the database, use one of
 * the presenter classes.
 */
public class DatabaseHelper {

    private static FirebaseUser mUser = FirebaseAuth.getInstance().getCurrentUser();
    private static FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();
    private static DatabaseReference mRootReference = mDatabase.getReference();
    static DatabaseReference mUsersReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);
    static DatabaseReference mMovesReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES);
    static DatabaseReference mLogsReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS);
    private static DatabaseReference mMetaReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
    static DatabaseReference mMetaMovesReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES);
    static DatabaseReference mMetaLogsReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS);
    private static DatabaseReference mFollowersReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWERS);
    static DatabaseReference mUserFollowersReference = mFollowersReference.child(mUser.getUid());
    private static DatabaseReference mMetaFollowersReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS);
    static DatabaseReference mUserFollowersCountReference = mMetaFollowersReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT);
    private static DatabaseReference mFollowingReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING);
    static DatabaseReference mUserFollowingReference = mFollowingReference.child(mUser.getUid());
    private static DatabaseReference mMetaFollowingReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING);
    static DatabaseReference mUserFollowingCountReference = mMetaFollowingReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
    private static DatabaseReference mRequestsReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS);
    static DatabaseReference mUserRequestsReference = mRequestsReference.child(mUser.getUid());
    private static DatabaseReference mMetaRequestsReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS);
    static DatabaseReference mUserRequestsCountReference = mMetaRequestsReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
    private static DatabaseReference mUserLogsReference = mLogsReference.child(mUser.getUid());
    private static DatabaseReference mSeenReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN);
    private static DatabaseReference mSeenRequestsReference = mSeenReference.child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_REQUESTS);
    static DatabaseReference mUserSeenRequestsReference = mSeenRequestsReference.child(mUser.getUid());
    private static DatabaseReference mSeenFollowingReference = mSeenReference.child(Constants.FIREBASE_DATABASE_REFERENCE_SEEN_FOLLOWING);
    static DatabaseReference mUserSeenFollowingReference = mSeenFollowingReference.child(mUser.getUid());


    /*
     * 1. Decrement by 1 the count of the number of requests the user has.
     * 2. Remove the Firebase Id of the friend from the list of requests the user has.
     * 3. Increment by 1 the count of the number of followers the user has.
     * 4. Add the Firebase Id of the friend to the list of followers the user has.
     * 5. Increment by 1 the count of the number of friends the friend is following.
     * 6. Add the Firebase Id of the user to the list of friends the friend is following.
     */
    public static void acceptRequest(final Friend friend) {
        mUserRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    mUserRequestsCountReference.setValue(count - 1);

                    final DatabaseReference userRequestsReference = mRequestsReference.child(mUser.getUid());
                    userRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue() != null) {
                                    if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                        userRequestsReference.child(snapshot.getKey()).setValue(null);
                                    }
                                } else {
                                    break;
                                }
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

        mUserFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                mUserFollowersCountReference.setValue(count + 1);

                mUserFollowersReference.orderByKey().limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String key = "0";
                        if (dataSnapshot.getValue() != null) {
                            key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                        }
                        mUserFollowersReference.child(key).setValue(friend.getFirebaseId());
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

        final DatabaseReference friendFollowingCountReference = mMetaFollowingReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
        friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                friendFollowingCountReference.setValue(count + 1);

                final DatabaseReference friendFollowingReference = mFollowingReference.child(friend.getFirebaseId());
                friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getKey() != null) {
                            friendFollowingReference.child(String.valueOf(dataSnapshot.getChildrenCount())).setValue(mUser.getUid());
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

    /*
     * 1. Decrement by 1 the count of the number of followers of the user.
     * 2. Remove the Firebase Id of the friend from the list of followers of the user.
     * 3. Decrement by 1 the count of the number of friends the friend is following.
     * 4. Remove the Firebase Id of the user from the list of the friends the friend is following.
     */
    public static void removeFollower(final Friend friend) {
        mUserFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    mUserFollowersCountReference.setValue(count - 1);

                    mUserFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue() != null) {
                                    if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                        mUserFollowersReference.child(snapshot.getKey()).setValue(null);
                                    }
                                } else {
                                    break;
                                }
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

        final DatabaseReference friendFollowingCountReference = mMetaFollowingReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
        friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    friendFollowingCountReference.setValue(count - 1);

                    final DatabaseReference friendFollowingReference = mFollowingReference.child(friend.getFirebaseId());
                    friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue() != null) {
                                    if (snapshot.getValue().equals(mUser.getUid())) {
                                        friendFollowingReference.child(snapshot.getKey()).setValue(null);
                                    }
                                } else {
                                    break;
                                }
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
    }

    /*
     * 1. Decrement by 1 the count of the number of friends the user is following.
     * 2. Remove the Firebase Id of the friend from the list of friends the user is following.
     * 3. Decrement by 1 the count of the number of followers of the friend.
     * 4. Remove the Firebase Id of the user from the list of followers of the friend.
     */
    public static void unfollowFriend(final Friend friend) {
        mUserFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    mUserFollowingCountReference.setValue(count - 1);
                }

                mUserFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                mUserFollowingReference.child(snapshot.getKey()).setValue(null);
                            }
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

        final DatabaseReference friendFollowersCountReference = mMetaFollowersReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT);
        friendFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    friendFollowersCountReference.setValue(count - 1);

                    final DatabaseReference friendFollowersReference = mFollowersReference.child(friend.getFirebaseId());
                    friendFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue().equals(mUser.getUid())) {
                                    friendFollowersReference.child(snapshot.getKey()).setValue(null);
                                }
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
    }

    /*
     * 1. Increment by 1 the count of the number of requests the friend has.
     * 2. Add the Firebase Id of the user to the list of the requests of the friend.
     */
    public static void followFriend(final Friend friend, final FollowFriendListener listener) {
        final DatabaseReference friendRequestsCountReference = mMetaRequestsReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
        friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                friendRequestsCountReference.setValue(count + 1);

                final DatabaseReference friendRequestsReference = mRequestsReference.child(friend.getFirebaseId());
                friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String key = "0";
                        if (dataSnapshot.getValue() != null) {
                            key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                        }
                        friendRequestsReference.child(key).setValue(mUser.getUid());
                        if (listener != null)
                            listener.onRequested();
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

    /*
     * 1. Decrement by 1 the count of the number of requests the friend has.
     * 2. Remove the Firebase Id of the user from the list of the requests of the friend.
     */
    public static void cancelRequest(final Friend friend, final CancelRequestListener listener) {
        final DatabaseReference friendRequestsCountReference = mMetaRequestsReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
        friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int count = Integer.valueOf(dataSnapshot.getValue().toString());
                    friendRequestsCountReference.setValue(count - 1);

                    final DatabaseReference friendRequestsReference = mRequestsReference.child(friend.getFirebaseId());
                    friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getValue() != null) {
                                    if (snapshot.getValue().equals(mUser.getUid())) {
                                        friendRequestsReference.child(snapshot.getKey()).setValue(null);
                                        if (listener != null)
                                            listener.onCancelled();
                                    }
                                } else {
                                    break;
                                }

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
    }

    public static void isRequested(final Friend friend, final RequestListener listener) {
        mRequestsReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if (snapshot.getValue() != null) {
                        if (snapshot.getValue().equals(mUser.getUid())) listener.onComplete(true);
                        else listener.onComplete(false);
                    } else break;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /* To avoid notifications for already seen requests. */
    public static void pushSeenRequests(List<Friend> requests) {
        List<String> requestIds = new ArrayList<>();
        for (Friend request : requests) {
            requestIds.add(request.getFirebaseId());
        }
        mUserSeenRequestsReference.setValue(requestIds);
    }

    /* To avoid notifications for already seen accepted requests. */
    public static void pushSeenFollowing(List<Friend> following) {
        List<String> followingIds = new ArrayList<>();
        for (Friend friend : following) {
            followingIds.add(friend.getFirebaseId());
        }
        mUserSeenFollowingReference.setValue(followingIds);
    }

    /* Used by the NotificationService to get the details of the move when the latest log is changed. */
    public static void loadMove(Friend friend, String moveId, boolean inProgress, final LoadMoveListener listener) {
        DatabaseReference reference = mMovesReference.child(friend.getFirebaseId()).child(moveId);
        if (inProgress) {
            reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PRESENT).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (listener != null)
                        listener.onMoveLoaded(dataSnapshot.getValue().toString());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        } else {
            reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PAST).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (listener != null)
                        listener.onMoveLoaded(dataSnapshot.getValue().toString());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    public static void pushMove(final Log log) {
        final DatabaseReference userLogsCountReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META).child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS)
                .child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT);
        userLogsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                userLogsCountReference.setValue(count + 1);
                mUserLogsReference.push().setValue(log);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public static void stopLatestMove() {
        mUserLogsReference.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if (snapshot.getKey() != null) {
                        mUserLogsReference.child(snapshot.getKey()).child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS_ENDTIME).setValue(System.currentTimeMillis());
                    } else {
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }


    /*
     * The follow button in the FindFriends fragment changes its text upon completion to allow the
     * user to cancel the request.
     */
    public interface FollowFriendListener {
        void onRequested();
    }

    /*
     * The cancel request button in the FindFriends fragment changes its text upon completion to allow
     * the user to follow the friend.
     */
    public interface CancelRequestListener {
        void onCancelled();
    }

    /*
     * When the FindFriends fragment is initialized, it needs to check if a friend has already been
     * requested to update the button text.
     */
    public interface RequestListener {
        void onComplete(boolean isRequested);
    }

    public interface LoadMoveListener {
        void onMoveLoaded(String text);
    }
}