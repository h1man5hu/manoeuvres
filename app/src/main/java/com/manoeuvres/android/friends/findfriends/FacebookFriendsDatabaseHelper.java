package com.manoeuvres.android.friends.findfriends;

import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.login.AuthPresenter;
import com.manoeuvres.android.database.CompletionListeners.GetStringListener;
import com.manoeuvres.android.database.CompletionListeners.GraphRequestListener;
import com.manoeuvres.android.database.CompletionListeners.OnCompleteListener;
import com.manoeuvres.android.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

class FacebookFriendsDatabaseHelper {

    static void updateFriends(final List<Friend> friends, final OnCompleteListener listener) {
        getFacebookFriendsJSON(new GraphRequestListener() {
            @Override
            public void onComplete(JSONObject object, GraphResponse response) {
                JSONArray friendsJSONArray;
                try {
                    if (object != null) {
                        friendsJSONArray = object.getJSONObject(
                                Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS
                        ).getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                        processFacebookFriendsJSON(friends, friendsJSONArray, listener);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed() {

            }
        });
    }

    private static void getFacebookFriendsJSON(final GraphRequestListener listener) {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        listener.onComplete(object, response);
                    }
                }
        );
        Bundle parameters = new Bundle();
        parameters.putString(
                Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS,
                Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS
        );
        request.setParameters(parameters);
        request.executeAsync();
    }

    private static void processFacebookFriendsJSON(final List<Friend> friends,
                                                   JSONArray array,
                                                   final OnCompleteListener listener
    ) {
        final int numberOfFriends = array.length();
        for (int i = 0; i < numberOfFriends; i++) {
            JSONObject friendJSONObject;
            final Friend friend;
            try {
                friendJSONObject = array.getJSONObject(i);
                friend = new Friend(
                        friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID),
                        friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME)
                );
                getFirebaseId(friend.getFacebookId(), new GetStringListener() {
                            @Override
                            public void onComplete(String firebaseId) {
                                friend.setFirebaseId(firebaseId);
                                int index = friends.indexOf(friend);
                                if (index != -1) {
                                    friends.remove(index);
                                    friends.add(index, friend);
                                } else {
                                    friends.add(friend);
                                }

                                if (friends.size() == numberOfFriends) {
                                    listener.onComplete();
                                }
                            }

                            @Override
                            public void onFailed() {

                            }
                        }
                );
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getFirebaseId(Long facebookId, final GetStringListener listener) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference(Constants.FIREBASE_DATABASE_REFERENCE_USERS)
                .orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID)
                .equalTo(facebookId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChildren()) {
                            listener.onComplete(
                                    dataSnapshot.getChildren().iterator().next().getKey()
                            );
                        } else {
                            listener.onFailed();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        listener.onFailed();
                    }
                }
        );
    }

    static void getName(String userId, final GetStringListener listener) {
        AuthPresenter.getUserProfileReference(userId)
                .child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_NAME)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Object value = dataSnapshot.getValue();
                                if (value != null) {
                                    listener.onComplete(value.toString());
                                } else {
                                    listener.onFailed();
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                listener.onFailed();
                            }
                        }
                );
    }
}
