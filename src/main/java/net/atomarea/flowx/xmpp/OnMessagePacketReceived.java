package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
