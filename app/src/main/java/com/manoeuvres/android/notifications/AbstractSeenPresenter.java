package com.manoeuvres.android.notifications;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.login.AuthPresenter;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractSeenPresenter implements SeenPresenter {

    private ValueEventListener mListener;
    private List<Friend> mFriends;

    AbstractSeenPresenter(int initialCapacity) {
        mFriends = new ArrayList<>(initialCapacity);
    }

    @Override
    public void sync() {
        if (mListener == null) {

            String userId = AuthPresenter.getCurrentUserId();
            if (userId == null) {
                return;
            }

            mListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mFriends.clear();
                    if (dataSnapshot.hasChildren()) {
                        Friend friend = new Friend();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            friend.setFirebaseId(snapshot.getValue().toString());
                            mFriends.add(friend);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            getReference(userId).addValueEventListener(mListener);
        }
    }

    @Override
    public void stopSync() {
        if (mListener != null) {

            String userId = AuthPresenter.getCurrentUserId();
            if (userId == null) {
                return;
            }

            getReference(userId).removeEventListener(mListener);
            mListener = null;
        }
    }

    @Override
    public boolean contains(Friend friend) {
        return mFriends.contains(friend);
    }

    protected List<Friend> getAll() {
        return mFriends;
    }

    protected abstract DatabaseReference getReference(String userId);

    protected abstract void updateSeen();
}
