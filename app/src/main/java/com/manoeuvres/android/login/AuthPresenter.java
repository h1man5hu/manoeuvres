package com.manoeuvres.android.login;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.R;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.timeline.moves.Move;
import com.manoeuvres.android.database.CompletionListeners.GetBooleanListener;
import com.manoeuvres.android.database.CompletionListeners.GetFriendListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;
import com.manoeuvres.android.util.Constants;

public class AuthPresenter {

    public static String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getUid();
        } else {
            return null;
        }
    }

    static void registerUser(Long facebookId, String name,
                             Resources resources, OnCompleteListener listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (listener != null) {
                listener.onFailed();
            }
            return;
        }

        DatabaseReference rootReference = FirebaseDatabase.getInstance().getReference();

        DatabaseReference profileReference =
                rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS).child(userId);
        createUserProfile(facebookId, name, profileReference);

        DatabaseReference metaReference =
                rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
        initializeMetaData(userId, metaReference);

        DatabaseReference userMovesReference =
                rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES).child(userId);
        createDefaultMoves(userMovesReference, resources);

        if (listener != null) {
            listener.onComplete();
        }
    }

    private static void createUserProfile(Long facebookId, String name,
                                          DatabaseReference profileReference) {
        profileReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID)
                .setValue(facebookId);
        profileReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_NAME).setValue(name);
    }

    private static void initializeMetaData(String userId, DatabaseReference metaReference) {
        metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS).child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT).setValue(0);
        metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING).child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT).setValue(0);
        metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS).child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT).setValue(0);
        metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES).child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT).setValue(6);
        metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS).child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT).setValue(0);
    }

    private static void createDefaultMoves(DatabaseReference userMovesReference,
                                           Resources resources) {
        Move move = new Move();
        move.setName(resources.getString(R.string.move_drive_name));
        move.setPresent(resources.getString(R.string.move_drive_present));
        move.setPast(resources.getString(R.string.move_drive_past));
        userMovesReference.push().setValue(move);
        move.setName(resources.getString(R.string.move_eat_name));
        move.setPresent(resources.getString(R.string.move_eat_present));
        move.setPast(resources.getString(R.string.move_eat_past));
        userMovesReference.push().setValue(move);
        move.setName(resources.getString(R.string.move_relax_name));
        move.setPresent(resources.getString(R.string.move_relax_present));
        move.setPast(resources.getString(R.string.move_relax_past));
        userMovesReference.push().setValue(move);
        move.setName(resources.getString(R.string.move_sleep_name));
        move.setPresent(resources.getString(R.string.move_sleep_present));
        move.setPast(resources.getString(R.string.move_sleep_past));
        userMovesReference.push().setValue(move);
        move.setName(resources.getString(R.string.move_study_name));
        move.setPresent(resources.getString(R.string.move_study_present));
        move.setPast(resources.getString(R.string.move_study_past));
        userMovesReference.push().setValue(move);
        move.setName(resources.getString(R.string.move_work_name));
        move.setPresent(resources.getString(R.string.move_work_present));
        move.setPast(resources.getString(R.string.move_work_past));
        userMovesReference.push().setValue(move);
    }

    public static void getUserProfile(@NonNull String userId,
                                      @NonNull final GetFriendListener listener) {
        getUserProfileReference(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    Friend friend = dataSnapshot.getValue(Friend.class);
                    listener.onComplete(friend);
                } else {
                    listener.onFailed();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    static void checkIfUserExists(final String userId, final GetBooleanListener listener) {
        FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_USERS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChild(userId)) {
                            listener.onComplete(true);
                        } else {
                            listener.onComplete(false);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

    public static DatabaseReference getUserProfileReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_USERS)
                .child(userId));
    }
}
