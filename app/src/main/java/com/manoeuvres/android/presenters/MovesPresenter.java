package com.manoeuvres.android.presenters;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manoeuvres.android.models.Move;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.util.UniqueId;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MovesPresenter {
    private static MovesPresenter ourInstance;

    /*
     * A log is bound to a move with its moveId. A moveId is the push id of the move.
     * The map stores the push id of the move as the key to the move.
     */
    private Map<String, Map<String, Move>> mMoves;

    private Map<String, DatabaseReference> mCountReferences;
    private Map<String, DatabaseReference> mDataReferences;

    private Map<String, ValueEventListener> mCountListeners;
    private Map<String, ChildEventListener> mDataListeners;

    private Map<String, MovesListener[]> mObservers;

    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    private Map<String, Boolean> mIsLoaded;

    private MovesPresenter(Context applicationContext) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        mGson = new Gson();

        int capacity = Constants.INITIAL_COLLECTION_CAPACITY_FOLLOWING + 1;
        mCountReferences = new HashMap<>(capacity);
        mCountListeners = new HashMap<>(capacity);
        mDataReferences = new HashMap<>(capacity);
        mDataListeners = new HashMap<>(capacity);
        mObservers = new HashMap<>(capacity);
        mMoves = new HashMap<>(capacity);
        mIsLoaded = new HashMap<>(capacity);
    }

    public static MovesPresenter getInstance(Context applicationContext) {
        if (ourInstance == null) ourInstance = new MovesPresenter(applicationContext);
        return ourInstance;
    }

    public MovesPresenter attach(Object component, String userId) {
        MovesListener listener = (MovesListener) component;
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            /* If the observer is already attached, return. */
            for (MovesListener observer : listeners)
                if (observer != null && observer.equals(listener)) return ourInstance;

            /* Insert the observer at the first available slot. */
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] == null) {
                    listeners[i] = listener;
                    return ourInstance;
                }
            }
        } else {
            listeners = new MovesListener[Constants.MAX_MOVES_LISTENERS_COUNT];
            listeners[0] = listener;
            mObservers.put(userId, listeners);
        }
        return ourInstance;
    }

    public MovesPresenter detach(Object component, String userId) {
        MovesListener listener = (MovesListener) component;
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {

            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] != null && listeners[i].equals(listener)) listeners[i] = null;

            /* If there is at least one observer attached, return. */
            for (int i = 0; i < listeners.length; i++)
                if (listeners[i] != null) return ourInstance;

            mObservers.remove(userId);
            stopSync(userId);

            /* If there are no observers, free the memory for garbage collection. */
            if (mObservers.size() == 0) ourInstance = null;
            else {
                for (MovesListener[] listenerArray : mObservers.values()) {
                    for (MovesListener observer : listenerArray) {
                        if (observer != null) return ourInstance;
                    }
                }
                ourInstance = null;
            }
        }
        return ourInstance;
    }

    public MovesPresenter addFriend(String userId) {

        /*
         * Caching: Load the moves associated to the current user from the shared preferences file.
         * Update the list when network can be accessed.
         */
        Map<String, Move> moves = mMoves.get(userId);
        if (moves == null || moves.size() == 0) {
            String movesList = mSharedPreferences.getString(UniqueId.getMovesDataKey(userId), "");
            Type type = new TypeToken<Map<String, Move>>() {
            }.getType();
            moves = mGson.fromJson(movesList, type);
            if (moves == null) moves = new HashMap<>();

            mMoves.put(userId, moves);
        }

        DatabaseReference countReference = mCountReferences.get(userId);
        if (countReference == null) mCountReferences.put(userId
                , DatabaseHelper.mMetaMovesReference.child(userId).child(Constants.FIREBASE_DATABASE_REFERENCE_META_MOVES_COUNT));
        DatabaseReference dataReference = mDataReferences.get(userId);
        if (dataReference == null) mDataReferences.put(userId
                , DatabaseHelper.mMovesReference.child(userId));

        return ourInstance;
    }

    public MovesPresenter removeFriend(String userId) {
        removeListeners(userId);
        if (mCountReferences.containsKey(userId)) mCountReferences.remove(userId);
        if (mDataReferences.containsKey(userId)) mDataReferences.remove(userId);
        if (mMoves.containsKey(userId)) mMoves.remove(userId);
        if (mObservers.containsKey(userId)) mObservers.remove(userId);
        mSharedPreferences.edit().remove(UniqueId.getMovesDataKey(userId)).apply();
        return ourInstance;
    }

    private void setListeners(final String userId) {
        ValueEventListener movesCountListener = mCountListeners.get(userId);
        if (movesCountListener == null) {
            notifyObservers(userId, Constants.CALLBACK_START_LOADING);

            final Map<String, Move> moves = mMoves.get(userId);
            /* If there is no cache, display progress until the data is loaded from the network. */
            if (moves != null && moves.size() == 0)
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
                                for (String moveId : moves.keySet()) {
                                    moves.remove(moveId);
                                }
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
                            final Map<String, Move> updatedMoves = new HashMap<>();

                            ChildEventListener movesListener = mDataListeners.get(userId);
                            if (movesListener == null) {
                                DatabaseReference dataReference = mDataReferences.get(userId);
                                if (dataReference != null) {
                                    movesListener = mDataReferences.get(userId).addChildEventListener(new ChildEventListener() {
                                        @Override
                                        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                            String key = dataSnapshot.getKey();
                                            Move newMove = dataSnapshot.getValue(Move.class);
                                            Map<String, Move> moves;
                                            if (!mMoves.containsKey(userId)) {
                                                moves = new HashMap<>();
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
                                                    Map<String, Move> removedMoves = new HashMap<>(moves);
                                                    for (String moveId : updatedMoves.keySet()) {
                                                        removedMoves.remove(moveId);
                                                    }
                                                    for (String moveId : removedMoves.keySet()) {
                                                        Move removedMove = new Move(moves.get(moveId));
                                                        moves.remove(moveId);
                                                        notifyObservers(userId, Constants.CALLBACK_REMOVE_DATA, moveId, removedMove);
                                                    }
                                                }
                                                notifyObservers(userId, Constants.CALLBACK_COMPLETE_LOADING);
                                            }
                                        }

                                        @Override
                                        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                                            String key = dataSnapshot.getKey();
                                            Move updatedMove = dataSnapshot.getValue(Move.class);
                                            Map<String, Move> moves = mMoves.get(userId);
                                            if (moves != null) {
                                                moves.put(key, updatedMove);
                                                notifyObservers(userId, Constants.CALLBACK_CHANGE_DATA, key, updatedMove);
                                            }
                                        }

                                        @Override
                                        public void onChildRemoved(DataSnapshot dataSnapshot) {
                                            String key = dataSnapshot.getKey();
                                            Map<String, Move> moves = mMoves.get(userId);
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
    }

    private void removeListeners(String userId) {
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
    }

    public MovesPresenter sync() {
        for (String userId : mCountReferences.keySet()) {
            setListeners(userId);
        }
        return ourInstance;
    }

    public MovesPresenter sync(String userId) {
        setListeners(userId);
        return ourInstance;
    }

    public MovesPresenter stopSync() {
        for (String userId : mCountReferences.keySet()) {
            removeListeners(userId);
        }
        return ourInstance;
    }

    public MovesPresenter stopSync(String userId) {
        removeListeners(userId);
        return ourInstance;
    }

    public Move get(String userId, String moveId) {
        Map<String, Move> moves = mMoves.get(userId);
        if (moves != null) return moves.get(moveId);
        return null;
    }

    public Map<String, Move> getAll(String userId) {
        return mMoves.get(userId);
    }

    public String getKey(String userID, Move move) {
        Map<String, Move> moves = mMoves.get(userID);
        if (moves != null) {
            for (Map.Entry<String, Move> entry : moves.entrySet()) {
                if (entry.getValue().equals(move)) return entry.getKey();
            }
        }
        return null;
    }

    public Boolean isLoaded(String userId) {
        Boolean isLoaded = mIsLoaded.get(userId);
        if (isLoaded != null) return isLoaded;
        else return false;
    }

    private void notifyObservers(String userId, String event, String key, Move move) {
        MovesListener[] listeners = mObservers.get(userId);
        if (listeners != null) {
            for (MovesListener listener : listeners) {
                if (listener != null) {
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
    }

    private void notifyObservers(String userId, String event) {
        notifyObservers(userId, event, null, null);
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