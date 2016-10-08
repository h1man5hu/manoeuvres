package com.manoeuvres.android;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.appevents.AppEventsLogger;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivityLog";

    //Views
    private Toolbar mToolbar;
    private FloatingActionButton mFab;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private NavigationView mNavigationView;

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
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

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

        } else if (id == R.id.nav_log_out) {

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

}
