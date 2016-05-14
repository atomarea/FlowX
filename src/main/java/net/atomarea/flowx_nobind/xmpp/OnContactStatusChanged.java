package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.entities.Contact;

public interface OnContactStatusChanged {
	public void onContactStatusChanged(final Contact contact, final boolean online);
}
