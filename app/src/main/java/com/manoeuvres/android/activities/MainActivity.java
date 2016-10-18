package com.manoeuvres.android.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenu;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.MenuItem;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.NavigationView;
import android.support.design.widget.FloatingActionButton;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
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
import com.manoeuvres.android.fragments.FriendsFragment;
import com.manoeuvres.android.fragments.TimelineFragment;
import com.manoeuvres.android.models.Friend;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    /*
     * To display a menu item on the navigation drawer menu for each friend the user is following.
     * The user can click on the friend's menu item to see his/her timeline.
     */
    private List<Friend> mFollowing;

    private Toolbar mToolbar;
    private FloatingActionButton mFab;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private NavigationView mNavigationView;
    private View mNavigationHeaderView;
    private NavigationMenu mNavigationMenu;

    private FirebaseUser mUser;

    private DatabaseReference mRootReference;
    private DatabaseReference mUsersReference;
    private DatabaseReference mUserFollowingReference;
    private DatabaseReference mUserMovesReference;
    private DatabaseReference mUserLogsReference;
    private DatabaseReference mMetaReference;
    private DatabaseReference mMetaFollowingReference;
    private DatabaseReference mUserFollowingCountReference;
    private DatabaseReference mUserLogsCountReference;

    private ValueEventListener mLatestLogListener;
    private ChildEventListener mUserFollowingListener;
    private ValueEventListener mUserFollowingCountListener;

    /*
     * If a move is in progress, mFab acts as a button to stop the move.
     * Otherwise, it opens a dialog to select a new move.
     */
    private boolean mMoveInProgress;

    private SharedPreferences mSharedPreferences;

    private FragmentManager mFragmentManager;

    private Gson mGson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Initialize Facebook SDK. Should be called as early as possible. */
        FacebookSdk.sdkInitialize(getApplicationContext());

        /* App events for analytics. */
        AppEventsLogger.activateApp(getApplication());

        mUser = FirebaseAuth.getInstance().getCurrentUser();

        /*
         * Caching: Load the friends which the user is following from the shared preferences file.
         * Update the list when network can be accessed.
         */
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mGson = new Gson();
        String friendList = mSharedPreferences.getString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, "");
        Type type = new TypeToken<List<Friend>>() {
        }.getType();
        mFollowing = mGson.fromJson(friendList, type);
        if (mFollowing == null) {
            mFollowing = new ArrayList<>();
        }

        mMoveInProgress = mSharedPreferences.getBoolean(Constants.KEY_SHARED_PREF_IS_MOVE_IN_PROGRESS, false);

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        mRootReference = firebaseDatabase.getReference();
        mUsersReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS);
        mUserMovesReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES).child(mUser.getUid());
        mUserFollowingReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_FOLLOWING).child(mUser.getUid());
        mMetaReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
        mUserLogsReference = mRootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS).child(mUser.getUid());
        mMetaFollowingReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING);
        mUserFollowingCountReference = mMetaFollowingReference.child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_FOLLOWING_COUNT);
        mUserLogsCountReference = mMetaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS).child(mUser.getUid()).child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT);

        initializeViews();

        mFragmentManager = getSupportFragmentManager();

        /* Default fragment to show: Timeline of the user. */
        mFragmentManager.beginTransaction().add(R.id.content_main, TimelineFragment.newInstance(mUser.getUid(), mUser.getDisplayName()), Constants.TAG_FRAGMENT_TIMELINE).commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        isMoveInProgress();
        updateFollowing();
    }

    /*
     * Checks the state of the latest log in the Firebase database.
     * If the latest log is in progress, the mFab should display a stop icon and stop the move when clicked.
     * Otherwise, it should display a share icon and display a list of moves to share when clicked.
     */
    private void isMoveInProgress() {
        if (mLatestLogListener == null) {
            mLatestLogListener = mUserLogsReference.limitToLast(1).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.hasChildren()) {

                    /* If the endTime field of the latest log has a default value (0), the move is still in progress. */
                        if (dataSnapshot.getChildren().iterator().next().getValue(com.manoeuvres.android.models.Log.class).getEndTime() == 0) {
                            mMoveInProgress = true;
                        } else {
                            mMoveInProgress = false;
                        }

                        updateFab(mMoveInProgress);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    /* Checks for any updates on the cached list, which could have happened when the activity wasn't running. */
    private void updateFollowing() {
        if (mUserFollowingCountListener == null) {
            mUserFollowingCountListener = mUserFollowingCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                /*
                 * If the count on the Firebase database is zero but there are friends in the cached list,
                 * remove all of them.
                 */
                    if (count == 0) {
                        if (mFollowing.size() > 0) {
                            for (Friend removedFriend : mFollowing) {
                                int removedFriendMenuId = UniqueId.getMenuId(removedFriend);
                                if (mNavigationMenu.findItem(removedFriendMenuId) != null) {
                                    mNavigationMenu.removeItem(removedFriendMenuId);
                                }
                            }
                            mFollowing.clear();
                        }
                    }

                /*
                 * In case not all but some of the friends were removed, this list will
                 * be subtracted from the cached list to get the friends which were removed.
                 * Each friend on the Firebase database is added to this list.
                 * The subtraction will be done when all the friends have been retrieved from the
                 * Firebase database.
                 */
                    final List<Friend> updatedFollowing = new ArrayList<>();

                    if (mUserFollowingListener == null) {
                        mUserFollowingListener = mUserFollowingReference.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                final String firebaseId = dataSnapshot.getValue().toString();
                                if (mFollowing.indexOf(firebaseId) == -1) {
                            /* Get the details of the added friend from the users reference using the firebase id. */
                                    mUsersReference.child(firebaseId).addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            Friend friend = dataSnapshot.getValue(Friend.class);
                                            friend.setFirebaseId(firebaseId);
                                            mFollowing.add(friend);

                                            int friendMenuId = UniqueId.getMenuId(friend);
                                            if (mNavigationMenu.findItem(friendMenuId) == null) {
                                                mNavigationMenu.add(R.id.nav_group_timelines, friendMenuId, 1, friend.getName()).setCheckable(true);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                    updatedFollowing.add(new Friend(firebaseId));

                            /*
                             * All the friends have been retrieved, subtract the list and remove menu items
                             * for removed friends, if any.
                             */
                                    if (updatedFollowing.size() == count) {
                                        if (mFollowing.size() > count) {
                                            List<Friend> removedFollowing = new ArrayList<>(mFollowing);
                                            removedFollowing.removeAll(updatedFollowing);
                                            for (Friend removedFriend : removedFollowing) {
                                                int removedFriendMenuId = UniqueId.getMenuId(removedFriend);
                                                if (mNavigationMenu.findItem(removedFriendMenuId) != null) {
                                                    mNavigationMenu.removeItem(removedFriendMenuId);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                String firebaseId = dataSnapshot.getValue().toString();
                                Friend friend = new Friend(firebaseId);
                                if (mFollowing.indexOf(friend) != -1) {
                                    mFollowing.remove(friend);
                                }

                                int friendMenuId = UniqueId.getMenuId(friend);
                                if (mNavigationMenu.findItem(friendMenuId) != null) {
                                    mNavigationMenu.removeItem(friendMenuId);
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

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        /* Update the cache of friends the user is following and the status of latest log. */
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(Constants.KEY_SHARED_PREF_DATA_FOLLOWING, mGson.toJson(mFollowing));
        editor.putBoolean(Constants.KEY_SHARED_PREF_IS_MOVE_IN_PROGRESS, mMoveInProgress);
        editor.apply();

        if (mUserLogsReference != null && mLatestLogListener != null)
            mUserLogsReference.removeEventListener(mLatestLogListener);
        if (mUserFollowingReference != null && mUserFollowingListener != null)
            mUserFollowingReference.removeEventListener(mUserFollowingListener);
        if (mUserFollowingCountReference != null && mUserFollowingCountListener != null)
            mUserFollowingCountReference.removeEventListener(mUserFollowingCountListener);
    }

    private void initializeViews() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new FabClickListener());
        mFab.show();
        updateFab(mMoveInProgress);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mToggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();

        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        mNavigationMenu = (NavigationMenu) mNavigationView.getMenu();
        mNavigationMenu.findItem(R.id.nav_timeline).setChecked(true); //Set user's timeline as default position.

        mNavigationHeaderView = mNavigationView.getHeaderView(0);
        TextView name = (TextView) mNavigationHeaderView.findViewById(R.id.navigation_header_textview_name);
        name.setText(mUser.getDisplayName());
        ImageView profilePicture = (ImageView) mNavigationHeaderView.findViewById(R.id.navigation_header_imageview_profile_picture);
        profilePicture.setVisibility(View.INVISIBLE);

        /* Add the menu items for the cached friends to the navigation menu. */
        for (Friend friend : mFollowing) {
            int friendMenuId = UniqueId.getMenuId(friend);
            if (mNavigationMenu.findItem(friendMenuId) == null) {
                mNavigationMenu.add(R.id.nav_group_timelines, friendMenuId, 1, friend.getName()).setCheckable(true);
            }
        }
    }

    /* Close the drawer if it's open. */
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        /* States whether the TimelineFragment should display the user's logs, or a friend's logs. */
        boolean isFriend = false;

        /*
         * States the behaviour of the FriendsFragment.
         * It can be used to remove followers, un-follow friends, follow new friends, and accept
         * follow requests.
         */
        int friendsFragmentBehavior = 0;

        Class fragmentClass;

        if (id == R.id.nav_timeline) {
            fragmentClass = TimelineFragment.class;
        } else if (id == R.id.nav_followers) {
            fragmentClass = FriendsFragment.class;
            friendsFragmentBehavior = Constants.FRAGMENT_FOLLOWERS;
        } else if (id == R.id.nav_following) {
            fragmentClass = FriendsFragment.class;
            friendsFragmentBehavior = Constants.FRAGMENT_FOLLOWING;
        } else if (id == R.id.nav_find_friends) {
            fragmentClass = FriendsFragment.class;
            friendsFragmentBehavior = Constants.FRAGMENT_FIND_FRIENDS;
        } else if (id == R.id.nav_requests) {
            fragmentClass = FriendsFragment.class;
            friendsFragmentBehavior = Constants.FRAGMENT_REQUESTS;
        } else if (id == R.id.nav_settings) {
            return false;
        } else if (id == R.id.nav_log_out) {
            return false;
        } else {
            isFriend = true;
            fragmentClass = TimelineFragment.class;
        }

        if (fragmentClass == TimelineFragment.class) {
            startTimelineFragment(isFriend, id);
        } else if (fragmentClass == FriendsFragment.class) {
            startFriendsFragment(friendsFragmentBehavior);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /*
     * Initialize and start the TimelineFragment with the details of the user or a friend.
     * The friend is found by comparing the menuId of each friend to the menuId of the
     * menu item which was clicked.
     */
    void startTimelineFragment(boolean isFriend, int menuId) {
        TimelineFragment fragment;
        if (isFriend) {
            for (Friend friend : mFollowing) {
                if (UniqueId.getMenuId(friend) == menuId) {
                    fragment = TimelineFragment.newInstance(friend.getFirebaseId(), friend.getName());
                    mFragmentManager.beginTransaction().replace(R.id.content_main, fragment).commit();
                    mFab.hide();
                    break;
                }
            }

        } else {
            fragment = TimelineFragment.newInstance(mUser.getUid(), mUser.getDisplayName());
            mFragmentManager.beginTransaction().replace(R.id.content_main, fragment).commit();
            mFab.show();
        }
    }

    void startFriendsFragment(int friendsFragmentBehavior) {
        FriendsFragment fragment = FriendsFragment.newInstance(friendsFragmentBehavior);
        mFab.hide();
        mFragmentManager.beginTransaction().replace(R.id.content_main, fragment).commit();
    }

    void updateFab(boolean moveInProgress) {
        if (moveInProgress) {
            mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, R.color.colorFabStop)));
            mFab.setImageResource(R.drawable.ic_stop_white_24dp);
        } else {
            mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, R.color.colorAccent)));
            mFab.setImageResource(R.drawable.ic_share_white_24dp);
        }
    }

    class FabClickListener implements View.OnClickListener {

        /* Used to confirm if the mFab was clicked from the user's timeline. */
        private Fragment mCurrentFragment;

        @Override
        public void onClick(View view) {
            mCurrentFragment = mFragmentManager.findFragmentById(R.id.content_main);

            if (mCurrentFragment instanceof TimelineFragment) {
                if (!mMoveInProgress) {
                    final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_item_move_selector_title);
                    final List<DataSnapshot> moves = new ArrayList<>();
                    mUserMovesReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot move : dataSnapshot.getChildren()) {
                                arrayAdapter.add(move.child(Constants.FIREBASE_DATABASE_REFERENCE_USERS_NAME).getValue().toString());
                                moves.add(move);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });


                    new AlertDialog.Builder(MainActivity.this).setTitle(R.string.title_alert_dialog_select_move).setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mUserLogsReference.push().setValue(new com.manoeuvres.android.models.Log(moves.get(i).getKey()));

                            mUserLogsCountReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    int count = 0;
                                    if (dataSnapshot.getValue() != null) {
                                        count = Integer.valueOf(dataSnapshot.getValue().toString());
                                    }
                                    mUserLogsCountReference.setValue(count + 1);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });

                        }
                    }).setNegativeButton(R.string.button_text_alert_dialog_negative, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).show();
                }

                else {
                    mUserLogsReference.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (snapshot.getKey() != null) {
                                    mUserLogsReference.child(snapshot.getKey()).child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS_ENDTIME).setValue(System.currentTimeMillis());
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
        }
    }
}
