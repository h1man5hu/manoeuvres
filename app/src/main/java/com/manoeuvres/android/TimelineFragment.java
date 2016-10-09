package com.manoeuvres.android;


import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.manoeuvres.android.models.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TimelineFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    private DatabaseReference mLogsReference;
    private DatabaseReference mMovesReference;
    private DatabaseReference mRoot;
    private FirebaseUser mUser;

    private Map<String, Move> mMoves;
    private List<com.manoeuvres.android.models.Log> mLogs;


    public TimelineFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        //Display the floating action button.
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.show();

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view_timeline);

        //To improve performance if changes in content do not change the layout size of the RecyclerView.
        mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new TimelineAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mMoves = new HashMap<>();
        mLogs = new ArrayList<>();

        mRoot = FirebaseDatabase.getInstance().getReference();
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        mLogsReference = mRoot.getRef().child("logs").child(mUser.getUid());
        mMovesReference = mRoot.getRef().child("moves").child(mUser.getUid());

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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_timeline, parent, false);
            ViewHolder viewHolder = new ViewHolder(v);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(TimelineAdapter.ViewHolder holder, int position) {
            //Get the log to be displayed.
            com.manoeuvres.android.models.Log log = mLogs.get(position);

            //Get the move details from the move id stored in the log.
            Move move = mMoves.get(log.getMoveId());

            //Error handling.
            if (move != null) {

                //If the move is in progress, display it in present tense.
                if (log.getEndTime() != 0) {
                    holder.mMoveTitle.setText(move.getPast());
                    holder.mMoveSubtitle.setText("From " + log.getStartTime() + " to " + log.getEndTime());
                }

                //If the move is done, display it in paste tense.
                else {
                    holder.mMoveTitle.setText(move.getPresent());
                    holder.mMoveSubtitle.setText("Since " + log.getStartTime());
                }
            }
        }

        @Override
        public int getItemCount() {
            return mLogs.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView mMoveTitle;
            public TextView mMoveSubtitle;

            public ViewHolder(View view) {
                super(view);
                mMoveTitle = (TextView) view.findViewById(R.id.move_text);
                mMoveSubtitle = (TextView) view.findViewById(R.id.move_subtext);
            }
        }
    }
}
