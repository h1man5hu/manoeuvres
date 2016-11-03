package com.manoeuvres.android.core;


import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenu;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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
import com.facebook.login.LoginManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.manoeuvres.android.R;
import com.manoeuvres.android.login.LoginActivity;
import com.manoeuvres.android.timeline.logs.Log;
import com.manoeuvres.android.timeline.moves.Move;
import com.manoeuvres.android.database.DatabaseHelper;
import com.manoeuvres.android.friends.following.FollowingPresenter;
import com.manoeuvres.android.friends.following.FollowingPresenter.FollowingListener;
import com.manoeuvres.android.notifications.LatestLogPresenter;
import com.manoeuvres.android.notifications.LatestLogPresenter.LatestLogListener;
import com.manoeuvres.android.timeline.moves.MovesPresenter;
import com.manoeuvres.android.notifications.NotificationService;
import com.manoeuvres.android.friends.findfriends.FindFriendsFragment;
import com.manoeuvres.android.friends.followers.FollowersFragment;
import com.manoeuvres.android.friends.following.FollowingFragment;
import com.manoeuvres.android.friends.requests.RequestsFragment;
import com.manoeuvres.android.timeline.TimelineFragment;
import com.manoeuvres.android.friends.Friend;

import java.util.Arrays;
import java.util.List;

import com.manoeuvres.android.network.NetworkMonitor;
import com.manoeuvres.android.network.NetworkMonitor.NetworkListener;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;


