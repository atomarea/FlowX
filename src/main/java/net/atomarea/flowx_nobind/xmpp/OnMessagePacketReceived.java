package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
