package net.atomarea.flowx.generator;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Presences;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.stanzas.PresencePacket;

public class PresenceGenerator extends AbstractGenerator {

	public PresenceGenerator(XmppConnectionService service) {
		super(service);
	}

	private PresencePacket subscription(String type, Contact contact) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", type);
		packet.setTo(contact.getJid());
		packet.setFrom(contact.getAccount().getJid().toBareJid());
		return packet;
	}

	public PresencePacket requestPresenceUpdatesFrom(Contact contact) {
		return subscription("subscribe", contact);
	}

	public PresencePacket stopPresenceUpdatesFrom(Contact contact) {
		return subscription("unsubscribe", contact);
	}

	public PresencePacket stopPresenceUpdatesTo(Contact contact) {
		return subscription("unsubscribed", contact);
	}

	public PresencePacket sendPresenceUpdatesTo(Contact contact) {
		return subscription("subscribed", contact);
	}

	public PresencePacket selfPresence(Account account, int presence) {
		PresencePacket packet = new PresencePacket();
		switch(presence) {
			case Presences.AWAY:
				packet.addChild("show").setContent("away");
				break;
			case Presences.XA:
				packet.addChild("show").setContent("xa");
				break;
			case Presences.CHAT:
				packet.addChild("show").setContent("chat");
				break;
			case Presences.DND:
				packet.addChild("show").setContent("dnd");
				break;
		}
		packet.setFrom(account.getJid());
		String sig = account.getPgpSignature();
		if (sig != null) {
			packet.addChild("status").setContent("online");
			packet.addChild("x", "jabber:x:signed").setContent(sig);
		}
		String capHash = getCapHash();
		if (capHash != null) {
			Element cap = packet.addChild("c",
					"http://jabber.org/protocol/caps");
			cap.setAttribute("hash", "sha-1");
			cap.setAttribute("node", "http://flowx.im");
			cap.setAttribute("ver", capHash);
		}
		return packet;
	}

	public PresencePacket sendOfflinePresence(Account account) {
		PresencePacket packet = new PresencePacket();
		packet.setFrom(account.getJid());
		packet.setAttribute("type","unavailable");
		return packet;
	}
}
