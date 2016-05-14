package net.atomarea.flowx_nobind.xmpp.stanzas.csi;

import net.atomarea.flowx_nobind.xmpp.stanzas.AbstractStanza;

public class ActivePacket extends AbstractStanza {
	public ActivePacket() {
		super("active");
		setAttribute("xmlns", "urn:xmpp:csi:0");
	}
}
