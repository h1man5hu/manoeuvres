package com.manoeuvres.android.notifications;

import com.google.firebase.database.DatabaseReference;
import com.manoeuvres.android.friends.requests.RequestsDatabaseHelper;
import com.manoeuvres.android.util.Constants;

class SeenRequestsPresenter extends AbstractSeenPresenter {
    private static SeenRequestsPresenter ourInstance;

    private SeenRequestsPresenter() {
        super(Constants.INITIAL_COLLECTION_CAPACITY_SEEN_REQUESTS);
    }

    public static SeenRequestsPresenter getInstance() {
        if (ourInstance == null) {
            ourInstance = new SeenRequestsPresenter();
        }
        return ourInstance;
    }

    @Override
    protected DatabaseReference getReference(String userId) {
        return RequestsDatabaseHelper.getSeenReference(userId);
    }

    @Override
    protected void updateSeen() {
        RequestsDatabaseHelper.updateSeen(super.getAll());
    }
}
