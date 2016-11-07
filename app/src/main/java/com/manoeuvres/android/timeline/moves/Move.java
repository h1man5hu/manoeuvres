package com.manoeuvres.android.timeline.moves;

//Represents a move which can be broadcasted by the user.
public class Move {

    private String mName;
    private String mPresent;
    private String mPast;

    //Empty constructor needed for FirebaseDatabase.
    public Move() {

    }

    Move(Move move) {
        mName = move.getName();
        mPresent = move.getPresent();
        mPast = move.getPast();
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getPresent() {
        return mPresent;
    }

    public void setPresent(String present) {
        mPresent = present;
    }

    public String getPast() {
        return mPast;
    }

    public void setPast(String past) {
        mPast = past;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Move)) {
            return false;
        }
        Move move = (Move) obj;
        return mName.equals(move.getName());
    }

    @Override
    public int hashCode() {
        return mName.hashCode();
    }

    @Override
    public String toString() {
        return mName;
    }
}
