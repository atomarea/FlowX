package net.atomarea.flowx_nobind.xmpp.stanzas.streammgmt;

import net.atomarea.flowx_nobind.xmpp.stanzas.AbstractStanza;

public class RequestPacket extends AbstractStanza {

	public RequestPacket(int smVersion) {
		super("r");
		this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
	}

}
