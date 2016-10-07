package com.manoeuvres.android;

import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;


public class MainActivity extends AppCompatActivity {

    private String mFacebookAccessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FacebookSdk.sdkInitialize(getApplicationContext());

        //App events for analytics.
        AppEventsLogger.activateApp(getApplication());

        mFacebookAccessToken = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("accessToken", null);

    }
}
