package com.manoeuvres.android.friends;

import java.util.List;

interface FriendsPresenter {
    void attach(Object component);

    FriendsPresenter detach(Object component);

    void sync();

    void stopSync();

    Friend get(int index);

    List<Friend> getAll();

    int size();

    boolean isLoaded();
}
