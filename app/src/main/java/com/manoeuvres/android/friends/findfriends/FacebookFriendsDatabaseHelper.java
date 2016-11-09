package com.manoeuvres.android.friends.findfriends;


import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.friends.requests.RequestsDatabaseHelper;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

class FacebookFriendsDatabaseHelper {

    private static void getFacebookFriendsJSON(final FacebookFriendsPresenter.GraphRequestListener listener) {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        listener.onRequestComplete(object, response);
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS, Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS);
        request.setParameters(parameters);
        request.executeAsync();
    }

    static void processFacebookFriendsJSON(final List<Friend> friends, JSONArray array, final FacebookFriendsPresenter.FriendListUpdateListener listener) {
        final int numberOfFriends = array.length();
        for (int i = 0; i < numberOfFriends; i++) {
            JSONObject friendJSONObject;
            final Friend friend;
            try {
                friendJSONObject = array.getJSONObject(i);
                friend = new Friend(
                        friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID),
                        friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME));
                FacebookFriendsDatabaseHelper.getFirebaseId(friend.getFacebookId(), new FacebookFriendsPresenter.FirebaseIdListener() {
                    @Override
                    public void onLoaded(String firebaseId) {
                        friend.setFirebaseId(firebaseId);
                        int index = friends.indexOf(friend);
                        if (index != -1) {
                            friends.remove(index);
                            friends.add(index, friend);
                        } else friends.add(friend);

                        if (friends.size() == numberOfFriends) {
                            listener.onUpdated(friends);
                        }
                    }

                    @Override
                    public void onFailed() {

                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    static void updateFriends(final List<Friend> friends, final FacebookFriendsPresenter.FriendListUpdateListener listener) {
        getFacebookFriendsJSON(new FacebookFriendsPresenter.GraphRequestListener() {
            @Override
            public void onRequestComplete(JSONObject object, GraphResponse response) {
                JSONArray friendsJSONArray;
                try {
                    if (object != null) {
                        friendsJSONArray = object.getJSONObject(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS)
                                .getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                        processFacebookFriendsJSON(friends, friendsJSONArray, listener);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void getFirebaseId(Long facebookId, final FacebookFriendsPresenter.FirebaseIdListener listener) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference(Constants.FIREBASE_DATABASE_REFERENCE_USERS)
                .orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).
                equalTo(facebookId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChildren()) {
                    listener.onLoaded(dataSnapshot.getChildren().iterator().next().getKey());
                } else listener.onFailed();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    static void isRequested(final Friend friend, final FacebookFriendsPresenter.RequestListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        RequestsDatabaseHelper.getRequestsDataReference(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChildren()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        if (snapshot.getValue().equals(userId)) listener.onComplete(true);
                    }
                } else listener.onComplete(false);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    /*
     * 1. Increment by 1 the count of the number of requests the friend has.
     * 2. Add the Firebase Id of the user to the list of the requests of the friend.
     */
    static void followFriend(final Friend friend, final FacebookFriendsPresenter.FollowFriendListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference friendRequestsCountReference = RequestsDatabaseHelper.getRequestsCountReference(friend.getFirebaseId());

        friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int count = 0;
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    count = Integer.valueOf(value.toString());
                }

                if (count < 0) friendRequestsCountReference.setValue(0);
                else friendRequestsCountReference.setValue(count + 1);

                final DatabaseReference friendRequestsReference = RequestsDatabaseHelper.getRequestsDataReference(friend.getFirebaseId());
                friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String key = "0";
                        if (dataSnapshot.hasChildren()) {
                            key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                        }
                        friendRequestsReference.child(key).setValue(userId);
                        listener.onRequested();
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        listener.onFailed();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    /*
     * 1. Decrement by 1 the count of the number of requests the friend has.
     * 2. Remove the Firebase Id of the user from the list of the requests of the friend.
     */
    static void cancelRequest(final Friend friend, final FacebookFriendsPresenter.CancelRequestListener listener) {
        final String userId = AuthPresenter.getCurrentUserId();
        if (userId == null) return;

        final DatabaseReference friendRequestsCountReference = RequestsDatabaseHelper.getRequestsCountReference(friend.getFirebaseId());
        friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                if (value != null) {
                    int count = Integer.valueOf(value.toString());
                    if (count > 0) friendRequestsCountReference.setValue(count - 1);
                    else friendRequestsCountReference.setValue(0);

                    final DatabaseReference friendRequestsReference = RequestsDatabaseHelper.getRequestsDataReference(friend.getFirebaseId());
                    friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChildren()) {
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    if (snapshot.getValue().equals(userId)) {
                                        friendRequestsReference.child(snapshot.getKey()).setValue(null);
                                        listener.onRequestCancelled();
                                        return;
                                    }
                                }
                            }
                            listener.onFailed();
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            listener.onFailed();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                listener.onFailed();
            }
        });
    }

    static void getUserName(String userId, final FacebookFriendsPresenter.GetNameListener listener) {
        AuthPresenter.getUserProfileReference(userId).child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_NAME)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Object value = dataSnapshot.getValue();
                        if (value != null) listener.onLoaded(value.toString());
                        else listener.onFailed();
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        listener.onFailed();
                    }
                });
    }
}
