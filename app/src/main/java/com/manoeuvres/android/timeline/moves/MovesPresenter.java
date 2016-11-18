package com.manoeuvres.android.timeline.moves;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.timeline.AbstractTimelinePresenter;
import com.manoeuvres.android.timeline.logs.LogsDatabaseHelper;
import com.manoeuvres.android.database.CompletionListeners.GetStringListener;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.lang.reflect.Type;
import java.util.Set;

public class MovesPresenter extends AbstractTimelinePresenter {

    private static MovesPresenter ourInstance;

    /*
     * A log is bound to a move with its moveId. A moveId is the push id of the move.
     * The map stores the push id of the move as the key to the move.
     */
    private SimpleArrayMap<String, ArrayMap<String, Move>> mMoves;

    private MovesPresenter(Context applicationContext) {
        super(applicationContext);

        mMoves = new SimpleArrayMap<>(Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1);
    }

    public static MovesPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) {
            ourInstance = new MovesPresenter(applicationContext);
        }
        return ourInstance;
    }

    public void loadMove(Friend friend, String moveId,
                         boolean inProgress, final GetStringListener listener) {
        MovesDatabaseHelper.loadMove(friend, moveId, inProgress, listener);
    }

    void pushMove(String moveId) {
        LogsDatabaseHelper.pushLog(moveId);
    }

    @Override
    protected boolean hasData(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        return (moves != null && moves.size() > 0);
    }

    @Override
    protected void clearDataForUser(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        clearMovesInMapHavingKeys(userId, moves, moves.keySet());
    }

    @Override
    protected int getDataLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected Object getNewCollection(String userId) {
        return new ArrayMap<String, Move>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void addDataToCachedAndUpdatedCollections(String userId,
                                                        DataSnapshot dataSnapshot,
                                                        Object updatedCollection) {
        String key = dataSnapshot.getKey();
        Move newMove = dataSnapshot.getValue(Move.class);
        ArrayMap<String, Move> moves;
        if (!mMoves.containsKey(userId)) {
            moves = new ArrayMap<>();
            moves.put(key, newMove);
            mMoves.put(userId, moves);
        } else {
            moves = mMoves.get(userId);
            if (!moves.containsKey(key)) {
                moves.put(key, newMove);
            }
        }

        ArrayMap<String, Move> updatedMoves = (ArrayMap<String, Move>) updatedCollection;
        updatedMoves.put(key, newMove);
        notifyObservers(userId, Constants.CALLBACK_ADD_DATA, key, newMove);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected int getCollectionSize(Object collection) {
        ArrayMap<String, Move> moves = (ArrayMap<String, Move>) collection;
        return moves.size();
    }

    @Override
    protected int getUserCollectionSize(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves != null) {
            return moves.size();
        } else {
            return 0;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void removeRedundantDataFromUserCollection(String userId,
                                                         Object updatedCollectionObject) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        ArrayMap<String, Move> removedMoves = new ArrayMap<>(moves);
        ArrayMap<String, Move> updatedCollection = (ArrayMap<String, Move>) updatedCollectionObject;
        removedMoves.removeAll(updatedCollection.keySet());
        clearMovesInMapHavingKeys(userId, moves, removedMoves.keySet());
    }

    @Override
    protected void updateData(String userId, DataSnapshot dataSnapshot) {
        String key = dataSnapshot.getKey();
        Move updatedMove = dataSnapshot.getValue(Move.class);
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves != null) {
            moves.put(key, updatedMove);
            notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, key, updatedMove);
        }
    }

    @Override
    protected void removeData(String userId, DataSnapshot dataSnapshot) {
        String key = dataSnapshot.getKey();
        ArrayMap<String, Move> moves = mMoves.get(userId);
        removeMoveInThisMapHavingThisKey(userId, key, moves);
    }

    @Override
    protected void removeCollectionForUser(String userId) {
        if (mMoves.containsKey(userId)) {
            mMoves.remove(userId);
        }
    }

    @Override
    protected String getCacheKey(String userId) {
        return UniqueId.getMovesDataKey(userId);
    }

    @Override
    protected void loadCache(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves == null || moves.size() == 0) {
            String movesList = super.getSharedPreferences().getString(
                    UniqueId.getMovesDataKey(userId), ""
            );
            Type type = new TypeToken<ArrayMap<String, Move>>() {
            }.getType();
            moves = new Gson().fromJson(movesList, type);
            if (moves == null) {
                moves = new ArrayMap<>();
            }

            mMoves.put(userId, moves);
        }
    }

    @Override
    protected DatabaseReference getCountReference(String userId) {
        return MovesDatabaseHelper.getMovesCountReference(userId);
    }

    @Override
    protected DatabaseReference getDataReference(String userId) {
        return MovesDatabaseHelper.getMovesDataReference(userId);
    }

    @Override
    protected void destroy() {
        ourInstance = null;
    }

    @Override
    protected int getMaxListenersCount() {
        return Constants.MAX_MOVES_LISTENERS_COUNT;
    }

    private void clearMovesInMapHavingKeys(String userId,
                                           ArrayMap<String, Move> moves,
                                           Set<String> keys) {
        if (keys.size() > 0) {
            for (String moveId : keys) {
                removeMoveInThisMapHavingThisKey(userId, moveId, moves);
            }
        }
    }

    private void removeMoveInThisMapHavingThisKey(String userId,
                                                  String key,
                                                  ArrayMap<String, Move> moves) {
        if (moves != null) {
            if (moves.containsKey(key)) {
                Move removedMove = new Move(moves.get(key));
                moves.remove(key);
                notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, key, removedMove);
            }
        }
    }

    @Override
    protected void notifyCompleteLoading(String userId, Object listenerObject) {
        MovesListener listener = (MovesListener) listenerObject;
        listener.onCompleteMovesLoading(userId);
    }

    @Override
    protected void notifyRemoveData(String userId, Object listenerObject,
                                    Object keyObject, Object dataObject) {
        MovesListener listener = (MovesListener) listenerObject;
        String key = (String) keyObject;
        Move move = (Move) dataObject;
        listener.onMoveRemoved(userId, key, move);
    }

    @Override
    protected void notifyChangeData(String userId, Object listenerObject,
                                    Object keyObject, Object dataObject) {
        MovesListener listener = (MovesListener) listenerObject;
        String key = (String) keyObject;
        Move move = (Move) dataObject;
        listener.onMoveChanged(userId, key, move);
    }

    @Override
    protected void notifyAddData(String userId, Object listenerObject,
                                 Object keyObject, Object dataObject) {
        MovesListener listener = (MovesListener) listenerObject;
        String key = (String) keyObject;
        Move move = (Move) dataObject;
        listener.onMoveAdded(userId, key, move);
    }

    @Override
    protected void notifyInitialLoading(String userId, Object listenerObject) {
        MovesListener listener = (MovesListener) listenerObject;
        listener.onMovesInitialization(userId);
    }

    @Override
    protected void notifyStartLoading(String userId, Object listenerObject) {
        MovesListener listener = (MovesListener) listenerObject;
        listener.onStartMovesLoading(userId);
    }

    @Override
    public Move get(String userId, String moveId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves != null) return moves.get(moveId);
        return null;
    }

    @Override
    public ArrayMap<String, Move> getAll(String userId) {
        return mMoves.get(userId);
    }

    @Override
    public int size(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves != null) {
            return moves.size();
        }
        return 0;
    }

    String getKey(String userID, Move move) {
        ArrayMap<String, Move> moves = mMoves.get(userID);
        if (moves != null && moves.size() > 0) {
            Set<ArrayMap.Entry<String, Move>> moveEntrySet = moves.entrySet();
            if (moveEntrySet.size() > 0) {
                for (ArrayMap.Entry<String, Move> entry : moveEntrySet) {
                    if (entry.getValue().equals(move)) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    public interface MovesListener {
        void onStartMovesLoading(String userId);

        void onMovesInitialization(String userId);

        void onMoveAdded(String userId, String key, Move move);

        void onMoveChanged(String userId, String key, Move move);

        void onMoveRemoved(String userId, String key, Move move);

        void onCompleteMovesLoading(String userId);
    }
}
