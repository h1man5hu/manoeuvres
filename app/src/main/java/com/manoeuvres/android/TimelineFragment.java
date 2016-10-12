package com.manoeuvres.android;


import android.os.Bundle;

//v4-Support
import android.support.v4.app.Fragment;

//v7-Support
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

//Views
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

//Widgets
import android.widget.TextView;

//Firebase authentication
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

//Firebase database
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

//Models
import com.manoeuvres.android.models.Log;
import com.manoeuvres.android.models.Move;
import com.manoeuvres.android.util.Constants;

//Collections
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TimelineFragment extends Fragment {

    //RecyclerView.
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    //Firebase database.
    private DatabaseReference mLogsReference;
    private DatabaseReference mMovesReference;
    private DatabaseReference mRootReference;
    private FirebaseUser mUser;

    //Current user in this fragment can be the signed-in user
    //or one of the friends.
    private String mCurrentUserId;

    private Map<String, Move> mMoves;   //Used while displaying the moves in a dialog box.
    private List<com.manoeuvres.android.models.Log> mLogs;  //Used while displaying logs in the recycler view.

    //Will be used to allow customization to the current log if
    //the signed-in user is the current user.
    private boolean isFriend;

    public TimelineFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_timeline);

        //To improve performance if changes in content do not change the layout size of the RecyclerView.
        mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new TimelineAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mMoves = new HashMap<>();
        mLogs = new ArrayList<>();

        //Get the current user from the fragment's arguments.
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        if (getArguments() != null) {
            mCurrentUserId = getArguments().getString(Constants.KEY_ARGUMENTS_FIREBASE_ID_USER_FRAGMENT_TIMELINE_);
            isFriend = true;
        } else {
            try {
                mCurrentUserId = mUser.getUid();
                isFriend = false;
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        //Get the database references for the current user.
        mRootReference = FirebaseDatabase.getInstance().getReference();
        mLogsReference = mRootReference.getRef().child(Constants.FIREBASE_DATABASE_REFERENCE_LOGS).child(mCurrentUserId);
        mMovesReference = mRootReference.getRef().child(Constants.FIREBASE_DATABASE_REFERENCE_MOVES).child(mCurrentUserId);

        //Get all the logs.
        mLogsReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mLogs.clear();
                for (DataSnapshot log : dataSnapshot.getChildren()) {
                    mLogs.add(log.getValue(com.manoeuvres.android.models.Log.class));
                }
                Collections.reverse(mLogs);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        //Get all the moves.
        mMovesReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mMoves.clear();
                for (DataSnapshot move : dataSnapshot.getChildren()) {
                    mMoves.put(move.getKey(), move.getValue(Move.class));
                }

                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        return rootView;
    }

    public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

        @Override
        public TimelineAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_cardview_timeline, parent, false);
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(TimelineAdapter.ViewHolder holder, int position) {
            //Get the log to be displayed.
            com.manoeuvres.android.models.Log log = mLogs.get(position);

            //Get the move details from the move id stored in the log.
            Move move = mMoves.get(log.getMoveId());

            //Error handling.
            if (move != null) {

                //If the move is done, display it in past tense.
                if (log.getEndTime() != 0) {
                    holder.mMoveTitle.setText(move.getPast());
                    holder.mMoveSubtitle.setText(String.format(getResources().getString(R.string.log_sub_title_text_past), log.getStartTime(), log.getEndTime()));
                }

                //If the move is in progress, display it in present tense.
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
