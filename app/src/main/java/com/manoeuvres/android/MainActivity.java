package com.manoeuvres.android;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

//Views
import android.view.View;
import android.view.MenuItem;

//v4-Support
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;

//v7-Support
import android.support.v7.app.AlertDialog;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

//Support widgets
import android.support.design.widget.NavigationView;
import android.support.design.widget.FloatingActionButton;

//Widgets
import android.widget.ArrayAdapter;
import android.widget.TextView;

//FacebookSDK
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

//Firebase authentication
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

//Firebase database
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

//Models
import com.manoeuvres.android.models.Friend;

//Collections
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivityLog";    //For logging.

    private int mItemId = -1;   //Menu resource id for friends' timeline in navigation drawer.

    private List<Friend> mFriends;  //To display menu item for each friend on the navigation drawer menu.

    //Views
    private Toolbar mToolbar;
    private FloatingActionButton mFab;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private NavigationView mNavigationView;
    private View mNavigationHeaderView;

    private FirebaseUser mUser; //Used to read and update data of current user.

    //Database references.
    private DatabaseReference mRootReference;
    private DatabaseReference mUsersReference;
    private DatabaseReference mFollowingReference;

    //If a move is in progress, fab acts as a button to stop the move.
    // Otherwise, it opens a dialog to select a new move.
    private boolean mMoveInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize Facebook SDK. Call this as early as possible.
        FacebookSdk.sdkInitialize(getApplicationContext());

        //App events for analytics.
        AppEventsLogger.activateApp(getApplication());

        mUser = FirebaseAuth.getInstance().getCurrentUser();

        initializeViews();

        //Display the timeline of the current user. (default)
        getSupportFragmentManager().beginTransaction().add(R.id.content_main, new TimelineFragment(), "TIMELINE_FRAGMENT").commit();

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        mRootReference = firebaseDatabase.getReference();
        mUsersReference = mRootReference.child("users");
        mFollowingReference = mRootReference.child("following");

        //setProfilePicture();

        //Check if the latest log is in progress or not.
        mRootReference.child("logs").child(mUser.getUid()).limitToLast(1).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //If the endTime of the latest move is not initialized, the move is still in progress.
                if (dataSnapshot.getChildren().iterator().next().getValue(com.manoeuvres.android.models.Log.class).getEndTime() == 0) {
                    mMoveInProgress = true;
                } else {
                    mMoveInProgress = false;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        addFriendsToNavigationDrawer();
    }

    private void addFriendsToNavigationDrawer() {

        mFriends = new ArrayList<>();

        //Get the current user's friends.
        mFollowingReference.child(mUser.getUid()).addChildEventListener(new ChildEventListener() {

            //If a new friend is added,
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                //Get the firebase id of the added friend.
                final String firebaseUserId = dataSnapshot.getValue().toString();

                //Use the firebase id to get the facebook id and name of the friend from the users reference
                //and add a menu item for it.
                mUsersReference.child(firebaseUserId).addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        //Set the details and add the friend to the list.
                        Friend friend = dataSnapshot.getValue(Friend.class);
                        friend.setFirebaseId(firebaseUserId);
                        mFriends.add(friend);

                        //Position of the friend in the list is treated as the id for its menu item.
                        mNavigationView.getMenu().add(R.id.nav_group_timelines, ++mItemId, 1, friend.getName()).setCheckable(true);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void setProfilePicture() {

        //Get the name from the firebase database using firebase id.
        //To-Do: Access the name from a preferences file to load faster.
        mUsersReference.child(mUser.getUid()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                TextView name = (TextView) findViewById(R.id.navigation_header_textview_name);
                name.setText(dataSnapshot.getValue().toString());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        /*
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        if (object != null) {

                            ImageView profilePicture = (ImageView) findViewById(R.id.navigation_header_imageview_profile_picture);
                                //To-Do: Set Profile picture.
                            }
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "name,picture");
        request.setParameters(parameters);
        request.executeAsync();
        */
    }

    private void initializeViews() {
        //Initialize toolbar.
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        //Initialize floating action button.
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new FabClickListener());
        mFab.show();

        //Initialize drawer layout.
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        //Initialize action bar toggle.
        mToggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();

        //Initialize navigation view.
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        mNavigationView.getMenu().findItem(R.id.nav_timeline).setChecked(true); //Set user's timeline as default position.

        //Display the user's name on activity's title and
        //the navigation drawer header.
        mNavigationHeaderView = mNavigationView.getHeaderView(0);
        TextView name = (TextView) mNavigationHeaderView.findViewById(R.id.navigation_header_textview_name);
        name.setText(mUser.getDisplayName());
        setTitle(mUser.getDisplayName());
    }

    //Close the drawer if it's open.
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        //TimelineFragment is used to display both the user's logs
        //and the user's friends' logs.
        boolean isFriend = false;

        Class fragmentClass;
        Fragment fragment;

        if (id == R.id.nav_timeline) {
            fragmentClass = TimelineFragment.class;
        } else if (id == R.id.nav_followers) {
            fragmentClass = FollowersFragment.class;
        } else if (id == R.id.nav_following) {
            fragmentClass = FollowingFragment.class;
        } else if (id == R.id.nav_find_friends) {
            fragmentClass = FindFriendsFragment.class;
        } else if (id == R.id.nav_requests) {
            fragmentClass = RequestsFragment.class;
        } else if (id == R.id.nav_settings) {
            return false;
        } else if (id == R.id.nav_log_out) {
            return false;
        } else {
            isFriend = true;
            fragmentClass = TimelineFragment.class; //Use the TimelineFragment with different user id.
        }

        try {

            fragment = (Fragment) fragmentClass.newInstance();

            //Pass the appropriate user id to TimelineFragment.
            if (fragmentClass == TimelineFragment.class) {
                Bundle args = new Bundle();
                if (isFriend) {

                    Friend friend = mFriends.get(id);

                    //Menu item's id directly reflects the position of the friend in the list.
                    args.putString("firebaseUserId", friend.getFirebaseId());

                    setTitle(friend.getName()); //Update the activity's title to match the timeline.

                    mFab.hide();    //Don't allow insertions into friends' timeline.
                } else {
                    args.putString("firebaseUserId", mUser.getUid());

                    setTitle(mUser.getDisplayName());   //Update the activity's title to match the timeline.

                    mFab.show();
                }
                fragment.setArguments(args);
            } else {
                mFab.hide();    //Fab is not required in other fragments.
            }

            getSupportFragmentManager().beginTransaction().replace(R.id.content_main, fragment).commit();

        } catch (Exception e) {
            e.printStackTrace();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    class FabClickListener implements View.OnClickListener {

        private Fragment mCurrentFragment;  //Behavior of the fab depends on the fragment which is currently visible.

        @Override
        public void onClick(View view) {
            mCurrentFragment = getSupportFragmentManager().findFragmentById(R.id.content_main);

            //If the button is clicked from the timeline,
            if (mCurrentFragment instanceof TimelineFragment) {

                //If there is no move in progress, display a dialog to select a new move.
                if (!mMoveInProgress) {

                    //Adapter to display moves in an alert dialog.
                    final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.move_selector_listitem_title);

                    //Data of all the moves. One of which will be selected to create a log and then pushed.
                    final List<DataSnapshot> dataSnapshots = new ArrayList<>();

                    //Fill the adapter with the data from the Firebase database.
                    mRootReference.child("moves").child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot move : dataSnapshot.getChildren()) {
                                arrayAdapter.add(move.child("name").getValue().toString());
                                dataSnapshots.add(move);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });

                    //Create an alert dialog, push a log when a move is selected.
                    new AlertDialog.Builder(MainActivity.this).setTitle("Select a move to broadcast").setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mRootReference.child("logs").child(mUser.getUid()).push().setValue(new com.manoeuvres.android.models.Log(dataSnapshots.get(i).getKey()));
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).show();
                }

                //If a move is in progress, stop that move.
                else {
                    mRootReference.child("logs").child(mUser.getUid()).limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot ds : dataSnapshot.getChildren()) {
                                mRootReference.child("logs").child(mUser.getUid()).child(ds.getKey()).child("endTime").setValue(System.currentTimeMillis());
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
