package net.atomarea.flowx.entities;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.PgpEngine;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.forms.Data;
import net.atomarea.flowx.xmpp.forms.Field;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.stanzas.PresencePacket;

@SuppressLint("DefaultLocale")
public class MucOptions {

	public enum Affiliation {
		OWNER("owner", 4, R.string.owner),
		ADMIN("admin", 3, R.string.admin),
		MEMBER("member", 2, R.string.member),
		OUTCAST("outcast", 0, R.string.outcast),
		NONE("none", 1, R.string.no_affiliation);

		Affiliation(String string, int rank, int resId) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean outranks(Affiliation affiliation) {
			return rank > affiliation.rank;
		}

		public boolean ranks(Affiliation affiliation) {
			return rank >= affiliation.rank;
		}
	}

	public enum Role {
		MODERATOR("moderator", R.string.moderator,3),
		VISITOR("visitor", R.string.visitor,1),
		PARTICIPANT("participant", R.string.participant,2),
		NONE("none", R.string.no_role,0);

		private Role(String string, int resId, int rank) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean ranks(Role role) {
			return rank >= role.rank;
		}
	}

	public static final int ERROR_NO_ERROR = 0;
	public static final int ERROR_NICK_IN_USE = 1;
	public static final int ERROR_UNKNOWN = 2;
	public static final int ERROR_PASSWORD_REQUIRED = 3;
	public static final int ERROR_BANNED = 4;
	public static final int ERROR_MEMBERS_ONLY = 5;

	public static final int KICKED_FROM_ROOM = 9;

	public static final String STATUS_CODE_ROOM_CONFIG_CHANGED = "104";
	public static final String STATUS_CODE_SELF_PRESENCE = "110";
	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_CHANGED_NICK = "303";
	public static final String STATUS_CODE_KICKED = "307";
	public static final String STATUS_CODE_LOST_MEMBERSHIP = "321";

	private interface OnEventListener {
		public void onSuccess();

		public void onFailure();
	}

	public interface OnRenameListener extends OnEventListener {

	}

	public class User {
		private Role role = Role.NONE;
		private Affiliation affiliation = Affiliation.NONE;
		private String name;
		private Jid jid;
		private long pgpKeyId = 0;

		public String getName() {
			return name;
		}

		public void setName(String user) {
			this.name = user;
		}

		public void setJid(Jid jid) {
			this.jid = jid;
		}

		public Jid getJid() {
			return this.jid;
		}

		public Role getRole() {
			return this.role;
		}

		public void setRole(String role) {
			role = role.toLowerCase();
			switch (role) {
				case "moderator":
					this.role = Role.MODERATOR;
					break;
				case "participant":
					this.role = Role.PARTICIPANT;
					break;
				case "visitor":
					this.role = Role.VISITOR;
					break;
				default:
					this.role = Role.NONE;
					break;
			}
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			} else if (!(other instanceof User)) {
				return false;
			} else {
				User o = (User) other;
				return name != null && name.equals(o.name)
						&& jid != null && jid.equals(o.jid)
						&& affiliation == o.affiliation
						&& role == o.role;
			}
		}

