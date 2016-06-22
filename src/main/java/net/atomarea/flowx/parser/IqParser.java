package net.atomarea.flowx.parser;

import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyBundle;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.utils.Xmlns;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.OnIqPacketReceived;
import net.atomarea.flowx.xmpp.OnUpdateBlocklist;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.stanzas.IqPacket;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

	public IqParser(final XmppConnectionService service) {
		super(service);
	}

	private void rosterItems(final Account account, final Element query) {
		final String version = query.getAttribute("ver");
		if (version != null) {
			account.getRoster().setVersion(version);
		}
		for (final Element item : query.getChildren()) {
			if (item.getName().equals("item")) {
				final Jid jid = item.getAttributeAsJid("jid");
				if (jid == null) {
					continue;
				}
				final String name = item.getAttribute("name");
				final String subscription = item.getAttribute("subscription");
				final Contact contact = account.getRoster().getContact(jid);
				boolean bothPre = contact.getOption(Contact.Options.TO) && contact.getOption(Contact.Options.FROM);
				if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
					contact.setServerName(name);
					contact.parseGroupsFromElement(item);
				}
				if (subscription != null) {
					if (subscription.equals("remove")) {
						contact.resetOption(Contact.Options.IN_ROSTER);
						contact.resetOption(Contact.Options.DIRTY_DELETE);
						contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
					} else {
						contact.setOption(Contact.Options.IN_ROSTER);
						contact.resetOption(Contact.Options.DIRTY_PUSH);
						contact.parseSubscriptionFromElement(item);
					}
				}
				boolean both = contact.getOption(Contact.Options.TO) && contact.getOption(Contact.Options.FROM);
				if ((both != bothPre) && both) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": gained mutual presence subscription with "+contact.getJid());
					AxolotlService axolotlService = account.getAxolotlService();
					if (axolotlService != null) {
						axolotlService.clearErrorsInFetchStatusMap(contact.getJid());
					}
				}
				mXmppConnectionService.getAvatarService().clear(contact);
			}
		}
		mXmppConnectionService.updateConversationUi();
		mXmppConnectionService.updateRosterUi();
	}

	public String avatarData(final IqPacket packet) {
		final Element pubsub = packet.findChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		if (pubsub == null) {
			return null;
		}
		final Element items = pubsub.findChild("items");
		if (items == null) {
			return null;
		}
		return super.avatarData(items);
	}

	public Element getItem(final IqPacket packet) {
		final Element pubsub = packet.findChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		if (pubsub == null) {
			return null;
		}
		final Element items = pubsub.findChild("items");
		if (items == null) {
			return null;
		}
		return items.findChild("item");
	}

	@NonNull
	public Set<Integer> deviceIds(final Element item) {
		Set<Integer> deviceIds = new HashSet<>();
		if (item != null) {
			final Element list = item.findChild("list");
			if (list != null) {
				for (Element device : list.getChildren()) {
					if (!device.getName().equals("device")) {
						continue;
					}
					try {
						Integer id = Integer.valueOf(device.getAttribute("id"));
						deviceIds.add(id);
					} catch (NumberFormatException e) {
						Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Encountered invalid <device> node in PEP ("+e.getMessage()+"):" + device.toString()+ ", skipping...");
						continue;
					}
				}
			}
		}
		return deviceIds;
	}

	public Integer signedPreKeyId(final Element bundle) {
		final Element signedPreKeyPublic = bundle.findChild("signedPreKeyPublic");
		if(signedPreKeyPublic == null) {
			return null;
		}
		return Integer.valueOf(signedPreKeyPublic.getAttribute("signedPreKeyId"));
	}

	public ECPublicKey signedPreKeyPublic(final Element bundle) {
		ECPublicKey publicKey = null;
		final Element signedPreKeyPublic = bundle.findChild("signedPreKeyPublic");
		if(signedPreKeyPublic == null) {
			return null;
		}
		try {
			publicKey = Curve.decodePoint(Base64.decode(signedPreKeyPublic.getContent(),Base64.DEFAULT), 0);
		} catch (Throwable e) {
			Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Invalid signedPreKeyPublic in PEP: " + e.getMessage());
		}
		return publicKey;
	}

	public byte[] signedPreKeySignature(final Element bundle) {
		final Element signedPreKeySignature = bundle.findChild("signedPreKeySignature");
		if(signedPreKeySignature == null) {
			return null;
		}
		try {
			return Base64.decode(signedPreKeySignature.getContent(), Base64.DEFAULT);
		} catch (Throwable e) {
			Log.e(Config.LOGTAG,AxolotlService.LOGPREFIX+" : Invalid base64 in signedPreKeySignature");
			return null;
		}
	}

	public IdentityKey identityKey(final Element bundle) {
		IdentityKey identityKey = null;
		final Element identityKeyElement = bundle.findChild("identityKey");
		if(identityKeyElement == null) {
			return null;
		}
		try {
			identityKey = new IdentityKey(Base64.decode(identityKeyElement.getContent(), Base64.DEFAULT), 0);
		} catch (Throwable e) {
			Log.e(Config.LOGTAG,AxolotlService.LOGPREFIX+" : "+"Invalid identityKey in PEP: "+e.getMessage());
		}
		return identityKey;
	}

	public Map<Integer, ECPublicKey> preKeyPublics(final IqPacket packet) {
		Map<Integer, ECPublicKey> preKeyRecords = new HashMap<>();
		Element item = getItem(packet);
		if (item == null) {
			Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Couldn't find <item> in bundle IQ packet: " + packet);
			return null;
		}
		final Element bundleElement = item.findChild("bundle");
		if(bundleElement == null) {
			return null;
		}
		final Element prekeysElement = bundleElement.findChild("prekeys");
		if(prekeysElement == null) {
			Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Couldn't find <prekeys> in bundle IQ packet: " + packet);
			return null;
		}
		for(Element preKeyPublicElement : prekeysElement.getChildren()) {
			if(!preKeyPublicElement.getName().equals("preKeyPublic")){
				Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Encountered unexpected tag in prekeys list: " + preKeyPublicElement);
				continue;
			}
			Integer preKeyId = Integer.valueOf(preKeyPublicElement.getAttribute("preKeyId"));
			try {
				ECPublicKey preKeyPublic = Curve.decodePoint(Base64.decode(preKeyPublicElement.getContent(), Base64.DEFAULT), 0);
				preKeyRecords.put(preKeyId, preKeyPublic);
			} catch (Throwable e) {
				Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Invalid preKeyPublic (ID="+preKeyId+") in PEP: "+ e.getMessage()+", skipping...");
				continue;
			}
		}
		return preKeyRecords;
	}

	public Pair<X509Certificate[],byte[]> verification(final IqPacket packet) {
		Element item = getItem(packet);
		Element verification = item != null ? item.findChild("verification",AxolotlService.PEP_PREFIX) : null;
		Element chain = verification != null ? verification.findChild("chain") : null;
		Element signature = verification != null ? verification.findChild("signature") : null;
		if (chain != null && signature != null) {
			List<Element> certElements = chain.getChildren();
			X509Certificate[] certificates = new X509Certificate[certElements.size()];
			try {
				CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				int i = 0;
				for(Element cert : certElements) {
					certificates[i] = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.getContent(),Base64.DEFAULT)));
					++i;
				}
				return new Pair<>(certificates,Base64.decode(signature.getContent(),Base64.DEFAULT));
			} catch (CertificateException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	public PreKeyBundle bundle(final IqPacket bundle) {
		Element bundleItem = getItem(bundle);
		if(bundleItem == null) {
			return null;
		}
		final Element bundleElement = bundleItem.findChild("bundle");
		if(bundleElement == null) {
			return null;
		}
		ECPublicKey signedPreKeyPublic = signedPreKeyPublic(bundleElement);
		Integer signedPreKeyId = signedPreKeyId(bundleElement);
		byte[] signedPreKeySignature = signedPreKeySignature(bundleElement);
		IdentityKey identityKey = identityKey(bundleElement);
		if(signedPreKeyPublic == null || identityKey == null) {
			return null;
		}

		return new PreKeyBundle(0, 0, 0, null,
				signedPreKeyId, signedPreKeyPublic, signedPreKeySignature, identityKey);
	}

	public List<PreKeyBundle> preKeys(final IqPacket preKeys) {
		List<PreKeyBundle> bundles = new ArrayList<>();
		Map<Integer, ECPublicKey> preKeyPublics = preKeyPublics(preKeys);
		if ( preKeyPublics != null) {
			for (Integer preKeyId : preKeyPublics.keySet()) {
				ECPublicKey preKeyPublic = preKeyPublics.get(preKeyId);
				bundles.add(new PreKeyBundle(0, 0, preKeyId, preKeyPublic,
						0, null, null, null));
			}
		}

		return bundles;
	}

	@Override
	public void onIqPacketReceived(final Account account, final IqPacket packet) {
		if (packet.getType() == IqPacket.TYPE.ERROR || packet.getType() == IqPacket.TYPE.TIMEOUT) {
			return;
		} else if (packet.hasChild("query", Xmlns.ROSTER) && packet.fromServer(account)) {
			final Element query = packet.findChild("query");
			// If this is in response to a query for the whole roster:
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				account.getRoster().markAllAsNotInRoster();
			}
			this.rosterItems(account, query);
		} else if ((packet.hasChild("block", Xmlns.BLOCKING) || packet.hasChild("blocklist", Xmlns.BLOCKING)) &&
				packet.fromServer(account)) {
			// Block list or block push.
			Log.d(Config.LOGTAG, "Received blocklist update from server");
			final Element blocklist = packet.findChild("blocklist", Xmlns.BLOCKING);
			final Element block = packet.findChild("block", Xmlns.BLOCKING);
			final Collection<Element> items = blocklist != null ? blocklist.getChildren() :
				(block != null ? block.getChildren() : null);
			// If this is a response to a blocklist query, clear the block list and replace with the new one.
			// Otherwise, just update the existing blocklist.
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				account.clearBlocklist();
				account.getXmppConnection().getFeatures().setBlockListRequested(true);
			}
			if (items != null) {
				final Collection<Jid> jids = new ArrayList<>(items.size());
				// Create a collection of Jids from the packet
				for (final Element item : items) {
					if (item.getName().equals("item")) {
						final Jid jid = item.getAttributeAsJid("jid");
						if (jid != null) {
							jids.add(jid);
						}
					}
				}
				account.getBlocklist().addAll(jids);
			}
			// Update the UI
			mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
			if (packet.getType() == IqPacket.TYPE.SET) {
				final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
				mXmppConnectionService.sendIqPacket(account, response, null);
			}
		} else if (packet.hasChild("unblock", Xmlns.BLOCKING) &&
				packet.fromServer(account) && packet.getType() == IqPacket.TYPE.SET) {
			Log.d(Config.LOGTAG, "Received unblock update from server");
			final Collection<Element> items = packet.findChild("unblock", Xmlns.BLOCKING).getChildren();
			if (items.size() == 0) {
				// No children to unblock == unblock all
				account.getBlocklist().clear();
			} else {
				final Collection<Jid> jids = new ArrayList<>(items.size());
				for (final Element item : items) {
					if (item.getName().equals("item")) {
						final Jid jid = item.getAttributeAsJid("jid");
						if (jid != null) {
							jids.add(jid);
						}
					}
				}
				account.getBlocklist().removeAll(jids);
			}
			mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
			final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
			mXmppConnectionService.sendIqPacket(account, response, null);
		} else if (packet.hasChild("open", "http://jabber.org/protocol/ibb")
				|| packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
			mXmppConnectionService.getJingleConnectionManager()
				.deliverIbbPacket(account, packet);
		} else if (packet.hasChild("query", "http://jabber.org/protocol/disco#info")) {
			final IqPacket response = mXmppConnectionService.getIqGenerator().discoResponse(packet);
			mXmppConnectionService.sendIqPacket(account, response, null);
		} else if (packet.hasChild("query","jabber:iq:version")) {
			final IqPacket response = mXmppConnectionService.getIqGenerator().versionResponse(packet);
			mXmppConnectionService.sendIqPacket(account,response,null);
		} else if (packet.hasChild("ping", "urn:xmpp:ping")) {
			final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
			mXmppConnectionService.sendIqPacket(account, response, null);
		} else {
			if (packet.getType() == IqPacket.TYPE.GET || packet.getType() == IqPacket.TYPE.SET) {
				final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
				final Element error = response.addChild("error");
				error.setAttribute("type", "cancel");
				error.addChild("feature-not-implemented","urn:ietf:params:xml:ns:xmpp-stanzas");
				account.getXmppConnection().sendIqPacket(response, null);
			}
		}
	}

}
