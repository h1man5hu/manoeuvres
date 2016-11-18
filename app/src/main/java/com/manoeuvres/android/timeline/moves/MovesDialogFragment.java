package com.manoeuvres.android.timeline.moves;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.manoeuvres.android.R;
import com.manoeuvres.android.views.DividerItemDecoration;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MovesDialogFragment extends DialogFragment {

    @BindView(R.id.recycler_view_moves)
    RecyclerView mRecyclerView;
    private DividerItemDecoration mItemDecoration;
    private MovesPresenter mMovesPresenter;
    private String mUserId;
    private Object[] moves;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mItemDecoration = new DividerItemDecoration(context);
        mMovesPresenter = MovesPresenter.getInstance(context.getApplicationContext());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            mUserId = user.getUid();
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_dialog_moves, container, false);
        ButterKnife.bind(this, rootView);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        MovesAdapter adapter = new MovesAdapter();
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.addItemDecoration(mItemDecoration);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        moves = mMovesPresenter.getAll(mUserId).values().toArray();
    }

    public class MovesAdapter extends RecyclerView.Adapter<MovesAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.list_item_move_selector_title, parent, false
            );
            return (new ViewHolder(v));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Move move = (Move) moves[position];
            holder.mTitleTextView.setText(move.getName());
        }

        @Override
        public int getItemCount() {
            return moves.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            @BindView(R.id.title_move_dialog_fragment)
            TextView mTitleTextView;

            ViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);
            }

            @OnClick
            void onRowClicked() {
                Move move = (Move) moves[getAdapterPosition()];
                String moveId = mMovesPresenter.getKey(mUserId, move);
                if (moveId != null) {
                    mMovesPresenter.pushMove(moveId);
                    dismiss();
                }
            }
        }
    }
}
