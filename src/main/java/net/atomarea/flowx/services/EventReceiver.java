package net.atomarea.flowx.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.persistance.DatabaseBackend;

public class EventReceiver extends BroadcastReceiver {
	public static final String bg = "net.atomarea.flowx.bg";
	public static final int MINUTEN = 1;
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		if (intent.getAction() != null) {
			mIntentForService.setAction(intent.getAction());
		} else {
			mIntentForService.setAction("other").equals(bg);
			AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			mgr.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 1000, 1000 * 60 * MINUTEN, PendingIntent.getBroadcast(context, 1337, new Intent(bg), 0));
		}
		final String action = intent.getAction();
		if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && Config.PUSH_MODE) {
			return;
		}
		if (action.equals("ui") || DatabaseBackend.getInstance(context).hasEnabledAccounts()) {
			context.startService(mIntentForService);
		}
	}

}
