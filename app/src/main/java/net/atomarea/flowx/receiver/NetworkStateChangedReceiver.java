package net.atomarea.flowx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import net.atomarea.flowx.services.XmppService;
import net.atomarea.flowx.ui.activities.ContactsActivity;

/**
 * Created by Tom on 09.08.2016.
 */
public class NetworkStateChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i("FX NET", "Network State was changed");

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(ContactsActivity.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        boolean networkAvailable;
        if (activeNetworkInfo == null) networkAvailable = false;
        else if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_BLUETOOTH)
            networkAvailable = false;
        else networkAvailable = activeNetworkInfo.isAvailable();

        if (networkAvailable) {
            context.startService(new Intent(context, XmppService.class));
        }
    }
}
