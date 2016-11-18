package com.manoeuvres.android.database;

import com.facebook.GraphResponse;
import com.manoeuvres.android.friends.Friend;

import org.json.JSONObject;

public class CompletionListeners {

    public interface OnCompleteListener {
        void onComplete();

        void onFailed();
    }

    public interface GetIntListener {
        void onComplete(int integer);

        void onFailed();
    }

    public interface GetStringListener {
        void onComplete(String string);

        void onFailed();
    }

    public interface GetBooleanListener {
        void onComplete(boolean bool);

        void onFailed();
    }

    public interface GraphRequestListener {
        void onComplete(JSONObject object, GraphResponse response);

        void onFailed();
    }

    public interface GetFriendListener {
        void onComplete(Friend friend);

        void onFailed();
    }
}
