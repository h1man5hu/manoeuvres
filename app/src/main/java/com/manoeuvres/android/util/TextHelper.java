package com.manoeuvres.android.util;


public class TextHelper {

    public static String getDurationText(long startTime, long endTime) {
        if (endTime == 0) {
            endTime = System.currentTimeMillis();
        }
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
            if (days == 1) {
                text = "1 day, ";
            } else {
                text = days + " days, ";
            }
        }

        if (hours > 0) {
            if (hours == 1) {
                text += "1 hour and ";
            } else {
                text += String.valueOf((int) hours) + " hours and ";
            }

        }

        if (minutes == 0) {
            text += "less than a minute";
        } else if (minutes > 0) {

            if (minutes == 1) {
                text += "a minute";
            } else {
                text += String.valueOf((int) minutes) + " minutes ";
            }

        }

        return text;
    }
}
