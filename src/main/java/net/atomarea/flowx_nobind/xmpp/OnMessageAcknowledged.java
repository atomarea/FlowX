package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.entities.Account;

public interface OnMessageAcknowledged {
	public void onMessageAcknowledged(Account account, String id);
}
