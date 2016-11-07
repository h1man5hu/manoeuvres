package com.manoeuvres.android.timeline.logs;


import android.support.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.util.Constants;

public class LogsDatabaseHelper {

    public static void pushLog(final String moveId) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference userLogsCountReference = getLogsCountReference(userId);
        userLogsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                if (dataSnapshot.getValue() != null) {
                    count = Integer.valueOf(dataSnapshot.getValue().toString());
                }
                if (count < 0) userLogsCountReference.setValue(0);
                else userLogsCountReference.setValue(count + 1);
                getLogsDataReference(userId).push().setValue(new Log(moveId));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    static DatabaseReference getLogsCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT));
    }

    public static DatabaseReference getLogsDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance().getReference(Constants.FIREBASE_DATABASE_REFERENCE_LOGS)
                .child(userId));
    }
}
