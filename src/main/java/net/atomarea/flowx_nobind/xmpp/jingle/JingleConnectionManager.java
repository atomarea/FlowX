package net.atomarea.flowx_nobind.xmpp.jingle;

import android.annotation.SuppressLint;
import android.util.Log;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.atomarea.flowx_nobind.Config;
import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.entities.Message;
import net.atomarea.flowx_nobind.entities.Transferable;
import net.atomarea.flowx_nobind.services.AbstractConnectionManager;
import net.atomarea.flowx_nobind.services.XmppConnectionService;
import net.atomarea.flowx_nobind.utils.Xmlns;
import net.atomarea.flowx_nobind.xml.Element;
import net.atomarea.flowx_nobind.xmpp.OnIqPacketReceived;
import net.atomarea.flowx_nobind.xmpp.jid.Jid;
import net.atomarea.flowx_nobind.xmpp.jingle.stanzas.JinglePacket;
import net.atomarea.flowx_nobind.xmpp.stanzas.IqPacket;

public class JingleConnectionManager extends AbstractConnectionManager {
	private List<JingleConnection> connections = new CopyOnWriteArrayList<>();

	private HashMap<Jid, JingleCandidate> primaryCandidates = new HashMap<>();

	@SuppressLint("TrulyRandom")
	private SecureRandom random = new SecureRandom();

	public JingleConnectionManager(XmppConnectionService service) {
		super(service);
	}

	public void deliverPacket(Account account, JinglePacket packet) {
		if (packet.isAction("session-initiate")) {
			JingleConnection connection = new JingleConnection(this);
			connection.init(account, packet);
			connections.add(connection);
		} else {
			for (JingleConnection connection : connections) {
				if (connection.getAccount() == account
						&& connection.getSessionId().equals(
								packet.getSessionId())
						&& connection.getCounterPart().equals(packet.getFrom())) {
					connection.deliverPacket(packet);
					return;
				}
			}
			IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
			Element error = response.addChild("error");
			error.setAttribute("type", "cancel");
			error.addChild("item-not-found",
					"urn:ietf:params:xml:ns:xmpp-stanzas");
			error.addChild("unknown-session", "urn:xmpp:jingle:errors:1");
			account.getXmppConnection().sendIqPacket(response, null);
		}
	}

	public JingleConnection createNewConnection(Message message) {
		Transferable old = message.getTransferable();
		if (old != null) {
			old.cancel();
		}
		JingleConnection connection = new JingleConnection(this);
		mXmppConnectionService.markMessage(message,Message.STATUS_WAITING);
		connection.init(message);
		this.connections.add(connection);
		return connection;
	}

	public JingleConnection createNewConnection(final JinglePacket packet) {
		JingleConnection connection = new JingleConnection(this);
		this.connections.add(connection);
		return connection;
	}

	public void finishConnection(JingleConnection connection) {
		this.connections.remove(connection);
	}

	public void getPrimaryCandidate(Account account,
			final OnPrimaryCandidateFound listener) {
		if (Config.DISABLE_PROXY_LOOKUP) {
			listener.onPrimaryCandidateFound(false, null);
			return;
		}
		if (!this.primaryCandidates.containsKey(account.getJid().toBareJid())) {
			final Jid proxy = account.getXmppConnection().findDiscoItemByFeature(Xmlns.BYTE_STREAMS);
			if (proxy != null) {
				IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
				iq.setTo(proxy);
				iq.query(Xmlns.BYTE_STREAMS);
				account.getXmppConnection().sendIqPacket(iq,new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(Account account, IqPacket packet) {
						Element streamhost = packet.query().findChild("streamhost",Xmlns.BYTE_STREAMS);
						final String host = streamhost == null ? null : streamhost.getAttribute("host");
						final String port = streamhost == null ? null : streamhost.getAttribute("port");
						if (host != null && port != null) {
							try {
								JingleCandidate candidate = new JingleCandidate(nextRandomId(), true);
								candidate.setHost(host);
								candidate.setPort(Integer.parseInt(port));
								candidate.setType(JingleCandidate.TYPE_PROXY);
								candidate.setJid(proxy);
								candidate.setPriority(655360 + 65535);
								primaryCandidates.put(account.getJid().toBareJid(),candidate);
								listener.onPrimaryCandidateFound(true,candidate);
							} catch (final NumberFormatException e) {
								listener.onPrimaryCandidateFound(false,null);
								return;
							}
						} else {
							listener.onPrimaryCandidateFound(false,null);
						}
					}
				});
			} else {
				listener.onPrimaryCandidateFound(false, null);
			}

		} else {
			listener.onPrimaryCandidateFound(true,
					this.primaryCandidates.get(account.getJid().toBareJid()));
		}
	}

	public String nextRandomId() {
		return new BigInteger(50, random).toString(32);
	}

	public void deliverIbbPacket(Account account, IqPacket packet) {
		String sid = null;
		Element payload = null;
		if (packet.hasChild("open", "http://jabber.org/protocol/ibb")) {
			payload = packet.findChild("open", "http://jabber.org/protocol/ibb");
			sid = payload.getAttribute("sid");
		} else if (packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
			payload = packet.findChild("data", "http://jabber.org/protocol/ibb");
			sid = payload.getAttribute("sid");
		}
		if (sid != null) {
			for (JingleConnection connection : connections) {
				if (connection.getAccount() == account
						&& connection.hasTransportId(sid)) {
					JingleTransport transport = connection.getTransport();
					if (transport instanceof JingleInbandTransport) {
						JingleInbandTransport inbandTransport = (JingleInbandTransport) transport;
						inbandTransport.deliverPayload(packet, payload);
						return;
					}
				}
			}
			Log.d(Config.LOGTAG,"couldn't deliver payload: " + payload.toString());
		} else {
			Log.d(Config.LOGTAG, "no sid found in incoming ibb packet");
		}
	}

	public void cancelInTransmission() {
		for (JingleConnection connection : this.connections) {
			if (connection.getJingleStatus() == JingleConnection.JINGLE_STATUS_TRANSMITTING) {
				connection.cancel();
			}
		}
	}
}
