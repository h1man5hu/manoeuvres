package com.manoeuvres.android.notifications;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.timeline.logs.LogsDatabaseHelper;
import com.manoeuvres.android.util.Constants;

class LatestLogDatabaseHelper {

    static void stopLatestMove() {
        String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        final DatabaseReference userLogsReference = LogsDatabaseHelper.getDataReference(userId);
        userLogsReference.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChildren()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        userLogsReference.child(snapshot.getKey())
                                .child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS_ENDTIME)
                                .setValue(System.currentTimeMillis());
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
