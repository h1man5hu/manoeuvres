package com.manoeuvres.android.timeline.moves;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.timeline.TimelinePresenter;
import com.manoeuvres.android.timeline.logs.LogsDatabaseHelper;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

public class MovesPresenter implements TimelinePresenter {
    private static MovesPresenter ourInstance;

    /*
     * A log is bound to a move with its moveId. A moveId is the push id of the move.
     * The map stores the push id of the move as the key to the move.
     */
    private SimpleArrayMap<String, ArrayMap<String, Move>> mMoves;

    private ArrayMap<String, DatabaseReference> mCountReferences;
    private SimpleArrayMap<String, DatabaseReference> mDataReferences;

    private SimpleArrayMap<String, ValueEventListener> mCountListeners;
    private SimpleArrayMap<String, ChildEventListener> mDataListeners;

    private ArrayMap<String, MovesListener[]> mObservers;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private SimpleArrayMap<String, Boolean> mIsLoaded;

    private MovesPresenter(Context applicationContext) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        mGson = new Gson();

        int capacity = Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1;
        mCountReferences = new ArrayMap<>(capacity);
        mCountListeners = new SimpleArrayMap<>(capacity);
        mDataReferences = new SimpleArrayMap<>(capacity);
        mDataListeners = new SimpleArrayMap<>(capacity);
        mObservers = new ArrayMap<>(capacity);
        mMoves = new SimpleArrayMap<>(capacity);
        mIsLoaded = new SimpleArrayMap<>(capacity);
    }

    public static MovesPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) ourInstance = new MovesPresenter(applicationContext);
        return ourInstance;
    }

    @Override
    public MovesPresenter attach(Object component, String userId) {
        MovesListener listener = (MovesListener) component;
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            /* If the observer is already attached, return. */
            for (int i = 0; i < listeners.length; i++) {
                MovesListener observer = listeners[i];
                if (observer != null && observer.equals(listener)) return ourInstance;
            }

            /* Insert the observer at the first available slot. */
            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] == null) {
                    listeners[i] = listener;
                    return ourInstance;
                }
        } else {
            listeners = new MovesListener[Constants.MAX_MOVES_LISTENERS_COUNT];
            listeners[0] = listener;
            mObservers.put(userId, listeners);
        }
        return ourInstance;
    }

    @Override
    public MovesPresenter detach(Object component, String userId) {
        MovesListener listener = (MovesListener) component;
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] != null && listeners[i].equals(listener)) listeners[i] = null;

            /* If there is at least one observer attached, return. */
            for (int i = 0; i < listeners.length; i++) if (listeners[i] != null) return ourInstance;

            mObservers.remove(userId);
            stopSync(userId);

            /* If there are no observers, free the memory for garbage collection. */
            if (mObservers.size() == 0) ourInstance = null;
            else {
                Collection<MovesListener[]> listenerArrays = mObservers.values();
                if (listenerArrays.size() > 0)
                    for (MovesListener[] listenerArray : listenerArrays)
                        for (int i = 0; i < listenerArray.length; i++) {
                            MovesListener observer = listenerArray[i];
                            if (observer != null) return ourInstance;
                        }
                ourInstance = null;
            }
        }
        return ourInstance;
    }

    @Override
    public MovesPresenter addFriend(String userId) {

        /*
         * Caching: Load the moves associated to the current user from the shared preferences file.
         * Update the list when network can be accessed.
         */
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves == null || moves.size() == 0) {
            String movesList = mSharedPreferences.getString(UniqueId.getMovesDataKey(userId), "");
            Type type = new TypeToken<ArrayMap<String, Move>>() {
            }.getType();
            moves = mGson.fromJson(movesList, type);
            if (moves == null) moves = new ArrayMap<>();

            mMoves.put(userId, moves);
        }

        DatabaseReference countReference = mCountReferences.get(userId);
        if (countReference == null) mCountReferences.put(userId
                , MovesDatabaseHelper.getMovesCountReference(userId));
        DatabaseReference dataReference = mDataReferences.get(userId);
        if (dataReference == null) mDataReferences.put(userId
                , MovesDatabaseHelper.getMovesDataReference(userId));

        return ourInstance;
    }

    @Override
    public MovesPresenter removeFriend(String userId) {
        stopSync(userId);
        if (mCountReferences.containsKey(userId)) mCountReferences.remove(userId);
        if (mDataReferences.containsKey(userId)) mDataReferences.remove(userId);
        if (mMoves.containsKey(userId)) mMoves.remove(userId);
        if (mObservers.containsKey(userId)) mObservers.remove(userId);
        mSharedPreferences.edit().remove(UniqueId.getMovesDataKey(userId)).apply();
        return ourInstance;
    }

    @Override
    public MovesPresenter sync(final String userId) {
        ValueEventListener movesCountListener = mCountListeners.get(userId);
        if (movesCountListener == null) {
            notifyObservers(userId, Constants.CALLBACK_START_LOADING);

            final ArrayMap<String, Move> moves = mMoves.get(userId);
            /* If there is no cache, display progress until the data is loaded from the network. */
            if (moves == null || moves.size() == 0)
                notifyObservers(userId, Constants.CALLBACK_INITIAL_LOADING);

            DatabaseReference countReference = mCountReferences.get(userId);
            if (countReference != null) {
                movesCountListener = mCountReferences.get(userId).addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        final int count = Integer.valueOf(dataSnapshot.getValue().toString());

                        /*
                         * If the count on the Firebase database is zero but there are moves in the cached list,
                         * remove all of them.
                         */
                        if (count == 0) {
                            if (moves != null && moves.size() > 0) {
                                Set<String> keys = moves.keySet();
                                if (keys.size() > 0)
                                    for (String moveId : keys) moves.remove(moveId);
                                notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                            }
                        } else if (count > 0) {

                            /*
                             * In case not all but some of the moves were removed, this list will
                             * be subtracted from the cached list to get the moves which were removed.
                             * Each move on the Firebase database is added to this list.
                             * The subtraction will be done when all the moves have been retrieved from the
                             * Firebase database.
                             */
                            final ArrayMap<String, Move> updatedMoves = new ArrayMap<>();

                            ChildEventListener movesListener = mDataListeners.get(userId);
                            if (movesListener == null) {
                                DatabaseReference dataReference = mDataReferences.get(userId);
                                if (dataReference != null) {
                                    movesListener = mDataReferences.get(userId).addChildEventListener(new ChildEventListener() {
                                        @Override
                                        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
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

                                            updatedMoves.put(key, newMove);
                                            notifyObservers(userId, Constants.CALLBACK_ADD_DATA, key, newMove);

                                           /*
                                            * All the moves have been retrieved, subtract the list and notify
                                            * removed moves to listeners, if any.
                                            */
                                            if (updatedMoves.size() == count) {
                                                if (moves.size() > count) {
                                                    ArrayMap<String, Move> removedMoves = new ArrayMap<>(moves);
                                                    Set<String> keys = updatedMoves.keySet();
                                                    if (keys.size() > 0) for (String moveId : keys)
                                                        removedMoves.remove(moveId);

                                                    keys = removedMoves.keySet();
                                                    if (keys.size() > 0) {
                                                        Move removedMove = new Move();
                                                        for (String moveId : keys) {
                                                            Move move = moves.get(moveId);
                                                            removedMove.setName(move.getName());
                                                            removedMove.setPresent(move.getPresent());
                                                            removedMove.setPast(move.getPast());
                                                            moves.remove(moveId);
                                                            notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, moveId, removedMove);
                                                        }
                                                    }
                                                }
                                                notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                                            }
                                        }

                                        @Override
                                        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                            String key = dataSnapshot.getKey();
                                            Move updatedMove = dataSnapshot.getValue(Move.class);
                                            ArrayMap<String, Move> moves = mMoves.get(userId);
                                            if (moves != null) {
                                                moves.put(key, updatedMove);
                                                notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, key, updatedMove);
                                            }
                                        }

                                        @Override
                                        public void onChildRemoved(DataSnapshot dataSnapshot) {
                                            String key = dataSnapshot.getKey();
                                            ArrayMap<String, Move> moves = mMoves.get(userId);
                                            if (moves != null) {
                                                if (moves.containsKey(key)) {
                                                    Move removedMove = new Move(moves.get(key));
                                                    moves.remove(key);
                                                    notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, key, removedMove);
                                                }
                                            }
                                        }

                                        @Override
                                        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                    mDataListeners.put(userId, movesListener);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                mCountListeners.put(userId, movesCountListener);
            }
        }
        return ourInstance;
    }

    @Override
    public MovesPresenter stopSync(String userId) {
        DatabaseReference countReference = mCountReferences.get(userId);
        DatabaseReference dataReference = mDataReferences.get(userId);
        ValueEventListener countListener = mCountListeners.get(userId);
        ChildEventListener dataListener = mDataListeners.get(userId);

        if (countReference != null && countListener != null) {
            countReference.removeEventListener(countListener);
            mCountListeners.put(userId, null);
        }
        if (dataReference != null && dataListener != null) {
            dataReference.removeEventListener(dataListener);
            mDataListeners.put(userId, null);
        }

        return ourInstance;
    }

    @Override
    public MovesPresenter sync() {
        Set<String> keys = mCountReferences.keySet();
        if (keys.size() > 0) for (String userId : keys) sync(userId);
        return ourInstance;
    }

    @Override
    public MovesPresenter stopSync() {
        Set<String> keys = mCountReferences.keySet();
        if (keys.size() > 0) for (String userId : keys) stopSync(userId);
        return ourInstance;
    }

    @Override
    public int size(String userId) {
        ArrayMap<String, Move> moves = mMoves.get(userId);
        if (moves != null) return moves.size();
        return 0;
    }

    @Override
    public Boolean isLoaded(String userId) {
        Boolean isLoaded = mIsLoaded.get(userId);
        if (isLoaded != null) return isLoaded;
        else return false;
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

    String getKey(String userID, Move move) {
        ArrayMap<String, Move> moves = mMoves.get(userID);
        if (moves != null && moves.size() > 0) {
            Set<ArrayMap.Entry<String, Move>> moveEntrySet = moves.entrySet();
            if (moveEntrySet.size() > 0) for (ArrayMap.Entry<String, Move> entry : moveEntrySet)
                if (entry.getValue().equals(move)) return entry.getKey();
        }
        return null;
    }

    private void notifyObservers(String userId, String event, String key, Move move) {
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                MovesListener listener = listeners[i];
                if (listener != null)
                    switch (event) {
                        case Constants.CALLBACK_START_LOADING:
                            listener.onStartMovesLoading(userId);
                            break;
                        case Constants.CALLBACK_INITIAL_LOADING:
                            listener.onMovesInitialization(userId);
                            break;
                        case Constants.CALLBACK_ADD_DATA:
                            listener.onMoveAdded(userId, key, move);
                            break;
                        case Constants.CALLBACK_CHANGE_DATA:
                            listener.onMoveChanged(userId, key, move);
                            break;
                        case Constants.CALLBACK_REMOVE_DATA:
                            listener.onMoveRemoved(userId, key, move);
                            break;
                        case Constants.CALLBACK_COMPLETE_LOADING:
                            mIsLoaded.put(userId, true);
                            listener.onCompleteMovesLoading(userId);
                            break;
                    }
            }
        }
    }

    private void notifyObservers(String userId, String event) {
        notifyObservers(userId, event, null, null);
    }

    public void loadMove(Friend friend, String moveId, boolean inProgress, final LoadMoveListener listener) {
        MovesDatabaseHelper.loadMove(friend, moveId, inProgress, listener);
    }

    void pushMove(String moveId) {
        LogsDatabaseHelper.pushLog(moveId);
    }

    public interface MovesListener {
        void onStartMovesLoading(String userId);

        void onMovesInitialization(String userId);

        void onMoveAdded(String userId, String key, Move move);

        void onMoveChanged(String userId, String key, Move move);

        void onMoveRemoved(String userId, String key, Move move);

        void onCompleteMovesLoading(String userId);
    }

    public interface LoadMoveListener {
        void onMoveLoaded(String text);

        void onFailed();
    }
}
