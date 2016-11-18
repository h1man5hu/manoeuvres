package com.manoeuvres.android.timeline.logs;

import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.util.Constants;

public class LogsDatabaseHelper {

    public static void pushLog(@NonNull final String moveId) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) {
            return;
        }

        final DatabaseReference countReference = getCountReference(userId);
        DatabaseHelper.getCount(countReference, new GetIntListener() {
            @Override
            public void onComplete(int count) {
                DatabaseHelper.incrementCount(count, countReference);
                getDataReference(userId).push().setValue(new Log(moveId));
            }

            @Override
            public void onFailed() {

            }
        });
    }

    static DatabaseReference getCountReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_META)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS)
                .child(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT));
    }

    public static DatabaseReference getDataReference(@NonNull String userId) {
        return (FirebaseDatabase.getInstance()
                .getReference(Constants.FIREBASE_DATABASE_REFERENCE_LOGS)
                .child(userId));
    }

}
