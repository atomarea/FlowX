package net.atomarea.flowx.services;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.ui.ShareWithActivity;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.M)
public class ContactChooserTargetService extends ChooserTargetService implements ServiceConnection {

	private final Object lock = new Object();

	private XmppConnectionService mXmppConnectionService;

	private final int MAX_TARGETS = 5;

	@Override
	public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("contact_chooser");
		startService(intent);
		bindService(intent, this, Context.BIND_AUTO_CREATE);
		ArrayList<ChooserTarget> chooserTargets = new ArrayList<>();
		try {
			waitForService();
			final ArrayList<Conversation> conversations = new ArrayList<>();
			if (!mXmppConnectionService.areMessagesInitialized()) {
				return chooserTargets;
			}
			mXmppConnectionService.populateWithOrderedConversations(conversations, false);
			final ComponentName componentName = new ComponentName(this, ShareWithActivity.class);
			final int pixel = (int) (48 * getResources().getDisplayMetrics().density);
			for(int i = 0; i < Math.min(conversations.size(),MAX_TARGETS); ++i) {
				final Conversation conversation = conversations.get(i);
				final String name = conversation.getName();
				final Icon icon = Icon.createWithBitmap(mXmppConnectionService.getAvatarService().get(conversation, pixel));
				final float score = 1 - (1.0f / MAX_TARGETS) * i;
				final Bundle extras = new Bundle();
				extras.putString("uuid", conversation.getUuid());
				chooserTargets.add(new ChooserTarget(name, icon, score, componentName, extras));
			}
		} catch (InterruptedException e) {
		}
		unbindService(this);
		return chooserTargets;
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
		mXmppConnectionService = binder.getService();
		synchronized (this.lock) {
			lock.notifyAll();
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		mXmppConnectionService = null;
	}

	private void waitForService() throws InterruptedException {
		if (mXmppConnectionService == null) {
			synchronized (this.lock) {
				lock.wait();
			}
		}
	}
}
