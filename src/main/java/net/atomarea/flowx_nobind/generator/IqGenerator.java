package net.atomarea.flowx_nobind.generator;


import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.atomarea.flowx_nobind.Config;
import net.atomarea.flowx_nobind.crypto.axolotl.AxolotlService;
import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.entities.Conversation;
import net.atomarea.flowx_nobind.entities.DownloadableFile;
import net.atomarea.flowx_nobind.services.MessageArchiveService;
import net.atomarea.flowx_nobind.services.XmppConnectionService;
import net.atomarea.flowx_nobind.utils.Xmlns;
import net.atomarea.flowx_nobind.xml.Element;
import net.atomarea.flowx_nobind.xmpp.forms.Data;
import net.atomarea.flowx_nobind.xmpp.jid.Jid;
import net.atomarea.flowx_nobind.xmpp.pep.Avatar;
import net.atomarea.flowx_nobind.xmpp.stanzas.IqPacket;

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

	public IqPacket publishNick(String nick) {
		final Element item = new Element("item");
		item.addChild("nick","http://jabber.org/protocol/nick").setContent(nick);
		return publish("http://jabber.org/protocol/nick", item);
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
		packet.addChild("vCard", "vcard-temp");
		return packet;
	}

	public IqPacket retrieveAvatarMetaData(final Jid to) {
		final IqPacket packet = retrieve("urn:xmpp:avatar:metadata", null);
		if (to != null) {
			packet.setTo(to);
		}
		return packet;
	}

	public IqPacket retrieveDeviceIds(final Jid to) {
		final IqPacket packet = retrieve(AxolotlService.PEP_DEVICE_LIST, null);
		if(to != null) {
			packet.setTo(to);
		}
		return packet;
	}

	public IqPacket retrieveBundlesForDevice(final Jid to, final int deviceid) {
		final IqPacket packet = retrieve(AxolotlService.PEP_BUNDLES+":"+deviceid, null);
		packet.setTo(to);
		return packet;
	}

	public IqPacket retrieveVerificationForDevice(final Jid to, final int deviceid) {
		final IqPacket packet = retrieve(AxolotlService.PEP_VERIFICATION+":"+deviceid, null);
		packet.setTo(to);
		return packet;
	}

	public IqPacket publishDeviceIds(final Set<Integer> ids) {
		final Element item = new Element("item");
		final Element list = item.addChild("list", AxolotlService.PEP_PREFIX);
		for(Integer id:ids) {
			final Element device = new Element("device");
			device.setAttribute("id", id);
			list.addChild(device);
		}
		return publish(AxolotlService.PEP_DEVICE_LIST, item);
	}

	public IqPacket publishBundles(final SignedPreKeyRecord signedPreKeyRecord, final IdentityKey identityKey,
	                               final Set<PreKeyRecord> preKeyRecords, final int deviceId) {
		final Element item = new Element("item");
		final Element bundle = item.addChild("bundle", AxolotlService.PEP_PREFIX);
		final Element signedPreKeyPublic = bundle.addChild("signedPreKeyPublic");
		signedPreKeyPublic.setAttribute("signedPreKeyId", signedPreKeyRecord.getId());
		ECPublicKey publicKey = signedPreKeyRecord.getKeyPair().getPublicKey();
		signedPreKeyPublic.setContent(Base64.encodeToString(publicKey.serialize(),Base64.DEFAULT));
		final Element signedPreKeySignature = bundle.addChild("signedPreKeySignature");
		signedPreKeySignature.setContent(Base64.encodeToString(signedPreKeyRecord.getSignature(),Base64.DEFAULT));
		final Element identityKeyElement = bundle.addChild("identityKey");
		identityKeyElement.setContent(Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));

		final Element prekeys = bundle.addChild("prekeys", AxolotlService.PEP_PREFIX);
		for(PreKeyRecord preKeyRecord:preKeyRecords) {
			final Element prekey = prekeys.addChild("preKeyPublic");
			prekey.setAttribute("preKeyId", preKeyRecord.getId());
			prekey.setContent(Base64.encodeToString(preKeyRecord.getKeyPair().getPublicKey().serialize(), Base64.DEFAULT));
		}

		return publish(AxolotlService.PEP_BUNDLES+":"+deviceId, item);
	}

	public IqPacket publishVerification(byte[] signature, X509Certificate[] certificates, final int deviceId) {
		final Element item = new Element("item");
		final Element verification = item.addChild("verification", AxolotlService.PEP_PREFIX);
		final Element chain = verification.addChild("chain");
		for(int i = 0; i < certificates.length; ++i) {
			try {
				Element certificate = chain.addChild("certificate");
				certificate.setContent(Base64.encodeToString(certificates[i].getEncoded(), Base64.DEFAULT));
				certificate.setAttribute("index",i);
			} catch (CertificateEncodingException e) {
				Log.d(Config.LOGTAG, "could not encode certificate");
			}
		}
		verification.addChild("signature").setContent(Base64.encodeToString(signature, Base64.DEFAULT));
		return publish(AxolotlService.PEP_VERIFICATION+":"+deviceId, item);
	}

	public IqPacket queryMessageArchiveManagement(final MessageArchiveService.Query mam) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		final Element query = packet.query("urn:xmpp:mam:0");
		query.setAttribute("queryid", mam.getQueryId());
		final Data data = new Data();
		data.setFormType("urn:xmpp:mam:0");
		if (mam.muc()) {
			packet.setTo(mam.getWith());
		} else if (mam.getWith()!=null) {
			data.put("with", mam.getWith().toString());
		}
		data.put("start", getTimestamp(mam.getStart()));
		data.put("end", getTimestamp(mam.getEnd()));
		data.submit();
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

	public IqPacket requestHttpUploadSlot(Jid host, DownloadableFile file, String mime) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
		packet.setTo(host);
		Element request = packet.addChild("request", Xmlns.HTTP_UPLOAD);
		request.addChild("filename").setContent(file.getName());
		request.addChild("size").setContent(String.valueOf(file.getExpectedSize()));
		if (mime != null) {
			request.addChild("content-type").setContent(mime);
		}
		return packet;
	}

	public IqPacket generateCreateAccountWithCaptcha(Account account, String id, Data data) {
		final IqPacket register = new IqPacket(IqPacket.TYPE.SET);

		register.setTo(account.getServer());
		register.setId(id);
		Element query = register.query("jabber:iq:register");
		if (data != null) {
			query.addChild(data);
		}
		return register;
	}

	public IqPacket pushTokenToAppServer(Jid appServer, String token, String deviceId) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		packet.setTo(appServer);
		Element command = packet.addChild("command", "http://jabber.org/protocol/commands");
		command.setAttribute("node","register-push-gcm");
		command.setAttribute("action","execute");
		Data data = new Data();
		data.put("token", token);
		data.put("device-id", deviceId);
		data.submit();
		command.addChild(data);
		return packet;
	}

	public IqPacket enablePush(Jid jid, String node, String secret) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
		Element enable = packet.addChild("enable","urn:xmpp:push:0");
		enable.setAttribute("jid",jid.toString());
		enable.setAttribute("node", node);
		Data data = new Data();
		data.setFormType("http://jabber.org/protocol/pubsub#publish-options");
		data.put("secret",secret);
		data.submit();
		enable.addChild(data);
		return packet;
	}

	public IqPacket queryAffiliation(Conversation conversation, String affiliation) {
		IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
		packet.setTo(conversation.getJid().toBareJid());
		packet.query("http://jabber.org/protocol/muc#admin").addChild("item").setAttribute("affiliation",affiliation);
		return packet;
	}
}
