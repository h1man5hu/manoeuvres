package com.manoeuvres.android.friends;


import java.util.List;

public interface FriendsPresenter {
    FriendsPresenter attach(Object component);

    FriendsPresenter detach(Object component);

    FriendsPresenter sync();

    FriendsPresenter stopSync();

    Friend get(int index);

    List<Friend> getAll();

    int size();

    boolean isLoaded();
}
