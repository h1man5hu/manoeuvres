package com.manoeuvres.android.fragments;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.R;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;


public class FriendsFragment extends Fragment {

    /*
     * This fragment can be used to remove followers, un-follow friends, follow friends, and accept
     * follow requests.
     */
    private int mFragmentBehavior;

    private FirebaseUser mUser;

    private List<Friend> mAllFriends;
    private List<Friend> mFollowing;
    private List<Friend> mFollowers;
    private List<Friend> mRequests;
    private List<Friend> mUnfollowedFriends;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private DatabaseReference mFollowersReference;
    private DatabaseReference mFollowingReference;
    private DatabaseReference mRequestsReference;
    private DatabaseReference mUsersReference;
    private DatabaseReference mMetaFollowingReference;
    private DatabaseReference mMetaFollowersReference;
    private DatabaseReference mMetaRequestsReference;
    private DatabaseReference mUserFollowersReference;
    private DatabaseReference mUserFollowingReference;
    private DatabaseReference mUserRequestsReference;
    private DatabaseReference mUserFollowersCountReference;
    private DatabaseReference mUserFollowingCountReference;
    private DatabaseReference mUserRequestsCountReference;

    private ChildEventListener mUserFollowersListener;
    private ChildEventListener mUserFollowingListener;
    private ChildEventListener mUserRequestsListener;
    private ValueEventListener mUserFollowingCountListener;
    private ValueEventListener mUserFollowersCountListener;
    private ValueEventListener mUserRequestsCountListener;

    /*
     * These are useful in multiple scenarios.
     * Used to check if all the data has been loaded or not when using ChildEventListener.
     * Used to check if there is some difference between the cache and the Firebase database.
     */
    private int mFollowingCount;
    private int mFollowersCount;
    private int mRequestsCount;

    private SharedPreferences mSharedPreferences;

    private NavigationView mNavigationView;
    private Menu mNavigationMenu;

    private Activity mParentActivity;

    private Gson mGson;

    private ContentLoadingProgressBar mProgressBar;
    private TextView mLoadingTextView;

    private boolean mFollowersLoaded;
    private boolean mFollowingLoaded;
    private boolean mRequestsLoaded;

    private ConnectivityManager mConnectivityManager;
    private boolean mIsConnected;
    private BroadcastReceiver mNetworkReceiver;
    private Snackbar mNoConnectionSnackbar;

    private NotificationManager mNotificationManager;

    public FriendsFragment() {
        // Required empty public constructor
    }