public class MainActivity extends AppCompatActivity
        implements FollowingListener, LatestLogListener, NetworkListener, NavigationView.OnNavigationItemSelectedListener {

    private FollowingPresenter mFollowingPresenter;
    private LatestLogPresenter mLatestLogPresenter;
    private MovesPresenter mMovesPresenter;

    private NetworkMonitor mNetworkMonitor;

    private FloatingActionButton mFab;
    private DrawerLayout mDrawerLayout;
    private NavigationMenu mNavigationMenu;

    private FirebaseUser mUser;

    private Gson mGson;

    private SharedPreferences mSharedPreferences;

    private FragmentManager mFragmentManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Initialize Facebook SDK. Should be called as early as possible. */
        FacebookSdk.sdkInitialize(getApplicationContext());

        /* App events for analytics. */
        AppEventsLogger.activateApp(getApplication());

        mUser = FirebaseAuth.getInstance().getCurrentUser();

        mGson = new Gson();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        initializeViews();

        mFragmentManager = getSupportFragmentManager();

        /* Default fragment to show: Timeline of the user. */
        mFragmentManager.beginTransaction().add(R.id.content_main, TimelineFragment.newInstance(mUser.getUid(), mUser.getDisplayName()), Constants.TAG_FRAGMENT_TIMELINE).commit();
    }

    private void initializeViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new FabClickListener());
        mFab.hide();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mNavigationMenu = (NavigationMenu) navigationView.getMenu();
        mNavigationMenu.findItem(R.id.nav_timeline).setChecked(true); //Set user's timeline as default position.

        View navigationHeaderView = navigationView.getHeaderView(0);
        TextView name = (TextView) navigationHeaderView.findViewById(R.id.navigation_header_textview_name);
        name.setText(mUser.getDisplayName());
        ImageView profilePicture = (ImageView) navigationHeaderView.findViewById(R.id.navigation_header_imageview_profile_picture);
        profilePicture.setVisibility(View.INVISIBLE);
    }

    private void updateFab(boolean moveInProgress) {
        if (moveInProgress) {
            mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, R.color.colorFabStop)));
            mFab.setImageResource(R.drawable.ic_stop_white_24dp);
        } else {
            mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, R.color.colorAccent)));
            mFab.setImageResource(R.drawable.ic_share_white_24dp);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mFollowingPresenter = FollowingPresenter.getInstance(getApplicationContext())
                .attach(this)
                .sync();

        mLatestLogPresenter = LatestLogPresenter.getInstance(getApplicationContext())
                .addFriend(mUser.getUid())
                .attach(this, mUser.getUid())
                .sync();

        updateFab(mLatestLogPresenter.isInProgress(mUser.getUid()));

        mMovesPresenter = MovesPresenter.getInstance(getApplicationContext())
                .addFriend(mUser.getUid())
                .sync();

        mNetworkMonitor = NetworkMonitor.getInstance(getApplicationContext()).attach(this);

        startService(new Intent(this, NotificationService.class));

        if (mNetworkMonitor.isNetworkConnected()) onNetworkConnected();
        else onNetworkDisconnected();

        /* Add the menu items for the cached friends to the navigation menu. */
        for (int i = 0; i < mFollowingPresenter.size(); i++)
            onFollowingAdded(i, mFollowingPresenter.get(i));

        /* Remove the menu items for the friends which were removed in the background. */
        List<Friend> removedFollowing = mFollowingPresenter.getRemovedFollowing();
        if (removedFollowing != null)
            for (int i = 0; i < removedFollowing.size(); i++) {
                Friend removedFriend = removedFollowing.get(i);
                onFollowingRemoved(0, removedFriend);
            }
    }

    @Override
    public void onStartFollowingLoading() {

    }

    @Override
    public void onFollowingInitialization() {

    }

    @Override
    public void onFollowingAdded(int index, Friend newFriend) {
        int friendMenuId = UniqueId.getMenuId(newFriend);
        MenuItem friendMenuItem = mNavigationMenu.findItem(friendMenuId);
        if (friendMenuItem == null) {
            friendMenuItem = mNavigationMenu.add(R.id.nav_group_timelines, friendMenuId, 1, newFriend.getName()).setCheckable(true);
            if (friendMenuItem != null) friendMenuItem.setIcon(R.drawable.ic_person_black_24dp);
        }
    }

    @Override
    public void onFollowingChanged(int index, Friend updatedFriend) {
        MenuItem menuItem = mNavigationMenu.findItem(UniqueId.getMenuId(updatedFriend));
        if (menuItem != null)
            menuItem.setTitle(updatedFriend.getName());
    }

    @Override
    public void onFollowingRemoved(int index, Friend removedFriend) {
        int friendMenuId = UniqueId.getMenuId(removedFriend);
        if (mNavigationMenu.findItem(friendMenuId) != null)
            mNavigationMenu.removeItem(friendMenuId);

        /* If the user is viewing the timeline of the friend which was removed, destroy the timeline fragment. */
        Fragment fragment = mFragmentManager.findFragmentByTag(UniqueId.getTimelineFragmentTag(removedFriend));
        if (fragment != null)
            mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onCompleteFollowingLoading() {

    }

    @Override
    public void onLatestLogChanged(String userId, Log log, boolean inProgress) {
       /*
        * If the latest log is in progress, the mFab should display a stop icon and stop the move when clicked.
        * Otherwise, it should display a share icon and display a list of moves to share when clicked.
        */
        if (userId.equals(mUser.getUid())) updateFab(inProgress);
    }

    /*
     * onNetworkConnected and onNetworkDisconnected are called from a thread in a separate process
     * which pings the Google's server to check for network availability. Make sure to explicitly
     * run the code block on the UI thread, if required.
     */
    @Override
    public void onNetworkConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNavigationMenu.setGroupVisible(R.id.nav_group_profile, true);
            }
        });
    }

    @Override
    public void onNetworkDisconnected() {
        if (mNavigationMenu.findItem(R.id.nav_followers).isChecked()
                || mNavigationMenu.findItem(R.id.nav_following).isChecked()
                || mNavigationMenu.findItem(R.id.nav_find_friends).isChecked()
                || mNavigationMenu.findItem(R.id.nav_requests).isChecked()) {
            mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNavigationMenu.setGroupVisible(R.id.nav_group_profile, false);
            }
        });
    }

    /*
     * Close the drawer if it's open.
     * Pop all the fragment transactions if pressed from a fragment other than timeline fragment.
     */
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START);
        else if (mFragmentManager.getBackStackEntryCount() > 0)
            /* The back button should always take the user to the timeline. */
            mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        else super.onBackPressed();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.nav_timeline) {
            startTimelineFragment(false, id);
        } else if (id == R.id.nav_followers) {
            startFriendsFragment(Constants.FRAGMENT_FOLLOWERS, id);
        } else if (id == R.id.nav_following) {
            startFriendsFragment(Constants.FRAGMENT_FOLLOWING, id);
        } else if (id == R.id.nav_find_friends) {
            startFriendsFragment(Constants.FRAGMENT_FIND_FRIENDS, id);
        } else if (id == R.id.nav_requests) {
            startFriendsFragment(Constants.FRAGMENT_REQUESTS, id);
        } else if (id == R.id.nav_log_out) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            stopService(new Intent(MainActivity.this, NotificationService.class));
            FirebaseAuth.getInstance().signOut();
            LoginManager.getInstance().logOut();
            mSharedPreferences.edit().clear().apply();
            finish();
        } else {
            startTimelineFragment(true, id);
        }

        mDrawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /*
     * Initialize and start the TimelineFragment with the details of the user or a friend.
     * The friend is found by comparing the menuId of each friend to the menuId of the
     * menu item which was clicked.
     */
    private void startTimelineFragment(boolean isFriend, int menuId) {
        MenuItem menuItem = mNavigationMenu.findItem(menuId);
        if (menuItem != null && menuItem.isChecked()) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            TimelineFragment fragment;
            if (isFriend) {
                List<Friend> following = mFollowingPresenter.getAll();
                for (int i = 0; i < following.size(); i++) {
                    Friend friend = following.get(i);
                    if (UniqueId.getMenuId(friend) == menuId) {
                        fragment = (TimelineFragment) mFragmentManager.findFragmentByTag(UniqueId.getTimelineFragmentTag(friend));
                        if (fragment == null) {
                            fragment = TimelineFragment.newInstance(friend.getFirebaseId(), friend.getName());
                        }
                        mFragmentManager.beginTransaction().replace(R.id.content_main, fragment, UniqueId.getTimelineFragmentTag(friend)).addToBackStack("").commit();
                        mFab.hide();
                        break;
                    }
                }
            } else {
                /* Simulate the back button if timeline is selected in the navigation menu. */
                fragment = (TimelineFragment) mFragmentManager.findFragmentByTag(Constants.TAG_FRAGMENT_TIMELINE);
                if (fragment == null) {
                    fragment = TimelineFragment.newInstance(mUser.getUid(), mUser.getDisplayName());
                    mFragmentManager.beginTransaction().replace(R.id.content_main, fragment, Constants.TAG_FRAGMENT_TIMELINE).commit();
                } else
                    mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                mFab.show();
            }
        }
    }

    private void startFriendsFragment(int fragmentId, int menuId) {
        if (mNavigationMenu.findItem(menuId).isChecked())
            mDrawerLayout.closeDrawer(GravityCompat.START);
        else {
            String fragmentTag = null;
            if (fragmentId == Constants.FRAGMENT_FOLLOWING)
                fragmentTag = Constants.TAG_FRAGMENT_FOLLOWING;
            else if (fragmentId == Constants.FRAGMENT_FOLLOWERS)
                fragmentTag = Constants.TAG_FRAGMENT_FOLLOWERS;
            else if (fragmentId == Constants.FRAGMENT_FIND_FRIENDS)
                fragmentTag = Constants.TAG_FRAGMENT_FIND_FRIENDS;
            else if (fragmentId == Constants.FRAGMENT_REQUESTS)
                fragmentTag = Constants.TAG_FRAGMENT_REQUESTS;

            Fragment fragment = mFragmentManager.findFragmentByTag(fragmentTag);
            FragmentTransaction transaction = mFragmentManager.beginTransaction();
            if (fragment == null) {
                if (fragmentId == Constants.FRAGMENT_FOLLOWING)
                    transaction.replace(R.id.content_main, FollowingFragment.newInstance(), fragmentTag);
                else if (fragmentId == Constants.FRAGMENT_FOLLOWERS)
                    transaction.replace(R.id.content_main, FollowersFragment.newInstance(), fragmentTag);
                else if (fragmentId == Constants.FRAGMENT_FIND_FRIENDS)
                    transaction.replace(R.id.content_main, FindFriendsFragment.newInstance(), fragmentTag);
                else if (fragmentId == Constants.FRAGMENT_REQUESTS)
                    transaction.replace(R.id.content_main, RequestsFragment.newInstance(), fragmentTag);
            } else
                transaction.replace(R.id.content_main, fragment, fragmentTag);

            mFab.hide();
            transaction.addToBackStack("").commit();
        }
    }

    /* If a notification is pressed when the activity is running. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle bundle = intent.getExtras();
        String notificationType = bundle.getString(Constants.KEY_EXTRA_NOTIFICATION_SERVICE);
        if (notificationType != null) {
            if (notificationType.equals(Constants.NOTIFICATION_TYPE_REQUEST))
                startFriendsFragment(Constants.FRAGMENT_REQUESTS, R.id.nav_requests);
            else if (notificationType.equals(Constants.NOTIFICATION_TYPE_FOLLOWING) || (notificationType.equals(Constants.NOTIFICATION_TYPE_LOG)))
                startTimelineFragment(true, UniqueId.getMenuId(new Friend(bundle.getString(Constants.KEY_EXTRA_FRAGMENT_TIMELINE_FRIEND_ID))));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        /* Update the cache of friends the user is following and the status of latest log. */
        if (!isFinishing()) {
            String following = mGson.toJson(mFollowingPresenter.getAll());
            mSharedPreferences.edit()
                    .putString(Constants.KEY_SHARED_PREF_MENU_ITEMS_FOLLOWING, following)
                    .putBoolean(Constants.KEY_SHARED_PREF_IS_MOVE_IN_PROGRESS, mLatestLogPresenter.isInProgress(mUser.getUid()))
                    .apply();
        }

        mNetworkMonitor.detach(this);
        mLatestLogPresenter.detach(this, mUser.getUid());
        mFollowingPresenter.detach(this);
    }

    class FabClickListener implements View.OnClickListener {

        /* Used to confirm if the mFab was clicked from the user's timeline. */
        private Fragment mCurrentFragment;

        @Override
        public void onClick(View view) {
            mCurrentFragment = mFragmentManager.findFragmentById(R.id.content_main);

            if (mCurrentFragment instanceof TimelineFragment) {
                if (!mLatestLogPresenter.isInProgress(mUser.getUid())) {
                    Object[] movesObjects = mMovesPresenter.getAll(mUser.getUid()).values().toArray();
                    Move[] moves = Arrays.copyOf(movesObjects, movesObjects.length, Move[].class);
                    final ArrayAdapter<Move> arrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_item_move_selector_title, moves);

                    new AlertDialog.Builder(MainActivity.this).setTitle(R.string.title_alert_dialog_select_move).setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Move move = (Move) mMovesPresenter.getAll(mUser.getUid()).values().toArray()[i];
                            String moveId = mMovesPresenter.getKey(mUser.getUid(), move);
                            if (moveId != null) DatabaseHelper.pushMove(new Log(moveId));
                        }
                    }).setNegativeButton(R.string.button_text_alert_dialog_negative, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).show();
                } else {
                    DatabaseHelper.stopLatestMove();
                }
            }
        }
    }
}
