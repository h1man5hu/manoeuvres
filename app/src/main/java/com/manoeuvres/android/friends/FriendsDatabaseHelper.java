package com.manoeuvres.android.friends;

import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.List;

public class FriendsDatabaseHelper {

    public static void pushListOfFriends(List list, DatabaseReference reference) {
        List<String> firebaseIds = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Friend friend = (Friend) list.get(i);
            firebaseIds.add(friend.getFirebaseId());
        }
        reference.setValue(firebaseIds);
    }

}
