package com.manoeuvres.android.timeline.moves;

import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.database.CompletionListeners.GetStringListener;
import com.manoeuvres.android.util.Constants;

class MovesDatabaseHelper {

    static void loadMove(Friend friend, String moveId,
                         boolean inProgress, final GetStringListener listener) {
        DatabaseReference reference = getMovesDataReference(friend.getFirebaseId()).child(moveId);
        if (inProgress) {
            reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PRESENT)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (listener != null) {
                                Object value = dataSnapshot.getValue();
                                if (value != null) {
                                    listener.onComplete(value.toString());
                                } else {
                                    listener.onFailed();
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            if (listener != null) {
                                listener.onFailed();
                            }
                        }
                    });
        } else {
            reference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES_PAST)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (listener != null) {
                        Object value = dataSnapshot.getValue();
                        if (value != null) {
                            listener.onComplete(value.toString());
                        } else {
                            listener.onFailed();
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    if (listener != null) {
                        listener.onFailed();
                    }
                }
            });
        }
    }

    static DatabaseReference getMovesCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT));
    }

    static DatabaseReference getMovesDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_MOVES)
                .child(userId));
    }
}
