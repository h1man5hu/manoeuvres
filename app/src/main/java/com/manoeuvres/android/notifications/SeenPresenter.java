package com.manoeuvres.android.notifications;


import com.manoeuvres.android.friends.Friend;

interface SeenPresenter {
    SeenPresenter attach(Object component);

    SeenPresenter detach(Object component);

    SeenPresenter sync();

    SeenPresenter stopSync();

    boolean contains(Friend friend);
}
