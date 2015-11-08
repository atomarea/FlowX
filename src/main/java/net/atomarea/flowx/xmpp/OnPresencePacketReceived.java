package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
