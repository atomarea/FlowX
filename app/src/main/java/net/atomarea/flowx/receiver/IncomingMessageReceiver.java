package net.atomarea.flowx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Tom on 08.08.2016.
 */
public class IncomingMessageReceiver extends BroadcastReceiver {

    public static final String INTENT = "net.atomarea.flowx.intent.action.INCOMING_MESSAGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        
    }
}