		public Affiliation getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			affiliation = affiliation.toLowerCase();
			switch (affiliation) {
				case "admin":
					this.affiliation = Affiliation.ADMIN;
					break;
				case "owner":
					this.affiliation = Affiliation.OWNER;
					break;
				case "member":
					this.affiliation = Affiliation.MEMBER;
					break;
				case "outcast":
					this.affiliation = Affiliation.OUTCAST;
					break;
				default:
					this.affiliation = Affiliation.NONE;
			}
		}

		public void setPgpKeyId(long id) {
			this.pgpKeyId = id;
		}

		public long getPgpKeyId() {
			return this.pgpKeyId;
		}

		public Contact getContact() {
			return account.getRoster().getContactFromRoster(getJid());
		}
	}

	private Account account;
	private List<User> users = new CopyOnWriteArrayList<>();
	private List<String> features = new ArrayList<>();
	private Data form = new Data();
	private Conversation conversation;
	private boolean isOnline = false;
	private int error = ERROR_UNKNOWN;
	private OnRenameListener onRenameListener = null;
	private User self = new User();
	private String subject = null;
	private String password = null;
	private boolean mNickChangingInProgress = false;

	public MucOptions(Conversation conversation) {
		this.account = conversation.getAccount();
		this.conversation = conversation;
	}

	public void updateFeatures(ArrayList<String> features) {
		this.features.clear();
		this.features.addAll(features);
	}

	public void updateFormData(Data form) {
		this.form = form;
	}

	public boolean hasFeature(String feature) {
		return this.features.contains(feature);
	}

	public boolean canInvite() {
		Field field = this.form.getFieldByName("muc#roomconfig_allowinvites");
		return !membersOnly() || self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean canChangeSubject() {
		Field field = this.form.getFieldByName("muc#roomconfig_changesubject");
		return self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean participating() {
		return !online() || self.getRole().ranks(Role.PARTICIPANT);
	}

	public boolean membersOnly() {
		return hasFeature("muc_membersonly");
	}

	public boolean mamSupport() {
		// Update with "urn:xmpp:mam:1" once we support it
		return hasFeature("urn:xmpp:mam:0");
	}

	public boolean nonanonymous() {
		return hasFeature("muc_nonanonymous");
	}

	public boolean persistent() {
		return hasFeature("muc_persistent");
	}

	public boolean moderated() {
		return hasFeature("muc_moderated");
	}

	public void deleteUser(String name) {
		for (int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(name)) {
				users.remove(i);
				return;
			}
		}
	}

	public void addUser(User user) {
		for (int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(user.getName())) {
				users.set(i, user);
				return;
			}
		}
		users.add(user);
	}

	public boolean isUserInRoom(String name) {
		for (int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public void processPacket(PresencePacket packet, PgpEngine pgp) {
		final Jid from = packet.getFrom();
		if (!from.isBareJid()) {
			final String name = from.getResourcepart();
			final String type = packet.getAttribute("type");
			final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
			final List<String> codes = getStatusCodes(x);
			if (type == null) {
				User user = new User();
				if (x != null) {
					Element item = x.findChild("item");
					if (item != null && name != null) {
						user.setName(name);
						user.setAffiliation(item.getAttribute("affiliation"));
						user.setRole(item.getAttribute("role"));
						user.setJid(item.getAttributeAsJid("jid"));
						if (codes.contains(STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(this.conversation.getJid())) {
							this.isOnline = true;
							this.error = ERROR_NO_ERROR;
							self = user;
							if (mNickChangingInProgress) {
								if (onRenameListener != null) {
									onRenameListener.onSuccess();
								}
								mNickChangingInProgress = false;
							}
						} else {
							addUser(user);
						}
						if (pgp != null) {
							Element signed = packet.findChild("x", "jabber:x:signed");
							if (signed != null) {
								Element status = packet.findChild("status");
								String msg = status == null ? "" : status.getContent();
								long keyId = pgp.fetchKeyId(account, msg, signed.getContent());
								if (keyId != 0) {
									user.setPgpKeyId(keyId);
								}
							}
						}
					}
				}
			} else if (type.equals("unavailable")) {
				if (codes.contains(STATUS_CODE_SELF_PRESENCE) ||
						packet.getFrom().equals(this.conversation.getJid())) {
					if (codes.contains(STATUS_CODE_CHANGED_NICK)) {
						this.mNickChangingInProgress = true;
					} else if (codes.contains(STATUS_CODE_KICKED)) {
						setError(KICKED_FROM_ROOM);
					} else if (codes.contains(STATUS_CODE_BANNED)) {
						setError(ERROR_BANNED);
					} else if (codes.contains(STATUS_CODE_LOST_MEMBERSHIP)) {
						setError(ERROR_MEMBERS_ONLY);
					} else {
						setError(ERROR_UNKNOWN);
					}
				} else {
					deleteUser(name);
				}
			} else if (type.equals("error")) {
				Element error = packet.findChild("error");
				if (error != null && error.hasChild("conflict")) {
					if (isOnline) {
						if (onRenameListener != null) {
							onRenameListener.onFailure();
						}
					} else {
						setError(ERROR_NICK_IN_USE);
					}
				} else if (error != null && error.hasChild("not-authorized")) {
					setError(ERROR_PASSWORD_REQUIRED);
				} else if (error != null && error.hasChild("forbidden")) {
					setError(ERROR_BANNED);
				} else if (error != null && error.hasChild("registration-required")) {
					setError(ERROR_MEMBERS_ONLY);
				}
			}
		}
	}

	private void setError(int error) {
		this.isOnline = false;
		this.error = error;
	}

	private List<String> getStatusCodes(Element x) {
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

	public List<User> getUsers() {
		return this.users;
	}

	public String getProposedNick() {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getNick() != null
				&& !conversation.getBookmark().getNick().isEmpty()) {
			return conversation.getBookmark().getNick();
		} else if (!conversation.getJid().isBareJid()) {
			return conversation.getJid().getResourcepart();
		} else {
			return account.getUsername();
		}
	}

	public String getActualNick() {
		if (this.self.getName() != null) {
			return this.self.getName();
		} else {
			return this.getProposedNick();
		}
	}

	public boolean online() {
		return this.isOnline;
	}

	public int getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.onRenameListener = listener;
	}

	public void setOffline() {
		this.users.clear();
		this.error = 0;
		this.isOnline = false;
	}

	public User getSelf() {
		return self;
	}

	public void setSubject(String content) {
		this.subject = content;
	}

	public String getSubject() {
		return this.subject;
	}

	public String createNameFromParticipants() {
		if (users.size() >= 2) {
			List<String> names = new ArrayList<String>();
			for (User user : users) {
				Contact contact = user.getContact();
				if (contact != null && !contact.getDisplayName().isEmpty()) {
					names.add(contact.getDisplayName().split("\\s+")[0]);
				} else {
					names.add(user.getName());
				}
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < names.size(); ++i) {
				builder.append(names.get(i));
				if (i != names.size() - 1) {
					builder.append(", ");
				}
			}
			return builder.toString();
		} else {
			return null;
		}
	}

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<>();
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				ids.add(user.getPgpKeyId());
			}
		}
		ids.add(account.getPgpId());
		long[] primitiveLongArray = new long[ids.size()];
		for (int i = 0; i < ids.size(); ++i) {
			primitiveLongArray[i] = ids.get(i);
		}
		return primitiveLongArray;
	}

	public boolean pgpKeysInUse() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				return true;
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() == 0) {
				return false;
			}
		}
		return true;
	}

	public Jid createJoinJid(String nick) {
		try {
			return Jid.fromString(this.conversation.getJid().toBareJid().toString() + "/" + nick);
		} catch (final InvalidJidException e) {
			return null;
		}
	}

	public Jid getTrueCounterpart(String counterpart) {
		for (User user : this.getUsers()) {
			if (user.getName().equals(counterpart)) {
				return user.getJid();
			}
		}
		return null;
	}

	public String getPassword() {
		this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
		if (this.password == null && conversation.getBookmark() != null
				&& conversation.getBookmark().getPassword() != null) {
			return conversation.getBookmark().getPassword();
		} else {
			return this.password;
		}
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
	}

	public Conversation getConversation() {
		return this.conversation;
	}
}
