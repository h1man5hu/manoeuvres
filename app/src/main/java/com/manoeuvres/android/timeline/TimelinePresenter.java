package com.manoeuvres.android.timeline;


public interface TimelinePresenter {
    TimelinePresenter attach(Object component, String userId);

    TimelinePresenter detach(Object component, String userId);

    TimelinePresenter addFriend(String userId);

    TimelinePresenter removeFriend(String userId);

    TimelinePresenter sync(String userId);

    TimelinePresenter stopSync(String userId);

    TimelinePresenter sync();

    TimelinePresenter stopSync();

    Object get(String userId, String key);

    Object getAll(String userId);

    int size(String userId);

    Boolean isLoaded(String userId);
}
