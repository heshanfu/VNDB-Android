package com.booboot.vndbandroid.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.booboot.vndbandroid.api.VNDBServer;

public class ConnectionReceiver extends BroadcastReceiver {
    public final static String CONNECTION_ERROR_MESSAGE = "A connection error occurred. Please check your connection or try again later.";

    @Override
    public void onReceive(Context context, Intent intent) {
        /*
        ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMan.getActiveNetworkInfo();

        if (netInfo == null) {
        */

        /* Resetting the sockets everytime the connection changes, to avoid dead sockets */
        VNDBServer.closeAll();
    }

    public static boolean isConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) VNDBApplication.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connectivityManager != null) {
            networkInfo = connectivityManager.getActiveNetworkInfo();
        }

        return networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED;
    }
}