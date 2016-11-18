package com.manoeuvres.android.timeline;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.internal.NavigationMenu;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.manoeuvres.android.R;
import com.manoeuvres.android.core.MainActivity;
import com.manoeuvres.android.timeline.logs.LogsPresenter;
import com.manoeuvres.android.timeline.logs.LogsPresenter.LogsListener;
import com.manoeuvres.android.timeline.moves.MovesPresenter;
import com.manoeuvres.android.timeline.moves.MovesPresenter.MovesListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.network.NetworkMonitor;
import com.manoeuvres.android.network.NetworkMonitor.NetworkListener;
import com.manoeuvres.android.timeline.logs.Log;
import com.manoeuvres.android.timeline.moves.Move;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.TextHelper;
import com.manoeuvres.android.util.UniqueId;
import com.manoeuvres.android.views.DividerItemDecoration;

public class TimelineFragment extends Fragment implements MovesListener, LogsListener, NetworkListener {

    private MovesPresenter mMovesPresenter;
    private LogsPresenter mLogsPresenter;
    private NetworkMonitor mNetworkMonitor;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private SharedPreferences mSharedPreferences;
    private MainActivity mMainActivity;
    private FloatingActionButton mFab;
    private NavigationMenu mNavigationMenu;
    private ProgressBar mProgressBar;
    private TextView mBackgroundTextView;
    private boolean mIsFriend;
    private Snackbar mNoConnectionSnackbar;
    private NotificationManager mNotificationManager;
    private String mCurrentUserId;
    private String mCurrentUserName;
    private boolean mIsRestarted;

    public TimelineFragment() {
        // Required empty public constructor
    }

    public static TimelineFragment newInstance(String currentUserId, String currentUserName) {
        TimelineFragment fragment = new TimelineFragment();

        Bundle args = new Bundle();
        args.putString(Constants.KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE, currentUserId);
        args.putString(Constants.KEY_ARGUMENTS_USER_NAME_FRAGMENT_TIMELINE, currentUserName);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mMainActivity = (MainActivity) context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                mMainActivity.getApplicationContext()
        );

