package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}
