package com.manoeuvres.android.util;


import android.content.res.Resources;

import com.manoeuvres.android.R;

public class TextHelper {

    public static String getDurationText(long startTime, long endTime, Resources resources) {
        if (endTime == 0) endTime = System.currentTimeMillis();
        long difference = endTime - startTime;
        long millisInADay = 24 * 60 * 60 * 1000;
        long days = difference / (millisInADay);
        long millisLeft = difference - (days * millisInADay);
        long millisInAnHour = 60 * 60 * 1000;
        long hours = millisLeft / (millisInAnHour);
        millisLeft = millisLeft - (hours * millisInAnHour);
        long minutes = millisLeft / (60 * 1000);

        String text = "";
        if (days > 0) {
            if (days == 1) text = resources.getString(R.string.duration_one_day);
            else text = days + resources.getString(R.string.duration_days);
        }

        if (hours > 0) {
            if (hours == 1) text += resources.getString(R.string.duration_one_hour);
            else text += String.valueOf((int) hours) + resources.getString(R.string.duration_hours);
        }

        if (minutes == 0) text += resources.getString(R.string.duration_less_than_a_minute);
        else if (minutes > 0) {
            if (minutes == 1) text += resources.getString(R.string.duration_minute);
            else
                text += String.valueOf((int) minutes) + resources.getString(R.string.duration_minutes);
        }
        return text;
    }
}
