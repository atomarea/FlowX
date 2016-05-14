package net.atomarea.flowx_nobind.xmpp.jingle.stanzas;

import net.atomarea.flowx_nobind.xml.Element;

public class Reason extends Element {
	private Reason(String name) {
		super(name);
	}

	public Reason() {
		super("reason");
	}
}
