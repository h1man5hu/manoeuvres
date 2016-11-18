package com.manoeuvres.android.notifications;

import com.google.firebase.database.DatabaseReference;
import com.manoeuvres.android.friends.following.FollowingDatabaseHelper;
import com.manoeuvres.android.util.Constants;

class SeenFollowingPresenter extends AbstractSeenPresenter {
    private static SeenFollowingPresenter ourInstance;

    private SeenFollowingPresenter() {
        super(Constants.INITIAL_COLLECTION_CAPACITY_SEEN_FOLLOWING);
    }

    public static SeenFollowingPresenter getInstance() {
        if (ourInstance == null) {
            ourInstance = new SeenFollowingPresenter();
        }
        return ourInstance;
    }

    @Override
    protected DatabaseReference getReference(String userId) {
        return FollowingDatabaseHelper.getSeenReference(userId);
    }

    @Override
    protected void updateSeen() {
        FollowingDatabaseHelper.updateSeen(super.getAll());
    }
}
