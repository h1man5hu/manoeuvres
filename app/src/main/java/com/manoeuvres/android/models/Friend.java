package com.manoeuvres.android.models;


public class Friend {

    private long mFacebookId;
    private String mName;
    private String mFirebaseId;

    public Friend() {
        //Empty constructor required for Firebase database.
    }

    public Friend(String firebaseId) {
        mFirebaseId = firebaseId;
    }

    public Friend(long facebookId, String name) {
        mFacebookId = facebookId;
        mName = name;
    }

    public Friend(Friend friend) {
        mFacebookId = friend.getFacebookId();
        mName = friend.getName();
    }

    public long getFacebookId() {
        return mFacebookId;
    }

    public void setFacebookId(long facebookId) {
        mFacebookId = facebookId;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getFirebaseId() {
        return mFirebaseId;
    }

    public void setFirebaseId(String firebaseId) {
        mFirebaseId = firebaseId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Friend)) {
            return false;
        }
        Friend friend = (Friend) obj;
        return mFirebaseId.equals(friend.getFirebaseId());
    }

    @Override
    public int hashCode() {
        return mFirebaseId.hashCode();
    }
}
