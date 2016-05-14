package net.atomarea.flowx_nobind.xmpp.jingle;

import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.xmpp.PacketReceived;
import net.atomarea.flowx_nobind.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	void onJinglePacketReceived(Account account, JinglePacket packet);
}
