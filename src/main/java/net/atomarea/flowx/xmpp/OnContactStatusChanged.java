package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.entities.Contact;

public interface OnContactStatusChanged {
	public void onContactStatusChanged(final Contact contact, final boolean online);
}
