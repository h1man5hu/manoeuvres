package com.manoeuvres.android.friends.findfriends;

import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter.FacebookFriendsListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)

public class FacebookFriendsPresenterTest {
    private FacebookFriendsPresenter mPresenter;
    private String mTwoFriendsGSON = "[{\"mFacebookId\":10206318318935662,\"mFirebaseId\":\"I24nwCQv1PMo458HcAm1sipM9ns2\",\"mName\":\"Himanshu Arora\"}" +
            ",{\"mFacebookId\":100013760411655,\"mFirebaseId\":\"Jk4sEMj0JYSpJBFiaUwTV9aJMWq1\",\"mName\":\"David Smith\"}]";
    private String mTwoFriendsJSON = "[{\"id\": \"10206318318935662\",\"name\": \"Himanshu Arora\"}" +
            ",{\"id\": \"100013760411655\",\"name\": \"David Smith\"}]";

    @Before
    public void createSingleton() {
        mPresenter = FacebookFriendsPresenter.getInstance(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void destroySingleton() {
        mPresenter.destroy();
    }

    @Test
    public void attach_detach_shouldAddRemoveComponentInObserversArray() throws Exception {
        FacebookFriendsListener[] listeners = new FacebookFriendsPresenter
                .FacebookFriendsListener[Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT];
        for (int i = 0; i < Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT; i++) {
            FacebookFriendsListener listener = mock(FacebookFriendsListener.class);
            listeners[i] = listener;
            mPresenter.attach(listener);
        }
        for (int i = 0; i < Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT; i++) {
            FacebookFriendsListener listener = listeners[i];
            assertThat(mPresenter.isAttached(listener), is(true));
            mPresenter.detach(listener);
            assertThat(mPresenter.isAttached(listener), is(false));
            assertFalse(mPresenter.isAttached(listener));
        }
    }

    @Test
    public void loadCache_sizeOfFriendsListShouldBeZeroWhenNoCache() {
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "")).thenReturn(null);
        mPresenter.loadCache(preferences);
        assertThat(mPresenter.size(), is(0));
    }

    @Test
    public void loadCache_shouldLoadFriendsFromPreferences() {
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "")).thenReturn(mTwoFriendsGSON);
        mPresenter.loadCache(preferences);
        assertThat(mPresenter.size(), is(2));
    }

    @Test
    public void sync_shouldNotifyObservers() throws Exception {
        if (Constants.MAX_FACEBOOK_FRIENDS_LISTENERS_COUNT > 0) {
            FacebookFriendsListener listener = mock(FacebookFriendsListener.class);
            mPresenter.attach(listener);
            mPresenter.sync();
            mPresenter.detach(listener);
            mPresenter.sync();
            verify(listener, times(1)).onStartFacebookFriendsLoading();
        }
    }

    @Test
    public void sync_should_getIDFromFirebase_addToList_andGetNotifiedUpdated() {
        List<Friend> friends = new ArrayList<>();
        FacebookFriendsPresenter.FriendListUpdateListener listener = new FacebookFriendsPresenter.FriendListUpdateListener() {
            @Override
            public void onUpdated(List<Friend> friendList) {
                assertThat(friendList.get(0).getFirebaseId(), is("I24nwCQv1PMo458HcAm1sipM9ns2"));
                assertThat(friendList.get(1).getFirebaseId(), is("Jk4sEMj0JYSpJBFiaUwTV9aJMWq1"));
            }
        };
        try {
            JSONArray friendsArray = new JSONArray(mTwoFriendsJSON);
            FacebookFriendsDatabaseHelper.processFacebookFriendsJSON(friends, friendsArray, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sync_shouldGetNotifiedUpdatedWhenNoFriends() {
        List<Friend> friends = new ArrayList<>();
        FacebookFriendsPresenter.FriendListUpdateListener listener = new FacebookFriendsPresenter.FriendListUpdateListener() {
            @Override
            public void onUpdated(List<Friend> friendList) {
                assertThat(friendList.size(), is(0));
            }
        };
        try {
            JSONArray friendsArray = new JSONArray("[]");
            FacebookFriendsDatabaseHelper.processFacebookFriendsJSON(friends, friendsArray, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sync_shouldUpdateFriends() {
        Type type = new TypeToken<List<Friend>>() {
        }.getType();
        List<Friend> friends = new Gson().fromJson(mTwoFriendsJSON, type);
        String modifiedTwoFriendsJSON = "[{\"id\": \"10206318318935662\",\"name\": \"Himanshu Arora\"}" +
                ",{\"id\": \"100013760411655\",\"name\": \"Mark Smith\"}]";
        FacebookFriendsPresenter.FriendListUpdateListener listener = new FacebookFriendsPresenter.FriendListUpdateListener() {
            @Override
            public void onUpdated(List<Friend> friendList) {
                assertThat(friendList.get(1).getName(), is("Mark Smith"));
            }
        };
        try {
            JSONArray friendsArray = new JSONArray(modifiedTwoFriendsJSON);
            FacebookFriendsDatabaseHelper.processFacebookFriendsJSON(friends, friendsArray, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
