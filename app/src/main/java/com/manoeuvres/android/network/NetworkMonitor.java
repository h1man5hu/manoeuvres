package com.manoeuvres.android.network;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;

import com.manoeuvres.android.util.Constants;

import java.io.IOException;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;


public class NetworkMonitor {

    private static NetworkMonitor ourInstance;

    private NetworkListener[] mObservers;

    private NetworkMonitor(Context applicationContext) {
        mObservers = new NetworkListener[Constants.MAX_NETWORK_LISTENERS_COUNT];

        /*
         * Uses a NetworkCallback instance to receive network updates in case of lollipop and above.
         * Falls back to receiving broadcasts otherwise.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager connectivityManager = (ConnectivityManager) applicationContext.getSystemService(CONNECTIVITY_SERVICE);
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);

                    if (isNetworkConnected()) notifyObservers(Constants.CALLBACK_NETWORK_CONNECTED);
                    else notifyObservers(Constants.CALLBACK_NETWORK_DISCONNECTED);
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);

                    notifyObservers(Constants.CALLBACK_NETWORK_DISCONNECTED);
                }
            };
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), callback);
        } else {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(CONNECTIVITY_ACTION)) {
                        if (isNetworkConnected())
                            notifyObservers(Constants.CALLBACK_NETWORK_CONNECTED);
                        else notifyObservers(Constants.CALLBACK_NETWORK_DISCONNECTED);
                    }
                }
            };
            applicationContext.registerReceiver(receiver, new IntentFilter(CONNECTIVITY_ACTION));
        }
    }

    public static NetworkMonitor getInstance(Context applicationContext) {
        if (ourInstance == null) ourInstance = new NetworkMonitor(applicationContext);
        return ourInstance;
    }

    public NetworkMonitor attach(Object component) {
        NetworkListener listener = (NetworkListener) component;
        for (NetworkListener observer : mObservers) {
            if (observer != null && observer.equals(listener)) return ourInstance;
        }

        for (int i = 0; i < mObservers.length; i++) {
            if (mObservers[i] == null) {
                mObservers[i] = listener;
                return ourInstance;
            }
        }
        return ourInstance;
    }

    public NetworkMonitor detach(Object component) {
        NetworkListener listener = (NetworkListener) component;
        for (int i = 0; i < mObservers.length; i++) {
            if (mObservers[i] != null && mObservers[i].equals(listener)) {
                mObservers[i] = null;
                return ourInstance;
            }
        }
        return ourInstance;
    }

    /* Pings Google's server to check for network connectivity. */
    public boolean isNetworkConnected() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process networkProcess = runtime.exec(Constants.COMMAND_NETWORK_CHECK);
            int exitValue = networkProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void notifyObservers(String event) {
        for (NetworkListener observer : mObservers) {
            if (observer != null) {
                switch (event) {
                    case Constants.CALLBACK_NETWORK_CONNECTED:
                        observer.onNetworkConnected();
                        break;
                    case Constants.CALLBACK_NETWORK_DISCONNECTED:
                        observer.onNetworkDisconnected();
                        break;
                }
            }
        }
    }

    public interface NetworkListener {
        void onNetworkConnected();

        void onNetworkDisconnected();
    }
}
