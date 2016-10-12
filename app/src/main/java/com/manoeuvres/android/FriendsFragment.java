package com.manoeuvres.android;


import android.os.Bundle;
import android.util.Log;

//Support library
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

//Views
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

//Widgets
import android.widget.Button;
import android.widget.TextView;

//FacebookSDK
import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;

//Models
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;

//JSON
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//Collections
import java.util.ArrayList;
import java.util.List;


public class FriendsFragment extends Fragment {

    private List<Friend> mAllFriends;
    private List<Friend> mFollowing;
    private List<Friend> mFollowers;
    private List<Friend> mRequests;
    private List<Friend> mFriends;

    //RecyclerView.
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    //Firebase Database.
    private FirebaseDatabase mDatabase;
    private DatabaseReference mRootReference;
    private DatabaseReference mFollowersReference;
    private DatabaseReference mFollowingReference;
    private DatabaseReference mRequestsReference;
    private DatabaseReference mUsersReference;

    private FirebaseUser mUser;

    private int mFragmentBehavior;

    public FriendsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_friends, container, false);

        //Firebase database initialization.
        mDatabase = FirebaseDatabase.getInstance();
        mRootReference = mDatabase.getReference();
        mUsersReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);

        mAllFriends = new ArrayList<>();
        mFollowing = new ArrayList<>();
        mFollowers = new ArrayList<>();
        mFriends = new ArrayList<>();
        mRequests = new ArrayList<>();

        mUser = FirebaseAuth.getInstance().getCurrentUser();

        //Get all the Facebook friends of the current user by a GraphRequest.
        getAllFriends();

        mFragmentBehavior = getArguments().getInt(Constants.KEY_ARGUMENTS_FRAGMENT_BEHAVIOR_FRIENDS);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_friends);

        //To improve performance if changes in content do not change the layout size of the RecyclerView.
        mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new FriendsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        return rootView;
    }

    private void getAllFriends() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        JSONArray friends;
                        try {
                            friends = object.getJSONObject(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS).getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                            for (int i = 0; i < friends.length(); i++) {
                                JSONObject friendJSONObject = friends.getJSONObject(i);
                                final Friend friend = new Friend(friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID), friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME));
                                mUsersReference.orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).equalTo(friend.getFacebookId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        friend.setFirebaseId(dataSnapshot.getChildren().iterator().next().getKey());
                                        mAllFriends.add(friend);

                                        // Calling these methods from onCreateView can cause problems, since mAllFriends stores the details of
                                        // all the friends. These methods just get their Firebase id.
                                        getFollowers();
                                        getFollowing();
                                        getRequests();
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {

                                    }
                                });
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS, Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS);
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void getFollowers() {
        mFollowersReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWERS);
        final GenericTypeIndicator<List<String>> type = new GenericTypeIndicator<List<String>>() {
        };
        mFollowersReference.child(mUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mFollowers.clear();
                List<String> ids = dataSnapshot.getValue(type);
                try {
                    for (String id : ids) {
                        if (id != null) {
                            Friend friend = new Friend();
                            friend.setFirebaseId(id);
                            mFollowers.add(friend);
                        }
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getFollowing() {
        mFollowingReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING);
        final GenericTypeIndicator<List<String>> type = new GenericTypeIndicator<List<String>>() {
        };
        mFollowingReference.child(mUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mFollowing.clear();
                List<String> ids = dataSnapshot.getValue(type);
                try {
                    for (String id : ids) {
                        if (id != null) {
                            Friend friend = new Friend();
                            friend.setFirebaseId(id);
                            mFollowing.add(friend);
                        }
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                mFriends = new ArrayList<>(mAllFriends);
                mFriends.removeAll(mFollowing);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getRequests() {
        mRequestsReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS);
        final GenericTypeIndicator<List<String>> type = new GenericTypeIndicator<List<String>>() {
        };
        mRequestsReference.child(mUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mRequests.clear();
                List<String> ids = dataSnapshot.getValue(type);
                try {
                    for (String id : ids) {
                        if (id != null) {
                            Friend friend = new Friend();
                            friend.setFirebaseId(id);
                            mRequests.add(friend);
                        }
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

        @Override
        public FriendsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(final FriendsAdapter.ViewHolder holder, int position) {
            if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                holder.mFriendName.setText(mAllFriends.get(mAllFriends.indexOf(mFollowing.get(position))).getName());
            } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                holder.mFriendName.setText(mAllFriends.get(mAllFriends.indexOf(mFollowers.get(position))).getName());
            } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                Friend friend = mAllFriends.get(mAllFriends.indexOf(mFriends.get(position)));
                holder.mFriendName.setText(friend.getName());
                holder.mButton.setText(R.string.button_text_follow);
                mRequestsReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(mUser.getUid())) {
                                holder.mButton.setText(R.string.button_text_cancel_request);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });


            } else {
                holder.mFriendName.setText(mAllFriends.get(mAllFriends.indexOf(mRequests.get(position))).getName());
            }
        }

        @Override
        public int getItemCount() {
            if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                return mFollowing.size();
            } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                return mFollowers.size();
            } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                return mFriends.size();
            } else {
                return mRequests.size();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView mFriendName;
            Button mButton;

            ViewHolder(View view) {
                super(view);
                mFriendName = (TextView) view.findViewById(R.id.textView_list_item_friend_name);
                mButton = (Button) view.findViewById(R.id.button_list_item_friend);
                mButton.setOnClickListener(this);
                if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                    mButton.setText(R.string.button_text_unfollow);
                } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                    mButton.setText(R.string.button_text_remove);
                } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                    mButton.setText(R.string.button_text_follow);
                } else {
                    mButton.setText(R.string.button_text_accept);
                }
            }

            @Override
            public void onClick(View view) {
                if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                    unfollowFriend();
                } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                    removeFollower();
                } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                    if (mButton.getText().equals(getResources().getString(R.string.button_text_follow))) {
                        followFriend();
                    } else if (mButton.getText().equals(getResources().getString(R.string.button_text_cancel_request))) {
                        cancelRequest();
                    }
                } else {
                    acceptRequest();
                }
            }

            void unfollowFriend() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFollowing.get(getAdapterPosition())));
                mFollowingReference.child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                mFollowingReference.child(mUser.getUid()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                mFollowersReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(mUser.getUid())) {
                                mFollowersReference.child(friend.getFirebaseId()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            void removeFollower() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFollowers.get(getAdapterPosition())));
                mFollowersReference.child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                mFollowersReference.child(mUser.getUid()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                mFollowingReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(mUser.getUid())) {
                                mFollowingReference.child(friend.getFirebaseId()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            void followFriend() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFriends.get(getAdapterPosition())));
                mRequestsReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        try {
                            mRequestsReference.child(friend.getFirebaseId()).child(String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1)).setValue(mUser.getUid());
                        } catch (Exception e) {
                            mRequestsReference.child(friend.getFirebaseId()).child("0").setValue(mUser.getUid());
                        }
                        mButton.setText(R.string.button_text_cancel_request);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            void cancelRequest() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFriends.get(getAdapterPosition())));
                mRequestsReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(mUser.getUid())) {
                                mRequestsReference.child(friend.getFirebaseId()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                        mButton.setText(R.string.button_text_follow);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            void acceptRequest() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mRequests.get(getAdapterPosition())));
                mRequestsReference.child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                mRequestsReference.child(mUser.getUid()).child(snapshot.getKey()).setValue(null);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                mFollowersReference.child(mUser.getUid()).orderByKey().limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        try {
                            mFollowersReference.child(mUser.getUid()).child(String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1)).setValue(friend.getFirebaseId());
                        } catch (Exception e) {
                            mFollowersReference.child(mUser.getUid()).child("0").setValue(friend.getFirebaseId());
                        }

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                mFollowingReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        mFollowingReference.child(friend.getFirebaseId()).child(String.valueOf(dataSnapshot.getChildrenCount())).setValue(mUser.getUid());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        }
    }
}
