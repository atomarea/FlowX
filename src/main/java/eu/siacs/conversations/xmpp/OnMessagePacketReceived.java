package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
