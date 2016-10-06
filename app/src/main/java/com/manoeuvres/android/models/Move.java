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

    public String getPresent() {
        return mPresent;
    }

    public String getPast() {
        return mPast;
    }
}
