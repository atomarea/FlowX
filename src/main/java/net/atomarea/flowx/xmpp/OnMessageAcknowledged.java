package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.entities.Account;

public interface OnMessageAcknowledged {
	public void onMessageAcknowledged(Account account, String id);
}
