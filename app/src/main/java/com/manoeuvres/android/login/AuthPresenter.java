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
import com.manoeuvres.android.util.Constants;


public class AuthPresenter {

    public static String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) return user.getUid();
        else return null;
    }

    static void registerUser(Long facebookId, String name, Resources resources, RegisterUserListener listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (listener != null) listener.onFailed();
            return;
        }

        DatabaseReference rootReference = FirebaseDatabase.getInstance().getReference();
        DatabaseReference metaReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);

        DatabaseReference profileReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS).child(userId);
        profileReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).setValue(facebookId);
        profileReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_NAME).setValue(name);

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

        DatabaseReference userMovesReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES).child(userId);
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

        if (listener != null) listener.onRegistered();
    }

    public static void getUserProfile(@NonNull String userId, @NonNull final UserProfileListener listener) {
        getUserProfileReference(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    Friend friend = dataSnapshot.getValue(Friend.class);
                    listener.onProfileLoaded(friend);
                } else listener.onFailed();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    public static DatabaseReference getUserProfileReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_USERS)
                .child(userId));
    }

    interface RegisterUserListener {
        void onRegistered();

        void onFailed();
    }

    public interface UserProfileListener {
        void onProfileLoaded(Friend friend);

        void onFailed();
    }

}
