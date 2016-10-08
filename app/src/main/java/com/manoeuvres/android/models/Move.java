package com.manoeuvres.android.models;

//Represents a move which can be broadcasted by the user.
public class Move {

    private String mName;
    private String mPresent;
    private String mPast;

    //Empty constructor needed for FirebaseDatabase.
    public Move() {

    }

    public Move(String name, String present, String past) {
        mName = name;
        mPresent = present;
        mPast = past;
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
}
