package com.manoeuvres.android;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.appevents.AppEventsLogger;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivityLog";

    //Views
    private Toolbar mToolbar;
    private FloatingActionButton mFab;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private NavigationView mNavigationView;

    //Firebase
    private FirebaseUser mUser;
    private DatabaseReference mDatabaseRoot;

    private boolean mMoveInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize Facebook SDK. Call this as early as possible.
        FacebookSdk.sdkInitialize(getApplicationContext());

        //App events for analytics.
        AppEventsLogger.activateApp(getApplication());

        initializeViews();

        setNameAndProfilePicture();

        getSupportFragmentManager().beginTransaction().add(R.id.content_main, new TimelineFragment(), "TIMELINE_FRAGMENT").commit();

        mUser = FirebaseAuth.getInstance().getCurrentUser();
        mDatabaseRoot = FirebaseDatabase.getInstance().getReference();

        //Check if the latest log is in progress or not.
        mDatabaseRoot.child("logs").child(mUser.getUid()).limitToLast(1).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
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
    }

    private void setNameAndProfilePicture() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        if (object != null) {
                            TextView name = (TextView) findViewById(R.id.navigation_header_textview_name);
                            ImageView profilePicture = (ImageView) findViewById(R.id.navigation_header_imageview_profile_picture);

                            try {
                                name.setText(object.getString("name"));
                                //To-Do: Set Profile picture.
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "name,picture");
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void initializeViews() {
        //Initialize toolbar.
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        //Initialize floating action button.
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new FabClickListener());
        mFab.hide();

        //Initialize drawer layout.
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        //Initialize action bar toggle.
        mToggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.setDrawerListener(mToggle);
        mToggle.syncState();

        //Initialize navigation view.
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        mNavigationView.getMenu().findItem(R.id.nav_timeline).setChecked(true);
    }

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

        Class fragmentClass = null;
        Fragment fragment = null;

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
        }

        try {
            fragment = (Fragment) fragmentClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getSupportFragmentManager().beginTransaction().replace(R.id.content_main, fragment).commit();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    class FabClickListener implements View.OnClickListener {

        private Fragment mCurrentFragment;

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
                    mDatabaseRoot.child("moves").child(mUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
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
                            mDatabaseRoot.child("logs").child(mUser.getUid()).push().setValue(new com.manoeuvres.android.models.Log(dataSnapshots.get(i).getKey()));
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).show();
                }

                //If a move is in progress, stop that move.
                else {
                    mDatabaseRoot.child("logs").child(mUser.getUid()).limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot ds : dataSnapshot.getChildren()) {
                                mDatabaseRoot.child("logs").child(mUser.getUid()).child(ds.getKey()).child("endTime").setValue(System.currentTimeMillis());
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
