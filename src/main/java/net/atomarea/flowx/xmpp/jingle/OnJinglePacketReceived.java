package net.atomarea.flowx.xmpp.jingle;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xmpp.PacketReceived;
import net.atomarea.flowx.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	void onJinglePacketReceived(Account account, JinglePacket packet);
}
