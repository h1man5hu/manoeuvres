package com.manoeuvres.android.database;

import android.support.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.database.CompletionListeners.GetIntListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;

public class DatabaseHelper {

    public static void getCount(DatabaseReference countReference, final GetIntListener listener) {
        countReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    count = Integer.valueOf(value.toString());
                }
                listener.onComplete(count);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    public static void incrementCount(int count, DatabaseReference countReference) {
        if (count > 0) {
            countReference.setValue(count + 1);
        } else {
            countReference.setValue(1);
        }
    }

    public static void decrementCount(int count, DatabaseReference countReference) {
        if (count > 0) {
            countReference.setValue(count - 1);
        } else {
            countReference.setValue(0);
        }
    }

    public static void addListItem(final String value,
                                   final DatabaseReference dataReference,
                                   @Nullable final OnCompleteListener listener) {
        dataReference.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String key = "0";
                if (dataSnapshot.getValue() != null) {
                    key = String.valueOf(Integer.valueOf(
                            dataSnapshot.getChildren().iterator().next().getKey()) + 1
                    );
                }
                dataReference.child(key).setValue(value);
                if (listener != null) {
                    listener.onComplete();
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

    public static void removeListItem(final String value,
                                      final DatabaseReference dataReference,
                                      @Nullable final OnCompleteListener listener) {
        dataReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChildren()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        if (snapshot.getValue().equals(value)) {
                            dataReference.child(snapshot.getKey()).setValue(null);
                            if (listener != null) {
                                listener.onComplete();
                            }
                        }
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
