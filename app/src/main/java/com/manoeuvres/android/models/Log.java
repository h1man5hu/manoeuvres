package com.manoeuvres.android.models;

//Represents an actual instance of move.
public class Log {
    private String mMoveId;
    private long mStartTime;

    //Empty constructor needed for FirebaseDatabase.
    public Log() {

    }

    public Log(String moveId) {
        mMoveId = moveId;
        mStartTime = System.currentTimeMillis();
    }

    public String getMoveId() {
        return mMoveId;
    }

    public void setMoveId(String moveId) {
        mMoveId = moveId;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public void setStartTime(long startTime) {
        mStartTime = startTime;
    }
}