    public static FriendsFragment newInstance(int fragmentBehavior) {
        FriendsFragment fragment = new FriendsFragment();

        Bundle args = new Bundle();
        args.putInt(Constants.KEY_ARGUMENTS_FRAGMENT_BEHAVIOR_FRIENDS, fragmentBehavior);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParentActivity = getActivity();

        mFragmentBehavior = getArguments().getInt(Constants.KEY_ARGUMENTS_FRAGMENT_BEHAVIOR_FRIENDS);

        mUser = FirebaseAuth.getInstance().getCurrentUser();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference rootReference = database.getReference();
        DatabaseReference metaReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
        mUsersReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);
        mFollowersReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWERS);
        mUserFollowersReference = mFollowersReference.child(mUser.getUid());
        mMetaFollowersReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS);
        mUserFollowersCountReference = mMetaFollowersReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT);
        mFollowingReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING);
        mUserFollowingReference = mFollowingReference.child(mUser.getUid());
        mMetaFollowingReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING);
        mUserFollowingCountReference = mMetaFollowingReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
        mRequestsReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_REQUESTS);
        mUserRequestsReference = mRequestsReference.child(mUser.getUid());
        mMetaRequestsReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS);
        mUserRequestsCountReference = mMetaRequestsReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        mGson = new Gson();

        /* Caching: Load the details of the Facebook friends of the user from the shared preferences file.
         * Update the list when network can be accessed. */
        String allFriendsList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, "");
        Type type = new TypeToken<List<Friend>>() {
        }.getType();
        mAllFriends = mGson.fromJson(allFriendsList, type);
        if (mAllFriends == null) {
            mAllFriends = new ArrayList<>();
        }

        mConnectivityManager = (ConnectivityManager) mParentActivity.getSystemService(CONNECTIVITY_SERVICE);

        mNotificationManager = (NotificationManager) mParentActivity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_friends, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_friends);

        mRecyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);

        mAdapter = new FriendsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mNavigationView = (NavigationView) mParentActivity.findViewById(R.id.nav_view);
        mNavigationMenu = mNavigationView.getMenu();

        mProgressBar = (ContentLoadingProgressBar) rootView.findViewById(R.id.progress_bar_friends);

        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_friends);

        Resources resources = getResources();

        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
            mLoadingTextView.setText(String.format(resources.getString(R.string.textview_loading_friends), resources.getString(R.string.text_loading_friends_find_friends)));
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
            mLoadingTextView.setText(String.format(resources.getString(R.string.textview_loading_friends), resources.getString(R.string.text_loading_friends_followers)));
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
            mLoadingTextView.setText(resources.getString(R.string.textview_loading_following));
        } else if (mFragmentBehavior == Constants.FRAGMENT_REQUESTS) {
            mLoadingTextView.setText(String.format(resources.getString(R.string.textview_loading_friends), resources.getString(R.string.text_loading_friends_requests)));
        }

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
            mParentActivity.setTitle(R.string.title_activity_main_find_friends);
            mNavigationMenu.findItem(R.id.nav_find_friends).setChecked(true);
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
            mParentActivity.setTitle(R.string.title_activity_main_followers);
            mNavigationMenu.findItem(R.id.nav_followers).setChecked(true);
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
            mParentActivity.setTitle(R.string.title_activity_main_following);
            mNavigationMenu.findItem(R.id.nav_following).setChecked(true);
        } else if (mFragmentBehavior == Constants.FRAGMENT_REQUESTS) {
            mParentActivity.setTitle(R.string.title_activity_main_requests);
            mNavigationMenu.findItem(R.id.nav_requests).setChecked(true);
        }

        checkNetworkAndSyncData();

        if (mNetworkReceiver == null) {
            mNetworkReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(CONNECTIVITY_ACTION)) {
                        checkNetworkAndSyncData();
                    }
                }
            };
            mParentActivity.registerReceiver(mNetworkReceiver, new IntentFilter(CONNECTIVITY_ACTION));
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        stopDataSync();

        if (mNetworkReceiver != null) {
            mParentActivity.unregisterReceiver(mNetworkReceiver);
            mNetworkReceiver = null;
        }
    }

    private void updateAllFriends() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        JSONArray friends;
                        try {
                            friends = object.getJSONObject(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FRIENDS).getJSONArray(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_DATA);
                            final int numberOfFriends = friends.length();
                            for (int i = 0; i < numberOfFriends; i++) {
                                JSONObject friendJSONObject = friends.getJSONObject(i);
                                final Friend friend = new Friend(friendJSONObject.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID),
                                        friendJSONObject.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME));
                                mUsersReference.orderByChild(Constants.FIREBASE_DATABASE_REFERENCE_USERS_FACEBOOK_ID).
                                        equalTo(friend.getFacebookId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        friend.setFirebaseId(dataSnapshot.getChildren().iterator().next().getKey());
                                        int index = mAllFriends.indexOf(friend);
                                        if (index != -1) {
                                            mAllFriends.remove(index);
                                            mAllFriends.add(index, friend);
                                        } else {
                                            mAllFriends.add(friend);
                                        }


                                        /* If all the friends have been loaded, update the cache. */
                                        if (mAllFriends.size() == numberOfFriends) {
                                            mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FRIENDS, mGson.toJson(mAllFriends)).apply();
                                        }

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
        if (mFollowers == null) {
            mFollowers = new ArrayList<>();
        }

        if (mUserFollowersCountListener == null) {
            mUserFollowersCountListener = mUserFollowersCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        mFollowersCount = Integer.valueOf(dataSnapshot.getValue().toString());
                    }

                    if (mFollowingCount == 0) {
                        if (!mFollowersLoaded) {
                            mFollowersLoaded = true;
                            hideProgress();
                        }
                        mSharedPreferences.edit().remove(Constants.KEY_SHARED_PREF_DATA_FOLLOWERS).apply();
                    }

                    if (mFollowersCount > 0) {
                        if (mUserFollowersListener == null) {
                            mUserFollowersListener = mUserFollowersReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    if (!mFollowers.contains(friend)) {
                                        mFollowers.add(friend);
                                        mAdapter.notifyItemInserted(mFollowers.size() - 1);
                                    }

                                    if (mFollowers.size() == mFollowingCount) {
                                        if (!mFollowersLoaded) {
                                            mFollowersLoaded = true;
                                            hideProgress();
                                        }
                                    }
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mFollowers.indexOf(friend);
                                    if (index != -1) {
                                        mFollowers.remove(index);
                                        mAdapter.notifyItemRemoved(index);
                                    }
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void getFollowing() {
        if (mFollowing == null) {
            mFollowing = new ArrayList<>();
        }

        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
            if (mUnfollowedFriends == null) {
                mUnfollowedFriends = new ArrayList<>();
            }
        }

        if (mUserFollowingCountListener == null) {
            mUserFollowingCountListener = mUserFollowingCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        mFollowingCount = Integer.valueOf(dataSnapshot.getValue().toString());
                    }


                    if (mFollowingCount == 0) {
                        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                            mUnfollowedFriends = new ArrayList<>(mAllFriends);
                            mUnfollowedFriends.removeAll(mFollowing);
                            mAdapter.notifyDataSetChanged();
                        }

                        if (!mFollowingLoaded) {
                            mFollowingLoaded = true;
                            hideProgress();
                        }
                        mSharedPreferences.edit().remove(Constants.KEY_SHARED_PREF_DATA_FOLLOWING).apply();
                    }

                    if (mFollowingCount > 0) {
                        if (mUserFollowingListener == null) {
                            mUserFollowingListener = mUserFollowingReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    if (!mFollowing.contains(friend)) {
                                        mFollowing.add(friend);
                                        if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                                            mAdapter.notifyItemInserted(mFollowing.size() - 1);
                                        }
                                    }

                                    if (mFollowing.size() == mFollowingCount) {
                                        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                                            mUnfollowedFriends = new ArrayList<>(mAllFriends);
                                            mUnfollowedFriends.removeAll(mFollowing);
                                            mAdapter.notifyDataSetChanged();
                                        }

                                        if (!mFollowingLoaded) {
                                            mFollowingLoaded = true;
                                            hideProgress();
                                        }

                                        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowing)).apply();
                                    }
                                    mNotificationManager.cancel(UniqueId.getFollowingId(friend));
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mFollowing.indexOf(friend);
                                    if (index != -1) {
                                        mFollowing.remove(index);
                                        if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                                            mAdapter.notifyItemRemoved(index);
                                        }
                                    }

                                    /* If a friend is un-followed, it is now available to be followed. */
                                    if (mFollowing.size() == mFollowingCount) {
                                        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                                            mUnfollowedFriends = new ArrayList<>(mAllFriends);
                                            mUnfollowedFriends.removeAll(mFollowing);
                                            mAdapter.notifyDataSetChanged();
                                        }

                                        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowing)).apply();
                                    }
                                    mNotificationManager.cancel(UniqueId.getFollowingId(friend));
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    } else if (mFollowingCount == 0) {    //If the user is not following anyone, all the friends are available to be followed.
                        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                            mUnfollowedFriends = new ArrayList<>(mAllFriends);
                            mAdapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void getRequests() {
        if (mRequests == null) {
            mRequests = new ArrayList<>();
        }

        if (mUserRequestsCountListener == null) {
            mUserRequestsCountListener = mUserRequestsCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        mRequestsCount = Integer.valueOf(dataSnapshot.getValue().toString());
                    }

                    if (mRequestsCount == 0) {
                        if (!mRequestsLoaded) {
                            hideProgress();
                            mRequestsLoaded = true;
                        }
                        mSharedPreferences.edit().remove(Constants.KEY_SHARED_PREF_DATA_REQUESTS).apply();
                    }

                    if (mRequestsCount > 0) {
                        if (mUserRequestsListener == null) {
                            mUserRequestsListener = mUserRequestsReference.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    if (!mRequests.contains(friend)) {
                                        mRequests.add(friend);
                                        mAdapter.notifyItemInserted(mRequests.size() - 1);
                                    }

                                    if (mRequests.size() == mRequestsCount) {
                                        if (!mRequestsLoaded) {
                                            hideProgress();
                                            mRequestsLoaded = true;
                                        }

                                        mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_REQUESTS, mGson.toJson(mRequests)).apply();
                                    }
                                    mNotificationManager.cancel(UniqueId.getRequestId(friend));
                                }

                                @Override
                                public void onChildRemoved(DataSnapshot dataSnapshot) {
                                    Friend friend = new Friend(dataSnapshot.getValue().toString());
                                    int index = mRequests.indexOf(friend);
                                    if (index != -1) {
                                        mRequests.remove(index);
                                        mAdapter.notifyItemRemoved(index);
                                    }

                                    if (mRequests.size() == mRequestsCount) {
                                        if (!mRequestsLoaded) {
                                            hideProgress();
                                            mRequestsLoaded = true;
                                            mSharedPreferences.edit().putString(Constants.KEY_SHARED_PREF_DATA_REQUESTS, mGson.toJson(mRequests)).apply();
                                        }
                                    }
                                    mNotificationManager.cancel(UniqueId.getRequestId(friend));
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void showProgress() {
        mProgressBar.show();
        mRecyclerView.setVisibility(View.INVISIBLE);
        mLoadingTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mProgressBar.hide();
        mRecyclerView.setVisibility(View.VISIBLE);
        mLoadingTextView.setVisibility(View.INVISIBLE);
    }

    private void stopDataSync() {
        /*
         * The number of followers, requests and following should be updated in the cache as and when
         * there is a change in the Firebase database. Updating them only here can result in unnecessary
         * notifications.
         */

        if (mFollowersReference != null && mUserFollowersListener != null) {
            mFollowersReference.removeEventListener(mUserFollowersListener);
            mUserFollowersListener = null;
        }

        if (mFollowingReference != null && mUserFollowingListener != null) {
            mFollowingReference.removeEventListener(mUserFollowingListener);
            mUserFollowingListener = null;
        }

        if (mRequestsReference != null && mUserRequestsListener != null) {
            mRequestsReference.removeEventListener(mUserRequestsListener);
            mUserRequestsListener = null;
        }

        if (mMetaFollowingReference != null && mUserFollowingCountListener != null) {
            mMetaFollowingReference.removeEventListener(mUserFollowingCountListener);
            mUserFollowingCountListener = null;
        }

        if (mMetaFollowersReference != null && mUserFollowersCountListener != null) {
            mMetaFollowersReference.removeEventListener(mUserFollowersCountListener);
            mUserFollowersCountListener = null;
        }

        if (mMetaRequestsReference != null && mUserRequestsCountListener != null) {
            mMetaRequestsReference.removeEventListener(mUserRequestsCountListener);
            mUserRequestsCountListener = null;
        }

    }

    private void startDataSync() {
        updateAllFriends();

        if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
            if (!mFollowingLoaded) {
                showProgress();
            } else {
                hideProgress();
            }
            getFollowing();  // Friends which the user is already following shouldn't be displayed.
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
            if (!mFollowersLoaded) {
                showProgress();
            } else {
                hideProgress();
            }
            getFollowers();
        } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
            if (!mFollowingLoaded) {
                showProgress();
            } else {
                hideProgress();
            }
            getFollowing();
        } else if (mFragmentBehavior == Constants.FRAGMENT_REQUESTS) {
            if (!mRequestsLoaded) {
                showProgress();
            } else {
                hideProgress();
            }
            getRequests();
        }
    }


    private void isConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            mIsConnected = true;
            if (mNoConnectionSnackbar != null && mNoConnectionSnackbar.isShown()) {
                mNoConnectionSnackbar.dismiss();
            }
        } else {
            mIsConnected = false;
            mNoConnectionSnackbar = Snackbar.make(mNavigationView, R.string.snackbar_no_internet, Snackbar.LENGTH_INDEFINITE);
            mNoConnectionSnackbar.show();
        }
    }

    private void checkNetworkAndSyncData() {
        isConnected();
        if (mIsConnected) {
            startDataSync();
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            stopDataSync();
            hideProgress();
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
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
                if (mFollowing.size() > position) {
                    Friend friend = mFollowing.get(position);
                    int index = mAllFriends.indexOf(friend);
                    if (index != -1) {
                        friend = mAllFriends.get(index);
                        holder.mFriendName.setText(friend.getName());
                    }
                }

            } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                if (mFollowers.size() > position) {
                    Friend friend = mFollowers.get(position);
                    int index = mAllFriends.indexOf(friend);
                    if (index != -1) {
                        friend = mAllFriends.get(index);
                        holder.mFriendName.setText(friend.getName());
                    }
                }

            } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                if (mUnfollowedFriends.size() > position) {
                    Friend friend = mUnfollowedFriends.get(position);
                    int index = mAllFriends.indexOf(friend);
                    if (index != -1) {
                        friend = mAllFriends.get(index);
                        holder.mFriendName.setText(friend.getName());
                        holder.mButton.setText(R.string.button_text_follow);

                        /* If a request has already been sent, update the button to perform cancellation. */
                        mRequestsReference.child(friend.getFirebaseId()).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    if (snapshot.getValue() != null) {
                                        if (snapshot.getValue().equals(mUser.getUid())) {
                                            holder.mButton.setText(R.string.button_text_cancel_request);
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }
                }
            } else if (mFragmentBehavior == Constants.FRAGMENT_REQUESTS) {
                if (mRequests.size() > position) {
                    Friend friend = mRequests.get(position);
                    int index = mAllFriends.indexOf(friend);
                    if (index != -1) {
                        friend = mAllFriends.get(index);
                        holder.mFriendName.setText(friend.getName());
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWING) {
                return mFollowing.size();
            } else if (mFragmentBehavior == Constants.FRAGMENT_FOLLOWERS) {
                return mFollowers.size();
            } else if (mFragmentBehavior == Constants.FRAGMENT_FIND_FRIENDS) {
                return mUnfollowedFriends.size();
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

            /*
             * 1. Decrement by 1 the count of the number of friends the user is following.
             * 2. Remove the Firebase Id of the friend from the list of friends the user is following.
             * 3. Decrement by 1 the count of the number of followers of the friend.
             * 4. Remove the Firebase Id of the user from the list of followers of the friend.
             */
            void unfollowFriend() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFollowing.get(getAdapterPosition())));

                mUserFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            mUserFollowingCountReference.setValue(count - 1);
                        }

                        final DatabaseReference userFollowingReference = mFollowingReference.child(mUser.getUid());
                        userFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                        userFollowingReference.child(snapshot.getKey()).setValue(null);
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

                final DatabaseReference friendFollowersCountReference = mMetaFollowersReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWERS_COUNT);
                friendFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            friendFollowersCountReference.setValue(count - 1);

                            final DatabaseReference friendFollowersReference = mFollowersReference.child(friend.getFirebaseId());
                            friendFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        if (snapshot.getValue().equals(mUser.getUid())) {
                                            friendFollowersReference.child(snapshot.getKey()).setValue(null);
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            /*
             * 1. Decrement by 1 the count of the number of followers of the user.
             * 2. Remove the Firebase Id of the friend from the list of followers of the user.
             * 3. Decrement by 1 the count of the number of friends the friend is following.
             * 4. Remove the Firebase Id of the user from the list of the friends the friend is following.
             */
            void removeFollower() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mFollowers.get(getAdapterPosition())));

                mUserFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            mUserFollowersCountReference.setValue(count - 1);

                            mUserFollowersReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        if (snapshot.getValue() != null) {
                                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                                mUserFollowersReference.child(snapshot.getKey()).setValue(null);
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

                final DatabaseReference friendFollowingCountReference = mMetaFollowingReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
                friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            friendFollowingCountReference.setValue(count - 1);

                            final DatabaseReference friendFollowingReference = mFollowingReference.child(friend.getFirebaseId());
                            friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        if (snapshot.getValue() != null) {
                                            if (snapshot.getValue().equals(mUser.getUid())) {
                                                friendFollowingReference.child(snapshot.getKey()).setValue(null);
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            /*
             * 1. Increment by 1 the count of the number of requests the friend has.
             * 2. Add the Firebase Id of the user to the list of the requests of the friend.
             */
            void followFriend() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mUnfollowedFriends.get(getAdapterPosition())));

                final DatabaseReference friendRequestsCountReference = mMetaRequestsReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
                friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        int count = 0;
                        if (dataSnapshot.getValue() != null) {
                            count = Integer.valueOf(dataSnapshot.getValue().toString());
                        }
                        friendRequestsCountReference.setValue(count + 1);

                        final DatabaseReference friendRequestsReference = mRequestsReference.child(friend.getFirebaseId());
                        friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                String key = "0";
                                if (dataSnapshot.getValue() != null) {
                                    key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                                }
                                friendRequestsReference.child(key).setValue(mUser.getUid());
                                mButton.setText(R.string.button_text_cancel_request);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            /*
             * 1. Decrement by 1 the count of the number of requests the friend has.
             * 2. Remove the Firebase Id of the user from the list of the requests of the friend.
             */
            void cancelRequest() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mUnfollowedFriends.get(getAdapterPosition())));

                final DatabaseReference friendRequestsCountReference = mMetaRequestsReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_REQUESTS_COUNT);
                friendRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            friendRequestsCountReference.setValue(count - 1);

                            final DatabaseReference friendRequestsReference = mRequestsReference.child(friend.getFirebaseId());
                            friendRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        if (snapshot.getValue() != null) {
                                            if (snapshot.getValue().equals(mUser.getUid())) {
                                                friendRequestsReference.child(snapshot.getKey()).setValue(null);
                                                mButton.setText(R.string.button_text_follow);
                                            }
                                        } else {
                                            break;
                                        }

                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            /*
             * 1. Decrement by 1 the count of the number of requests the user has.
             * 2. Remove the Firebase Id of the friend from the list of requests the user has.
             * 3. Increment by 1 the count of the number of followers the user has.
             * 4. Add the Firebase Id of the friend to the list of followers the user has.
             * 5. Increment by 1 the count of the number of friends the friend is following.
             * 6. Add the Firebase Id of the user to the list of friends the friend is following.
             */
            void acceptRequest() {
                final Friend friend = mAllFriends.get(mAllFriends.indexOf(mRequests.get(getAdapterPosition())));

                mUserRequestsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() != null) {
                            int count = Integer.valueOf(dataSnapshot.getValue().toString());
                            mUserRequestsCountReference.setValue(count - 1);

                            final DatabaseReference userRequestsReference = mRequestsReference.child(mUser.getUid());
                            userRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        if (snapshot.getValue() != null) {
                                            if (snapshot.getValue().equals(friend.getFirebaseId())) {
                                                userRequestsReference.child(snapshot.getKey()).setValue(null);
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

                mUserFollowersCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        int count = 0;
                        if (dataSnapshot.getValue() != null) {
                            count = Integer.valueOf(dataSnapshot.getValue().toString());
                        }
                        mUserFollowersCountReference.setValue(count + 1);

                        mUserFollowersReference.orderByKey().limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                String key = "0";
                                if (dataSnapshot.getValue() != null) {
                                    key = String.valueOf(Integer.valueOf(dataSnapshot.getChildren().iterator().next().getKey()) + 1);
                                }
                                mUserFollowersReference.child(key).setValue(friend.getFirebaseId());
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

                final DatabaseReference friendFollowingCountReference = mMetaFollowingReference.child(friend.getFirebaseId()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
                friendFollowingCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        int count = 0;
                        if (dataSnapshot.getValue() != null) {
                            count = Integer.valueOf(dataSnapshot.getValue().toString());
                        }
                        friendFollowingCountReference.setValue(count + 1);

                        final DatabaseReference friendFollowingReference = mFollowingReference.child(friend.getFirebaseId());
                        friendFollowingReference.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.getKey() != null) {
                                    friendFollowingReference.child(String.valueOf(dataSnapshot.getChildrenCount())).setValue(mUser.getUid());
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        }
    }
}
