package net.atomarea.flowx.parser;

import android.util.Log;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.MucOptions;
import net.atomarea.flowx.entities.Presence;
import net.atomarea.flowx.generator.IqGenerator;
import net.atomarea.flowx.generator.PresenceGenerator;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.OnPresencePacketReceived;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.pep.Avatar;
import net.atomarea.flowx.xmpp.stanzas.PresencePacket;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class PresenceParser extends AbstractParser implements
		OnPresencePacketReceived {

	public PresenceParser(XmppConnectionService service) {
		super(service);
	}

	public void parseConferencePresence(PresencePacket packet, Account account) {
		final Conversation conversation = packet.getFrom() == null ? null : mXmppConnectionService.find(account, packet.getFrom().toBareJid());
		if (conversation != null) {
			final MucOptions mucOptions = conversation.getMucOptions();
			boolean before = mucOptions.online();
			int count = mucOptions.getUserCount();
			final List<MucOptions.User> tileUserBefore = mucOptions.getUsers(5);
			processConferencePresence(packet, conversation);
			final List<MucOptions.User> tileUserAfter = mucOptions.getUsers(5);
			if (!tileUserAfter.equals(tileUserBefore)) {
				mXmppConnectionService.getAvatarService().clear(mucOptions);
			}
			if (before != mucOptions.online() || (mucOptions.online() && count != mucOptions.getUserCount())) {
				mXmppConnectionService.updateConversationUi();
			} else if (mucOptions.online()) {
				mXmppConnectionService.updateMucRosterUi();
			}
		}
	}

	private void processConferencePresence(PresencePacket packet, Conversation conversation) {
		MucOptions mucOptions = conversation.getMucOptions();
		final Jid from = packet.getFrom();
		if (!from.isBareJid()) {
			final String type = packet.getAttribute("type");
			final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
			Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
			final List<String> codes = getStatusCodes(x);
			if (type == null) {
				if (x != null) {
					Element item = x.findChild("item");
					if (item != null && !from.isBareJid()) {
						mucOptions.setError(MucOptions.Error.NONE);
						MucOptions.User user = parseItem(conversation, item, from);
						if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(mucOptions.getConversation().getJid())) {
							mucOptions.setOnline();
							mucOptions.setSelf(user);
							if (mucOptions.mNickChangingInProgress) {
								if (mucOptions.onRenameListener != null) {
									mucOptions.onRenameListener.onSuccess();
								}
								mucOptions.mNickChangingInProgress = false;
							}
						} else {
							mucOptions.updateUser(user);
						}
						if (codes.contains(MucOptions.STATUS_CODE_ROOM_CREATED) && mucOptions.autoPushConfiguration()) {
							Log.d(Config.LOGTAG,mucOptions.getAccount().getJid().toBareJid()
									+": room '"
									+mucOptions.getConversation().getJid().toBareJid()
									+"' created. pushing default configuration");
							mXmppConnectionService.pushConferenceConfiguration(mucOptions.getConversation(),
									IqGenerator.defaultRoomConfiguration(),
									null);
						}
						if (avatar != null) {
							avatar.owner = from;
							if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
								if (user.setAvatar(avatar)) {
									mXmppConnectionService.getAvatarService().clear(user);
								}
							} else if (mXmppConnectionService.isDataSaverDisabled()) {
								mXmppConnectionService.fetchAvatar(mucOptions.getAccount(), avatar);
							}
						}
					}
				}
			} else if (type.equals("unavailable")) {
				if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
						packet.getFrom().equals(mucOptions.getConversation().getJid())) {
					if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
						mucOptions.mNickChangingInProgress = true;
					} else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
						mucOptions.setError(MucOptions.Error.KICKED);
					} else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
						mucOptions.setError(MucOptions.Error.BANNED);
					} else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
						mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
					} else if (codes.contains(MucOptions.STATUS_CODE_AFFILIATION_CHANGE)) {
						mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
					} else if (codes.contains(MucOptions.STATUS_CODE_SHUTDOWN)) {
						mucOptions.setError(MucOptions.Error.SHUTDOWN);
					} else {
						mucOptions.setError(MucOptions.Error.UNKNOWN);
						Log.d(Config.LOGTAG, "unknown error in conference: " + packet);
					}
				} else if (!from.isBareJid()){
					Element item = x.findChild("item");
					if (item != null) {
						mucOptions.updateUser(parseItem(conversation, item, from));
					}
					MucOptions.User user = mucOptions.deleteUser(from);
					if (user != null) {
						mXmppConnectionService.getAvatarService().clear(user);
					}
				}
			} else if (type.equals("error")) {
				Element error = packet.findChild("error");
				if (error != null && error.hasChild("conflict")) {
					if (mucOptions.online()) {
						if (mucOptions.onRenameListener != null) {
							mucOptions.onRenameListener.onFailure();
						}
					} else {
						mucOptions.setError(MucOptions.Error.NICK_IN_USE);
					}
				} else if (error != null && error.hasChild("not-authorized")) {
					mucOptions.setError(MucOptions.Error.PASSWORD_REQUIRED);
				} else if (error != null && error.hasChild("forbidden")) {
					mucOptions.setError(MucOptions.Error.BANNED);
				} else if (error != null && error.hasChild("registration-required")) {
					mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
				}
			}
		}
	}

	private static List<String> getStatusCodes(Element x) {
		List<String> codes = new ArrayList<>();
		if (x != null) {
			for (Element child : x.getChildren()) {
				if (child.getName().equals("status")) {
					String code = child.getAttribute("code");
					if (code != null) {
						codes.add(code);
					}
				}
			}
		}
		return codes;
	}

	public void parseContactPresence(final PresencePacket packet, final Account account) {
		final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
		final Jid from = packet.getFrom();
		if (from == null || from.equals(account.getJid())) {
			return;
		}
		final String type = packet.getAttribute("type");
		final Contact contact = account.getRoster().getContact(from);
		if (type == null) {
			final String resource = from.isBareJid() ? "" : from.getResourcepart();
			contact.setPresenceName(packet.findChildContent("nick", "http://jabber.org/protocol/nick"));
			Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
			if (avatar != null && (!contact.isSelf() || account.getAvatar() == null)) {
				avatar.owner = from.toBareJid();
				if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
					if (avatar.owner.equals(account.getJid().toBareJid())) {
						account.setAvatar(avatar.getFilename());
						mXmppConnectionService.databaseBackend.updateAccount(account);
						mXmppConnectionService.getAvatarService().clear(account);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateAccountUi();
					} else if (contact.setAvatar(avatar)) {
						mXmppConnectionService.getAvatarService().clear(contact);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateRosterUi();
					}
				} else if (mXmppConnectionService.isDataSaverDisabled()){
					mXmppConnectionService.fetchAvatar(account, avatar);
				}
			}
			int sizeBefore = contact.getPresences().size();

			final String show = packet.findChildContent("show");
			final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
			final String message = packet.findChildContent("status");
			final Presence presence = Presence.parse(show, caps, message);
			contact.updatePresence(resource, presence);
			if (presence.hasCaps()) {
				mXmppConnectionService.fetchCaps(account, from, presence);
			}

			final Element idle = packet.findChild("idle","urn:xmpp:idle:1");
			if (idle != null) {
				contact.flagInactive();
				String since = idle.getAttribute("since");
				try {
					contact.setLastseen(AbstractParser.parseTimestamp(since));
				} catch (NullPointerException | ParseException e) {
					contact.setLastseen(System.currentTimeMillis());
				}
			} else {
				contact.flagActive();
				contact.setLastseen(AbstractParser.parseTimestamp(packet));
			}

			Element x = packet.findChild("x", "jabber:x:signed");
			boolean online = sizeBefore < contact.getPresences().size();
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
		} else if (type.equals("unavailable")) {
			if (from.isBareJid()) {
				contact.clearPresences();
			} else {
				contact.removePresence(from.getResourcepart());
			}
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
		} else if (type.equals("subscribe")) {
			if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				mXmppConnectionService.sendPresencePacket(account,
						mPresenceGenerator.sendPresenceUpdatesTo(contact));
			} else {
				contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
				final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
						account, contact.getJid().toBareJid(), false);
				final String statusMessage = packet.findChildContent("status");
				if (statusMessage != null
						&& !statusMessage.isEmpty()
						&& conversation.countMessages() == 0) {
					conversation.add(new Message(
							conversation,
							statusMessage,
							Message.ENCRYPTION_NONE,
							Message.STATUS_RECEIVED
					));
				}
			}
		}
		mXmppConnectionService.updateRosterUi();
	}

	@Override
	public void onPresencePacketReceived(Account account, PresencePacket packet) {
		if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			this.parseConferencePresence(packet, account);
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
			this.parseConferencePresence(packet, account);
		} else {
			this.parseContactPresence(packet, account);
		}
	}

}
