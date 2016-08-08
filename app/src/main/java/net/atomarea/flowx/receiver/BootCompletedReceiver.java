package net.atomarea.flowx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.atomarea.flowx.services.XmppService;

/**
 * Created by Tom on 08.08.2016.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, XmppService.class));
    }
}
