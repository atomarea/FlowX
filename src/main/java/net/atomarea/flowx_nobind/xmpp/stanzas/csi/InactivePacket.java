package net.atomarea.flowx_nobind.xmpp.stanzas.csi;

import net.atomarea.flowx_nobind.xmpp.stanzas.AbstractStanza;

public class InactivePacket extends AbstractStanza {
	public InactivePacket() {
		super("inactive");
		setAttribute("xmlns", "urn:xmpp:csi:0");
	}
}