        mNotificationManager = (NotificationManager) mMainActivity.getSystemService(
                Context.NOTIFICATION_SERVICE
        );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        Bundle bundle = getArguments();
        if (bundle != null) {
            mCurrentUserId = bundle.getString(
                    Constants.KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE
            );
            mCurrentUserName = bundle.getString(
                    Constants.KEY_ARGUMENTS_USER_NAME_FRAGMENT_TIMELINE
            );
            if (user != null && !mCurrentUserId.equals(user.getUid())) {
                mIsFriend = true;
            }
        } else {
            if (user != null) {
                mCurrentUserId = user.getUid();
                mCurrentUserName = user.getDisplayName();
            }
        }
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
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mMainActivity));

        mFab = (FloatingActionButton) mMainActivity.findViewById(R.id.fab);

        mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_timeline);
        mBackgroundTextView = (TextView) rootView.findViewById(R.id.textView_background_logs);

        NavigationView navigationView = (NavigationView) mMainActivity.findViewById(R.id.nav_view);
        mNavigationMenu = (NavigationMenu) navigationView.getMenu();

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        mMainActivity.setTitle(mCurrentUserName);
        MenuItem menuItem;
        if (!mIsFriend) {
            menuItem = mNavigationMenu.findItem(R.id.nav_timeline);
        } else {
            menuItem = mNavigationMenu.findItem(UniqueId.getMenuId(new Friend(mCurrentUserId)));
        }

        if (menuItem != null) {
            menuItem.setChecked(true);
        }

        mLogsPresenter = LogsPresenter.getInstance(mMainActivity.getApplicationContext());
        mLogsPresenter.addFriend(mCurrentUserId);
        mLogsPresenter.attach(this, mCurrentUserId);

        mMovesPresenter = MovesPresenter.getInstance(mMainActivity.getApplicationContext());
        mMovesPresenter.addFriend(mCurrentUserId);
        mMovesPresenter.attach(this, mCurrentUserId);
        mMovesPresenter.sync(mCurrentUserId);

        if (mMovesPresenter.isLoaded(mCurrentUserId)) {
            onCompleteMovesLoading(mCurrentUserId);
        }

        if (mLogsPresenter.isLoaded(mCurrentUserId)) {
            mAdapter.notifyDataSetChanged();
        }

        mNetworkMonitor = NetworkMonitor.getInstance(mMainActivity.getApplicationContext())
                .attach(this);
        if (mNetworkMonitor.isNetworkConnected()) {
            onNetworkConnected();
        } else {
            onNetworkDisconnected();
        }
    }

    @Override
    public void onStartMovesLoading(String userId) {
        if (mLogsPresenter.size(mCurrentUserId) == 0) {
            showProgress();
        }
    }

    @Override
    public void onMovesInitialization(String userId) {
        showProgress();
    }

    @Override
    public void onMoveAdded(String userId, String key, Move move) {

    }

    @Override
    public void onMoveChanged(String userId, String key, Move move) {

    }

    @Override
    public void onMoveRemoved(String userId, String key, Move move) {

    }

    @Override
    public void onCompleteMovesLoading(String userId) {
        mLogsPresenter.sync(mCurrentUserId);
    }

    @Override
    public void onStartLogsLoading(String userId) {

    }

    @Override
    public void onLogsInitialization(String userId) {

    }

    @Override
    public void onLogAdded(String userId, int index, Log log) {
        mAdapter.notifyItemInserted(0);
        mRecyclerView.scrollToPosition(0);
        mSharedPreferences.edit().putString(
                UniqueId.getLatestLogKey(mCurrentUserId),
                new Gson().toJson(mLogsPresenter.get(mCurrentUserId, "0"))
        ).apply();
    }

    @Override
    public void onLogChanged(String userId, int index, Log log) {
        mAdapter.notifyItemChanged(index);
        mSharedPreferences.edit().putString(
                UniqueId.getLatestLogKey(mCurrentUserId),
                new Gson().toJson(mLogsPresenter.get(mCurrentUserId, String.valueOf(index)))
        ).apply();
        mNotificationManager.cancel(UniqueId.getLogId(new Friend(mCurrentUserId)));
    }

    @Override
    public void onLogRemoved(String userId, int index, Log log) {

    }

    @Override
    public void onCompleteLogsLoading(String userId) {
        hideProgress();
        mNotificationManager.cancel(UniqueId.getLogId(new Friend(mCurrentUserId)));
        mSharedPreferences.edit().putString(
                UniqueId.getLatestLogKey(userId),
                new Gson().toJson(mLogsPresenter.get(userId, "0"))
        ).apply();
    }

    @Override
    public void onNetworkConnected() {
        if (mNoConnectionSnackbar != null && mNoConnectionSnackbar.isShown()) {
            mNoConnectionSnackbar.dismiss();
        }
        if (!mIsFriend) {
            mFab.show();
        }
    }

    @Override
    public void onNetworkDisconnected() {
        mFab.hide();
        mNoConnectionSnackbar = Snackbar.make(
                mRecyclerView, R.string.snackbar_no_internet, Snackbar.LENGTH_INDEFINITE
        );
        mNoConnectionSnackbar.show();
    }

    private void showProgress() {
        mRecyclerView.setVisibility(View.INVISIBLE);
        mFab.hide();
        if (!mIsRestarted) {
            mProgressBar.setVisibility(View.VISIBLE);
            String formatString = getString(R.string.textview_loading_logs);
            String nameArgument;
            if (mIsFriend) {
                nameArgument = mCurrentUserName + "'s";
            } else {
                nameArgument = getString(R.string.text_loading_logs_your);
            }
            mBackgroundTextView.setText(String.format(formatString, nameArgument));
            mBackgroundTextView.setVisibility(View.VISIBLE);
        } else if (mIsRestarted && (mLogsPresenter.size(mCurrentUserId) == 0)) {
            hideProgress();
        }
    }

    private void hideProgress() {
        if (mLogsPresenter.size(mCurrentUserId) > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mBackgroundTextView.setVisibility(View.INVISIBLE);
        } else {
            if (!mIsFriend) {
                mBackgroundTextView.setText(R.string.no_logs_user);
            } else {
                mBackgroundTextView.setText(String.format(
                        getString(R.string.no_logs_friend), mCurrentUserName)
                );
            }

            mBackgroundTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.INVISIBLE);
        }
        mProgressBar.setVisibility(View.INVISIBLE);
        if (mNetworkMonitor.isNetworkConnected() && !mIsFriend) {
            mFab.show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        /* Update the cache of logs and moves for this user. */
        if (!mMainActivity.isFinishing()) {
            Gson gson = new Gson();
            mSharedPreferences.edit()
                    .putString(
                            UniqueId.getLogsDataKey(mCurrentUserId),
                            gson.toJson(mLogsPresenter.getAll(mCurrentUserId))
                    ).putString(
                    UniqueId.getMovesDataKey(mCurrentUserId),
                    gson.toJson(mMovesPresenter.getAll(mCurrentUserId))
            ).apply();
        }

        mNetworkMonitor.detach(this);
        mMovesPresenter = (MovesPresenter) mMovesPresenter.detach(this, mCurrentUserId);
        mLogsPresenter = (LogsPresenter) mLogsPresenter.detach(this, mCurrentUserId);

        mIsRestarted = true;
    }

    public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

        @Override
        public TimelineAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.list_item_timeline, parent, false
            );
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(TimelineAdapter.ViewHolder holder, int position) {
            Log log = mLogsPresenter.get(mCurrentUserId, String.valueOf(position));
            Move move = mMovesPresenter.get(mCurrentUserId, log.getMoveId());

            /*
             * The text to be displayed depends on the status of the log. If it is in progress,
             * display the text in present tense, otherwise display it in past tense.
             */
            if (move != null) {
                if (log.getEndTime() != 0) {
                    holder.mMoveTitle.setText(move.getPast());
                    holder.mMoveSubtitle.setText(String.format(
                            getString(R.string.log_sub_title_text_past),
                            TextHelper.getDurationText(
                                    log.getStartTime(), log.getEndTime(), getResources()
                            )
                    ));
                } else {
                    holder.mMoveTitle.setText(move.getPresent());
                    holder.mMoveSubtitle.setText(String.format(
                            getString(R.string.log_sub_title_text_present),
                            TextHelper.getDurationText(
                                    log.getStartTime(), log.getEndTime(), getResources()
                            )
                    ));
                }
            }
        }

        @Override
        public int getItemCount() {
            return mLogsPresenter.size(mCurrentUserId);
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
