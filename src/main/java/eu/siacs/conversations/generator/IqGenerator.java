package eu.siacs.conversations.generator;


import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqGenerator extends AbstractGenerator {

	public IqGenerator(final XmppConnectionService service) {
		super(service);
	}

	public IqPacket discoResponse(final IqPacket request) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.RESULT);
		packet.setId(request.getId());
		packet.setTo(request.getFrom());
		final Element query = packet.addChild("query",
				"http://jabber.org/protocol/disco#info");
		query.setAttribute("node", request.query().getAttribute("node"));
		final Element identity = query.addChild("identity");
		identity.setAttribute("category", "client");
		identity.setAttribute("type", IDENTITY_TYPE);
		identity.setAttribute("name", getIdentityName());
		for (final String feature : getFeatures()) {
			query.addChild("feature").setAttribute("var", feature);
		}
		return packet;
	}

	public IqPacket versionResponse(final IqPacket request) {
		final IqPacket packet = request.generateResponse(IqPacket.TYPE.RESULT);
		Element query = packet.query("jabber:iq:version");
		query.addChild("name").setContent(IDENTITY_NAME);
		query.addChild("version").setContent(getIdentityVersion());
		return packet;
	}

	protected IqPacket publish(final String node, final Element item) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		final Element pubsub = packet.addChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		final Element publish = pubsub.addChild("publish");
		publish.setAttribute("node", node);
		publish.addChild(item);
		return packet;
	}

	protected IqPacket retrieve(String node, Element item) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
		final Element pubsub = packet.addChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		final Element items = pubsub.addChild("items");
		items.setAttribute("node", node);
		if (item != null) {
			items.addChild(item);
		}
		return packet;
	}

	public IqPacket publishAvatar(Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final Element data = item.addChild("data", "urn:xmpp:avatar:data");
		data.setContent(avatar.image);
		return publish("urn:xmpp:avatar:data", item);
	}

	public IqPacket publishAvatarMetadata(final Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final Element metadata = item
			.addChild("metadata", "urn:xmpp:avatar:metadata");
		final Element info = metadata.addChild("info");
		info.setAttribute("bytes", avatar.size);
		info.setAttribute("id", avatar.sha1sum);
		info.setAttribute("height", avatar.height);
		info.setAttribute("width", avatar.height);
		info.setAttribute("type", avatar.type);
		return publish("urn:xmpp:avatar:metadata", item);
	}

	public IqPacket retrievePepAvatar(final Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final IqPacket packet = retrieve("urn:xmpp:avatar:data", item);
		packet.setTo(avatar.owner);
		return packet;
	}

	public IqPacket retrieveVcardAvatar(final Avatar avatar) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
		packet.setTo(avatar.owner);
		packet.addChild("vCard","vcard-temp");
		return packet;
	}

	public IqPacket retrieveAvatarMetaData(final Jid to) {
		final IqPacket packet = retrieve("urn:xmpp:avatar:metadata", null);
		if (to != null) {
			packet.setTo(to);
		}
		return packet;
	}

	public IqPacket queryMessageArchiveManagement(final MessageArchiveService.Query mam) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		final Element query = packet.query("urn:xmpp:mam:0");
		query.setAttribute("queryid",mam.getQueryId());
		final Data data = new Data();
		data.setFormType("urn:xmpp:mam:0");
		if (mam.muc()) {
			packet.setTo(mam.getWith());
		} else if (mam.getWith()!=null) {
			data.put("with", mam.getWith().toString());
		}
		data.put("start",getTimestamp(mam.getStart()));
		data.put("end",getTimestamp(mam.getEnd()));
		query.addChild(data);
		if (mam.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
			query.addChild("set", "http://jabber.org/protocol/rsm").addChild("before").setContent(mam.getReference());
		} else if (mam.getReference() != null) {
			query.addChild("set", "http://jabber.org/protocol/rsm").addChild("after").setContent(mam.getReference());
		}
		return packet;
	}
	public IqPacket generateGetBlockList() {
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.addChild("blocklist", Xmlns.BLOCKING);

		return iq;
	}

	public IqPacket generateSetBlockRequest(final Jid jid) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		final Element block = iq.addChild("block", Xmlns.BLOCKING);
		block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
		return iq;
	}

	public IqPacket generateSetUnblockRequest(final Jid jid) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		final Element block = iq.addChild("unblock", Xmlns.BLOCKING);
		block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
		return iq;
	}

	public IqPacket generateSetPassword(final Account account, final String newPassword) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		packet.setTo(account.getServer());
		final Element query = packet.addChild("query", Xmlns.REGISTER);
		final Jid jid = account.getJid();
		query.addChild("username").setContent(jid.getLocalpart());
		query.addChild("password").setContent(newPassword);
		return packet;
	}

	public IqPacket changeAffiliation(Conversation conference, Jid jid, String affiliation) {
		List<Jid> jids = new ArrayList<>();
		jids.add(jid);
		return changeAffiliation(conference,jids,affiliation);
	}

	public IqPacket changeAffiliation(Conversation conference, List<Jid> jids, String affiliation) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		packet.setTo(conference.getJid().toBareJid());
		packet.setFrom(conference.getAccount().getJid());
		Element query = packet.query("http://jabber.org/protocol/muc#admin");
		for(Jid jid : jids) {
			Element item = query.addChild("item");
			item.setAttribute("jid", jid.toString());
			item.setAttribute("affiliation", affiliation);
		}
		return packet;
	}

	public IqPacket changeRole(Conversation conference, String nick, String role) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		packet.setTo(conference.getJid().toBareJid());
		packet.setFrom(conference.getAccount().getJid());
		Element item = packet.query("http://jabber.org/protocol/muc#admin").addChild("item");
		item.setAttribute("nick", nick);
		item.setAttribute("role", role);
		return packet;
	}
}
