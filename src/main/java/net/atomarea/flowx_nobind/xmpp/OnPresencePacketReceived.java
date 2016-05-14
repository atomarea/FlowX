package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
