package com.manoeuvres.android.friends.requests;

import com.google.firebase.database.DatabaseReference;
import com.manoeuvres.android.friends.AbstractFriendsPresenter;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.Constants;

import java.util.List;

public class RequestsPresenter extends AbstractFriendsPresenter {

    private static RequestsPresenter ourInstance;

    private RequestsPresenter() {
        super(
                Constants.INITIAL_COLLECTION_CAPACITY_REQUESTS,
                Constants.MAX_REQUESTS_LISTENERS_COUNT,
                null
        );
    }

    public static RequestsPresenter getInstance() {
        if (ourInstance == null) {
            ourInstance = new RequestsPresenter();
        }
        return ourInstance;
    }

    void acceptRequest(Friend friend) {
        RequestsDatabaseHelper.accept(friend.getFirebaseId());
    }

    void pushSeenRequests(List<Friend> requests) {
        RequestsDatabaseHelper.updateSeen(requests);
    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return RequestsDatabaseHelper.getDataReference(userId);
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return RequestsDatabaseHelper.getCountReference(userId);
    }

    @Override
    protected boolean isCachingEnabled() {
        return Constants.CACHE_REQUESTS;
    }

    @Override
    protected String getCacheKeyString() {
        return Constants.KEY_SHARED_PREF_DATA_REQUESTS;
    }

    @Override
    protected int getInitialListCapacity() {
        return Constants.INITIAL_COLLECTION_CAPACITY_REQUESTS;
    }

    @Override
    protected void notifyStartLoading(Object listener) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onStartRequestsLoading();
    }

    @Override
    protected void notifyInitialLoading(Object listener) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onRequestsInitialization();
    }

    @Override
    protected void notifyAddData(Object listener, int index, Friend friend) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onRequestAdded(index, friend);
    }

    @Override
    protected void notifyChangeData(Object listener, int index, Friend friend) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onRequestChanged(index, friend);
    }

    @Override
    protected void notifyRemoveData(Object listener, int index, Friend friend) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onRequestRemoved(index, friend);
    }

    @Override
    protected void notifyCompleteLoading(Object listener) {
        RequestsListener observer = (RequestsListener) listener;
        observer.onCompleteRequestsLoading();
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    public interface RequestsListener {
        void onStartRequestsLoading();

        void onRequestsInitialization();

        void onRequestAdded(int index, Friend friend);

        void onRequestChanged(int index, Friend friend);

        void onRequestRemoved(int index, Friend friend);

        void onCompleteRequestsLoading();
    }
}
