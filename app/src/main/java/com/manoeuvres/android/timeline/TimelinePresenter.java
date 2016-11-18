package com.manoeuvres.android.timeline;

interface TimelinePresenter {
    void attach(Object component, String userId);

    TimelinePresenter detach(Object component, String userId);

    void addFriend(String userId);

    void removeFriend(String userId);

    void sync(String userId);

    void stopSync(String userId);

    void sync();

    Object get(String userId, String key);

    Object getAll(String userId);

    int size(String userId);

    Boolean isLoaded(String userId);
}
