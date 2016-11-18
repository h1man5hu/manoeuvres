package com.manoeuvres.android.notifications;


import com.manoeuvres.android.friends.Friend;

interface SeenPresenter {
    void sync();

    void stopSync();

    boolean contains(Friend friend);
}
