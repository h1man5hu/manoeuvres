package com.manoeuvres.android.fragments;


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
import android.support.design.internal.NavigationMenu;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.manoeuvres.android.activities.MainActivity;
import com.manoeuvres.android.models.Friend;
import com.manoeuvres.android.models.Log;
import com.manoeuvres.android.models.Move;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;


public class TimelineFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private DatabaseReference mCurrentUserLogsReference;
    private DatabaseReference mCurrentUserMovesReference;
    private DatabaseReference mCurrentUserMovesCountReference;
    private DatabaseReference mCurrentUserLogsCountReference;

    private ChildEventListener mLogsListener;
    private ChildEventListener mMovesListener;
    private ValueEventListener mMovesCountListener;
    private ValueEventListener mLogsCountListener;

    /*
     * This fragment can display the logs of either the user or a friend of the user.
     * If the logs of a friend are to be displayed, these details are taken from the
     * fragment's arguments. Otherwise, these are initialized to the user's details.
     */
    private String mCurrentUserId;
    private String mCurrentUserName;

    /*
     * A log is bound to a move with its moveId. A moveId is the push id of the move.
     * The map stores the push id of the move as the key to the move.
     */
    private Map<String, Move> mMoves;
    private List<com.manoeuvres.android.models.Log> mLogs;

    private SharedPreferences mSharedPreferences;

    private MainActivity mParentActivity;
    private FloatingActionButton mFab;
    private NavigationMenu mNavigationMenu;

    private Gson mGson;

    /* Logs are updated only after the moves are updated. */
    private boolean mMovesUpdated;

    private ContentLoadingProgressBar mProgressBar;
    private TextView mLoadingTextView;

    private boolean mIsFriend;

    private ConnectivityManager mConnectivityManager;
    private boolean mIsConnected;
    private BroadcastReceiver mNetworkReceiver;
    private Snackbar mNoConnectionSnackbar;

    private NotificationManager mNotificationManager;

    public TimelineFragment() {
        // Required empty public constructor
    }

    public static TimelineFragment newInstance(String currentUserId, String currentUserName) {
        TimelineFragment fragment = new TimelineFragment();

        Bundle args = new Bundle();
        args.putString(Constants.KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE_, currentUserId);
        args.putString(Constants.KEY_ARGUMENTS_USER_NAME_FRAGMENT_TIMELINE, currentUserName);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        mParentActivity = (MainActivity) getActivity();

        Bundle bundle = getArguments();
        if (bundle != null) {
            mCurrentUserId = bundle.getString(Constants.KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE_);
            mCurrentUserName = bundle.getString(Constants.KEY_ARGUMENTS_USER_NAME_FRAGMENT_TIMELINE);
            if (user != null && !mCurrentUserId.equals(user.getUid())) {
                mIsFriend = true;
            }
        } else {
            if (user != null) {
                mCurrentUserId = user.getUid();
                mCurrentUserName = user.getDisplayName();
            }
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        mGson = new Gson();

        /*
         * Caching: Load the logs and moves associated to the current user from the shared preferences file.
         * Update the list when network can be accessed.
         */
        String logList = mSharedPreferences.getString(UniqueId.getLogsDataKey(mCurrentUserId), "");
        Type type = new TypeToken<List<Log>>() {
        }.getType();
        mLogs = mGson.fromJson(logList, type);
        if (mLogs == null) {
            mLogs = new ArrayList<>();
        }
        String movesList = mSharedPreferences.getString(UniqueId.getMovesDataKey(mCurrentUserId), "");
        type = new TypeToken<Map<String, Move>>() {
        }.getType();
        mMoves = mGson.fromJson(movesList, type);
        if (mMoves == null) {
            mMoves = new HashMap<>();
        }

        DatabaseReference rootReference = FirebaseDatabase.getInstance().getReference();
        mCurrentUserLogsReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS).child(mCurrentUserId);
        mCurrentUserMovesReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES).child(mCurrentUserId);
        DatabaseReference metaReference = rootReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META);
        mCurrentUserMovesCountReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES).child(mCurrentUserId).child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT);
        mCurrentUserLogsCountReference = metaReference.child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS).child(mCurrentUserId).child(Constants.FIREBASE_DATABASE_REFERENCE_META_LOGS_COUNT);

        mConnectivityManager = (ConnectivityManager) mParentActivity.getSystemService(CONNECTIVITY_SERVICE);

        mNotificationManager = (NotificationManager) mParentActivity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_timeline);

        mRecyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);

        mAdapter = new TimelineAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mFab = (FloatingActionButton) mParentActivity.findViewById(R.id.fab);

        mProgressBar = (ContentLoadingProgressBar) rootView.findViewById(R.id.progress_bar_timeline);
        mLoadingTextView = (TextView) rootView.findViewById(R.id.textView_loading_logs);

        NavigationView navigationView = (NavigationView) mParentActivity.findViewById(R.id.nav_view);
        mNavigationMenu = (NavigationMenu) navigationView.getMenu();

        Resources resources = getResources();
        String formatString = resources.getString(R.string.textview_loading_logs);
        String nameArgument;
        if (mIsFriend) {
            nameArgument = mCurrentUserName + "'s";
        } else {
            nameArgument = resources.getString(R.string.text_loading_logs_your);
        }
        mLoadingTextView.setText(String.format(formatString, nameArgument));

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mParentActivity.setTitle(mCurrentUserName);
        MenuItem menuItem;
        if (!mIsFriend) {
            menuItem = mNavigationMenu.findItem(R.id.nav_timeline);
        } else {
            menuItem = mNavigationMenu.findItem(UniqueId.getMenuId(new Friend(mCurrentUserId)));
        }
        if (menuItem != null)
            menuItem.setChecked(true);

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

    private void updateLogs() {
        if (mLogsCountListener == null) {
            mLogsCountListener = mCurrentUserLogsCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    if (count == 0) {
                        hideProgress();
                        mSharedPreferences.edit().remove(UniqueId.getLatestLogKey(mCurrentUserId)).apply();
                    } else {
                        if (mLogsListener == null) {
                            mLogsListener = mCurrentUserLogsReference.limitToLast(Constants.LIMIT_LOG_COUNT).addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    Log newLog = dataSnapshot.getValue(Log.class);
                                    int index = mLogs.indexOf(newLog);
                                    if (index == -1) {
                                        if (mLogs.size() >= Constants.LIMIT_LOG_COUNT) {
                                            mLogs.remove(mLogs.size() - 1);
                                            mAdapter.notifyItemRemoved(mAdapter.getItemCount() - 1);
                                        }
                                        mLogs.add(0, newLog);
                                        mAdapter.notifyItemInserted(0);
                                        mRecyclerView.scrollToPosition(0);
                                        mSharedPreferences.edit().putString(UniqueId.getLatestLogKey(mCurrentUserId), mGson.toJson(newLog)).apply();
                                    } else {    // If due to any bug, a previous log wasn't updated, update it now.
                                        Log oldLog = mLogs.get(index);
                                        if (oldLog.getEndTime() == 0 && newLog.getEndTime() != 0) {
                                            oldLog.setEndTime(newLog.getEndTime());
                                            mAdapter.notifyItemChanged(index);
                                        }
                                    }

                                    int limit = Constants.LIMIT_LOG_COUNT;
                                    if (count < limit) {
                                        limit = count;
                                    }
                                    if (mLogs.size() == limit) {
                                        hideProgress();
                                        mNotificationManager.cancel(UniqueId.getLogId(new Friend(mCurrentUserId)));
                                    }
                                }

                                @Override
                                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                    Log updatedLog = dataSnapshot.getValue(Log.class);
                                    Log oldLog = mLogs.get(0);  //Changes are only allowed to the latest log, if it's in progress.
                                    if ((oldLog.getMoveId().equals(updatedLog.getMoveId())) && (oldLog.getStartTime() == updatedLog.getStartTime())) {
                                        oldLog.setEndTime(updatedLog.getEndTime());
                                        mAdapter.notifyItemChanged(0);
                                        mSharedPreferences.edit().putString(UniqueId.getLatestLogKey(mCurrentUserId), mGson.toJson(updatedLog)).apply();
                                        mNotificationManager.cancel(UniqueId.getLogId(new Friend(mCurrentUserId)));
                                    }
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
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void updateMoves() {
        if (mMovesCountListener == null) {
            mMovesCountListener = mCurrentUserMovesCountReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                    if (mMovesListener == null) {
                        mMovesListener = mCurrentUserMovesReference.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                String key = dataSnapshot.getKey();
                                Move move = dataSnapshot.getValue(Move.class);
                                if (!mMoves.containsKey(key)) {
                                    mMoves.put(key, move);
                                }
                                if (mMoves.size() == count) {
                                    if (!mMovesUpdated) {
                                        mMovesUpdated = true;
                                        updateLogs();
                                    }
                                }
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                String key = dataSnapshot.getKey();
                                Move move = dataSnapshot.getValue(Move.class);
                                if (mMoves.containsKey(key)) {
                                    mMoves.put(key, move);
                                }
                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {
                                String key = dataSnapshot.getKey();
                                if (mMoves.containsKey(key)) {
                                    mMoves.remove(key);
                                }
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
    public void onStop() {
        super.onStop();

        /* Update the cache of logs and moves for this user. */
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(UniqueId.getLogsDataKey(mCurrentUserId), mGson.toJson(mLogs));
        editor.putString(UniqueId.getMovesDataKey(mCurrentUserId), mGson.toJson(mMoves));
        editor.apply();

        stopDataSync();

        if (mNetworkReceiver != null) {
            mParentActivity.unregisterReceiver(mNetworkReceiver);
            mNetworkReceiver = null;
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
            mNoConnectionSnackbar = Snackbar.make(mRecyclerView, R.string.snackbar_no_internet, Snackbar.LENGTH_INDEFINITE);
            mNoConnectionSnackbar.show();
        }
    }

    private void stopDataSync() {
        if (mCurrentUserLogsReference != null && mLogsListener != null) {
            mCurrentUserLogsReference.removeEventListener(mLogsListener);
            mLogsListener = null;
        }

        if (mCurrentUserLogsCountReference != null && mLogsCountListener != null) {
            mCurrentUserLogsCountReference.removeEventListener(mLogsCountListener);
            mLogsCountListener = null;
        }

        if (mCurrentUserMovesReference != null && mMovesListener != null) {
            mCurrentUserMovesReference.removeEventListener(mMovesListener);
            mMovesListener = null;
        }

        if (mCurrentUserMovesCountReference != null && mMovesCountListener != null) {
            mCurrentUserMovesCountReference.removeEventListener(mMovesCountListener);
            mMovesCountListener = null;
        }
    }

    private void startDataSync() {
        /*
         * This method calls the updateLogs method after all the moves are updated. Calling it before can
         * lead to empty view holders in the recycler view if some new moves were added by the friend
         * when the fragment wasn't running.
         */
        updateMoves();

        /* If this is not the first time startDataSync is getting called, the moves are already updated, update the logs. */
        if (mMovesUpdated) {
            updateLogs();
        }
    }

    private void checkNetworkAndSyncData() {
        isConnected();
        if (mIsConnected) {
            /* If there is no cache, display progress until the data is loaded from the network. */
            if (mMoves.size() == 0 || mLogs.size() == 0) {
                showProgress();
            } else {
                hideProgress();
            }
            startDataSync();
        } else {
            hideProgress();
            stopDataSync();
            mFab.hide();
        }
    }

    private void showProgress() {
        mFab.hide();
        mProgressBar.show();
        mRecyclerView.setVisibility(View.INVISIBLE);
        mLoadingTextView.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mProgressBar.hide();
        mRecyclerView.setVisibility(View.VISIBLE);
        mLoadingTextView.setVisibility(View.INVISIBLE);
        if (!mIsFriend) {
            if (mIsConnected) {
                mFab.show();
            }
        }
    }

    public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

        @Override
        public TimelineAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_cardview_timeline, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(TimelineAdapter.ViewHolder holder, int position) {
            com.manoeuvres.android.models.Log log = mLogs.get(position);
            Move move = mMoves.get(log.getMoveId());
            /*
             * The text to be displayed depends on the status of the log. If it is in progress,
             * display the text in present tense, otherwise display it in past tense.
             */
            if (move != null) {
                if (log.getEndTime() != 0) {
                    holder.mMoveTitle.setText(move.getPast());
                    holder.mMoveSubtitle.setText(String.format(getResources().getString(R.string.log_sub_title_text_past), log.getStartTime(), log.getEndTime()));
                }
                else {
                    holder.mMoveTitle.setText(move.getPresent());
                    holder.mMoveSubtitle.setText(String.format(getResources().getString(R.string.log_sub_title_text_present), log.getStartTime()));
                }
            }
        }

        @Override
        public int getItemCount() {
            return mLogs.size();
        }


        class ViewHolder extends RecyclerView.ViewHolder {
            TextView mMoveTitle;
            TextView mMoveSubtitle;

            ViewHolder(View view) {
                super(view);
                mMoveTitle = (TextView) view.findViewById(R.id.move_text);
                mMoveSubtitle = (TextView) view.findViewById(R.id.move_subtext);
            }
        }
    }
}
