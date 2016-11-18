package com.manoeuvres.android.timeline.logs;

//Represents an actual instance of move.
public class Log {

    private String mMoveId;
    private long mStartTime;
    private long mEndTime;

    //Empty constructor needed for FirebaseDatabase.
    public Log() {

    }

    public Log(String moveId) {
        mMoveId = moveId;
        mStartTime = System.currentTimeMillis();
        mEndTime = 0;
    }

    public Log(Log log) {
        mMoveId = log.getMoveId();
        mStartTime = log.getStartTime();
        mEndTime = log.getEndTime();
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

    public long getEndTime() {
        return mEndTime;
    }

    public void setEndTime(long endTime) {
        mEndTime = endTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Log)) {
            return false;
        }
        Log log = (Log) obj;
        return mMoveId.equals(log.getMoveId()) && mStartTime == log.getStartTime();
    }

    @Override
    public int hashCode() {
        return (mMoveId + String.valueOf(mStartTime)).hashCode();
    }
}
