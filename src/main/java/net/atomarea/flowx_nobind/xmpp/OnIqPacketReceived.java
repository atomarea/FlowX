package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}
