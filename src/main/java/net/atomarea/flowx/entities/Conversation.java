package net.atomarea.flowx.entities;

import android.content.ContentValues;
import android.database.Cursor;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.xmpp.chatstate.ChatState;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class Conversation extends AbstractEntity implements Blockable {
	public static final String TABLENAME = "conversations";

	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;

	public static final int MODE_MULTI = 1;
	public static final int MODE_SINGLE = 0;

	public static final String NAME = "name";
	public static final String ACCOUNT = "accountUuid";
	public static final String CONTACT = "contactUuid";
	public static final String CONTACTJID = "contactJid";
	public static final String STATUS = "status";
	public static final String CREATED = "created";
	public static final String MODE = "mode";
	public static final String ATTRIBUTES = "attributes";

	public static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
	public static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
	public static final String ATTRIBUTE_MUTED_TILL = "muted_till";

	private String name;
	private String contactUuid;
	private String accountUuid;
	private Jid contactJid;
	private int status;
	private long created;
	private int mode;

	private JSONObject attributes = new JSONObject();

	private Jid nextCounterpart;

	protected final ArrayList<Message> messages = new ArrayList<>();
	protected Account account = null;

	private transient SessionImpl otrSession;

	private transient String otrFingerprint = null;
	private Smp mSmp = new Smp();

	private String nextMessage;

	private transient MucOptions mucOptions = null;

	private byte[] symmetricKey;

	private Bookmark bookmark;

	private boolean messagesLeftOnServer = true;
	private ChatState mOutgoingChatState = Config.DEFAULT_CHATSTATE;
	private ChatState mIncomingChatState = Config.DEFAULT_CHATSTATE;
	private String mLastReceivedOtrMessageId = null;

	public boolean hasMessagesLeftOnServer() {
		return messagesLeftOnServer;
	}

	public void setHasMessagesLeftOnServer(boolean value) {
		this.messagesLeftOnServer = value;
	}

	public Message findUnsentMessageWithUuid(String uuid) {
		synchronized(this.messages) {
			for (final Message message : this.messages) {
				final int s = message.getStatus();
				if ((s == Message.STATUS_UNSEND || s == Message.STATUS_WAITING) && message.getUuid().equals(uuid)) {
					return message;
				}
			}
		}
		return null;
	}

	public void findWaitingMessages(OnMessageFound onMessageFound) {
		synchronized (this.messages) {
			for(Message message : this.messages) {
				if (message.getStatus() == Message.STATUS_WAITING) {
					onMessageFound.onMessageFound(message);
				}
			}
		}
	}

	public void findUnreadMessages(OnMessageFound onMessageFound) {
		synchronized (this.messages) {
			for(Message message : this.messages) {
				if (!message.isRead()) {
					onMessageFound.onMessageFound(message);
				}
			}
		}
	}

	public void findMessagesWithFiles(final OnMessageFound onMessageFound) {
		synchronized (this.messages) {
			for (final Message message : this.messages) {
				if ((message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE)
						&& message.getEncryption() != Message.ENCRYPTION_PGP) {
					onMessageFound.onMessageFound(message);
				}
			}
		}
	}

	public Message findMessageWithFileAndUuid(final String uuid) {
		synchronized (this.messages) {
			for (final Message message : this.messages) {
				if ((message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE)
						&& message.getEncryption() != Message.ENCRYPTION_PGP
						&& message.getUuid().equals(uuid)) {
					return message;
				}
			}
		}
		return null;
	}

	public void clearMessages() {
		synchronized (this.messages) {
			this.messages.clear();
		}
	}

	public boolean setIncomingChatState(ChatState state) {
		if (this.mIncomingChatState == state) {
			return false;
		}
		this.mIncomingChatState = state;
		return true;
	}

	public ChatState getIncomingChatState() {
		return this.mIncomingChatState;
	}

	public boolean setOutgoingChatState(ChatState state) {
		if (mode == MODE_MULTI) {
			return false;
		}
		if (this.mOutgoingChatState != state) {
			this.mOutgoingChatState = state;
			return true;
		} else {
			return false;
		}
	}

	public ChatState getOutgoingChatState() {
		return this.mOutgoingChatState;
	}

	public void trim() {
		synchronized (this.messages) {
			final int size = messages.size();
			final int maxsize = Config.PAGE_SIZE * Config.MAX_NUM_PAGES;
			if (size > maxsize) {
				this.messages.subList(0, size - maxsize).clear();
			}
		}
	}

	public void findUnsentMessagesWithEncryption(int encryptionType, OnMessageFound onMessageFound) {
		synchronized (this.messages) {
			for (Message message : this.messages) {
				if ((message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_WAITING)
						&& (message.getEncryption() == encryptionType)) {
					onMessageFound.onMessageFound(message);
				}
			}
		}
	}

	public void findUnsentTextMessages(OnMessageFound onMessageFound) {
		synchronized (this.messages) {
			for (Message message : this.messages) {
				if (message.getType() != Message.TYPE_IMAGE
						&& message.getStatus() == Message.STATUS_UNSEND) {
					onMessageFound.onMessageFound(message);
				}
			}
		}
	}

	public Message findSentMessageWithUuidOrRemoteId(String id) {
		synchronized (this.messages) {
			for (Message message : this.messages) {
				if (id.equals(message.getUuid())
						|| (message.getStatus() >= Message.STATUS_SEND
						&& id.equals(message.getRemoteMsgId()))) {
					return message;
				}
			}
		}
		return null;
	}

	public Message findSentMessageWithUuid(String id) {
		synchronized (this.messages) {
			for (Message message : this.messages) {
				if (id.equals(message.getUuid())) {
					return message;
				}
			}
		}
		return null;
	}

	public void populateWithMessages(final List<Message> messages) {
		synchronized (this.messages) {
			messages.clear();
			messages.addAll(this.messages);
		}
		for(Iterator<Message> iterator = messages.iterator(); iterator.hasNext();) {
			if (iterator.next().wasMergedIntoPrevious()) {
				iterator.remove();
			}
		}
	}

	@Override
	public boolean isBlocked() {
		return getContact().isBlocked();
	}

	@Override
	public boolean isDomainBlocked() {
		return getContact().isDomainBlocked();
	}

	@Override
	public Jid getBlockedJid() {
		return getContact().getBlockedJid();
	}

	public String getLastReceivedOtrMessageId() {
		return this.mLastReceivedOtrMessageId;
	}

	public void setLastReceivedOtrMessageId(String id) {
		this.mLastReceivedOtrMessageId = id;
	}

	public int countMessages() {
		synchronized (this.messages) {
			return this.messages.size();
		}
	}

	public interface OnMessageFound {
		void onMessageFound(final Message message);
	}

	public Conversation(final String name, final Account account, final Jid contactJid,
						final int mode) {
		this(java.util.UUID.randomUUID().toString(), name, null, account
						.getUuid(), contactJid, System.currentTimeMillis(),
				STATUS_AVAILABLE, mode, "");
		this.account = account;
	}

	public Conversation(final String uuid, final String name, final String contactUuid,
						final String accountUuid, final Jid contactJid, final long created, final int status,
						final int mode, final String attributes) {
		this.uuid = uuid;
		this.name = name;
		this.contactUuid = contactUuid;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.created = created;
		this.status = status;
		this.mode = mode;
		try {
			this.attributes = new JSONObject(attributes == null ? "" : attributes);
		} catch (JSONException e) {
			this.attributes = new JSONObject();
		}
	}

	public boolean isRead() {
		return (this.messages.size() == 0) || this.messages.get(this.messages.size() - 1).isRead();
	}

	public List<Message> markRead() {
		final List<Message> unread = new ArrayList<>();
		synchronized (this.messages) {
			for(Message message : this.messages) {
				if (!message.isRead()) {
					message.markRead();
					unread.add(message);
				}
			}
		}
		return unread;
	}

	public Message getLatestMarkableMessage() {
		for (int i = this.messages.size() - 1; i >= 0; --i) {
			if (this.messages.get(i).getStatus() <= Message.STATUS_RECEIVED
					&& this.messages.get(i).markable) {
				if (this.messages.get(i).isRead()) {
					return null;
				} else {
					return this.messages.get(i);
				}
			}
		}
		return null;
	}

	public Message getLatestMessage() {
		if (this.messages.size() == 0) {
			Message message = new Message(this, "", Message.ENCRYPTION_NONE);
			message.setTime(getCreated());
			return message;
		} else {
			Message message = this.messages.get(this.messages.size() - 1);
			message.setConversation(this);
			return message;
		}
	}

	public String getName() {
		if (getMode() == MODE_MULTI) {
			if (getMucOptions().getSubject() != null) {
				return getMucOptions().getSubject();
			} else if (bookmark != null && bookmark.getBookmarkName() != null) {
				return bookmark.getBookmarkName();
			} else {
				String generatedName = getMucOptions().createNameFromParticipants();
				if (generatedName != null) {
					return generatedName;
				} else {
					return getJid().getLocalpart();
				}
			}
		} else {
			return this.getContact().getDisplayName();
		}
	}
	public String getParticipants() {
		if (getMode() == MODE_MULTI) {
			String generatedName = getMucOptions().createNameFromParticipants();
			if (generatedName != null) {
				return generatedName;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}
	public String getAccountUuid() {
		return this.accountUuid;
	}

	public Account getAccount() {
		return this.account;
	}

	public Contact getContact() {
		return this.account.getRoster().getContact(this.contactJid);
	}

	public void setAccount(final Account account) {
		this.account = account;
	}

	@Override
	public Jid getJid() {
		return this.contactJid;
	}

	public int getStatus() {
		return this.status;
	}

	public long getCreated() {
		return this.created;
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(NAME, name);
		values.put(CONTACT, contactUuid);
		values.put(ACCOUNT, accountUuid);
		values.put(CONTACTJID, contactJid.toString());
		values.put(CREATED, created);
		values.put(STATUS, status);
		values.put(MODE, mode);
		values.put(ATTRIBUTES, attributes.toString());
		return values;
	}

	public static Conversation fromCursor(Cursor cursor) {
		Jid jid;
		try {
			jid = Jid.fromString(cursor.getString(cursor.getColumnIndex(CONTACTJID)), true);
		} catch (final InvalidJidException e) {
			// Borked DB..
			jid = null;
		}
		return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(NAME)),
				cursor.getString(cursor.getColumnIndex(CONTACT)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				jid,
				cursor.getLong(cursor.getColumnIndex(CREATED)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(MODE)),
				cursor.getString(cursor.getColumnIndex(ATTRIBUTES)));
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getMode() {
		return this.mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}

	public SessionImpl startOtrSession(String presence, boolean sendStart) {
		if (this.otrSession != null) {
			return this.otrSession;
		} else {
			final SessionID sessionId = new SessionID(this.getJid().toBareJid().toString(),
					presence,
					"xmpp");
			this.otrSession = new SessionImpl(sessionId, getAccount().getOtrService());
			try {
				if (sendStart) {
					this.otrSession.startSession();
					return this.otrSession;
				}
				return this.otrSession;
			} catch (OtrException e) {
				return null;
			}
		}

	}

	public SessionImpl getOtrSession() {
		return this.otrSession;
	}

	public void resetOtrSession() {
		this.otrFingerprint = null;
		this.otrSession = null;
		this.mSmp.hint = null;
		this.mSmp.secret = null;
		this.mSmp.status = Smp.STATUS_NONE;
	}

	public Smp smp() {
		return mSmp;
	}

	public void startOtrIfNeeded() {
		if (this.otrSession != null
				&& this.otrSession.getSessionStatus() != SessionStatus.ENCRYPTED) {
			try {
				this.otrSession.startSession();
			} catch (OtrException e) {
				this.resetOtrSession();
			}
		}
	}

	public boolean endOtrIfNeeded() {
		if (this.otrSession != null) {
			if (this.otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
				try {
					this.otrSession.endSession();
					this.resetOtrSession();
					return true;
				} catch (OtrException e) {
					this.resetOtrSession();
					return false;
				}
			} else {
				this.resetOtrSession();
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean hasValidOtrSession() {
		return this.otrSession != null;
	}

	public synchronized String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				if (getOtrSession() == null || getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
					return null;
				}
				DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession().getRemotePublicKey();
				this.otrFingerprint = getAccount().getOtrService().getFingerprint(remotePubKey);
			} catch (final OtrCryptoException | UnsupportedOperationException ignored) {
				return null;
			}
		}
		return this.otrFingerprint;
	}

	public boolean verifyOtrFingerprint() {
		final String fingerprint = getOtrFingerprint();
		if (fingerprint != null) {
			getContact().addOtrFingerprint(fingerprint);
			return true;
		} else {
			return false;
		}
	}

	public boolean isOtrFingerprintVerified() {
		return getContact().getOtrFingerprints().contains(getOtrFingerprint());
	}

	/**
	 * short for is Private and Non-anonymous
	 */
	public boolean isPnNA() {
		return mode == MODE_SINGLE || (getMucOptions().membersOnly() && getMucOptions().nonanonymous());
	}

	public synchronized MucOptions getMucOptions() {
		if (this.mucOptions == null) {
			this.mucOptions = new MucOptions(this);
		}
		return this.mucOptions;
	}

	public void resetMucOptions() {
		this.mucOptions = null;
	}

	public void setContactJid(final Jid jid) {
		this.contactJid = jid;
	}

	public void setNextCounterpart(Jid jid) {
		this.nextCounterpart = jid;
	}

	public Jid getNextCounterpart() {
		return this.nextCounterpart;
	}

	private int getMostRecentlyUsedOutgoingEncryption() {
		synchronized (this.messages) {
			for(int i = this.messages.size() -1; i >= 0; --i) {
				final Message m = this.messages.get(i);
				if (!m.isCarbon() && m.getStatus() != Message.STATUS_RECEIVED) {
					final int e = m.getEncryption();
					if (e == Message.ENCRYPTION_DECRYPTED || e == Message.ENCRYPTION_DECRYPTION_FAILED) {
						return Message.ENCRYPTION_PGP;
					} else {
						return e;
					}
				}
			}
		}
		return Message.ENCRYPTION_NONE;
	}

	private int getMostRecentlyUsedIncomingEncryption() {
		synchronized (this.messages) {
			for(int i = this.messages.size() -1; i >= 0; --i) {
				final Message m = this.messages.get(i);
				if (m.getStatus() == Message.STATUS_RECEIVED) {
					final int e = m.getEncryption();
					if (e == Message.ENCRYPTION_DECRYPTED || e == Message.ENCRYPTION_DECRYPTION_FAILED) {
						return Message.ENCRYPTION_PGP;
					} else {
						return e;
					}
				}
			}
		}
		return Message.ENCRYPTION_NONE;
	}

	public int getNextEncryption() {
		final AxolotlService axolotlService = getAccount().getAxolotlService();
		int next = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, -1);
		if (next == -1) {
			if (Config.X509_VERIFICATION && mode == MODE_SINGLE) {
								if (axolotlService != null && axolotlService.isContactAxolotlCapable(getContact())) {
										return Message.ENCRYPTION_AXOLOTL;
									} else {
										return Message.ENCRYPTION_NONE;
									}
							}
			int outgoing = this.getMostRecentlyUsedOutgoingEncryption();
			if (outgoing == Message.ENCRYPTION_NONE) {
				next = this.getMostRecentlyUsedIncomingEncryption();
			} else {
				next = outgoing;
			}
		}
		if (Config.FORCE_ENCRYPTION && mode == MODE_SINGLE && next <= 0) {
			if (axolotlService != null && axolotlService.isContactAxolotlCapable(getContact())) {
				return Message.ENCRYPTION_AXOLOTL;
			} else {
				return Message.ENCRYPTION_OTR;
			}
		}
		return next;
	}

	public void setNextEncryption(int encryption) {
		this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, String.valueOf(encryption));
	}

	public String getNextMessage() {
		if (this.nextMessage == null) {
			return "";
		} else {
			return this.nextMessage;
		}
	}

	public boolean smpRequested() {
		return smp().status == Smp.STATUS_CONTACT_REQUESTED;
	}

	public void setNextMessage(String message) {
		this.nextMessage = message;
	}

	public void setSymmetricKey(byte[] key) {
		this.symmetricKey = key;
	}

	public byte[] getSymmetricKey() {
		return this.symmetricKey;
	}

	public void setBookmark(Bookmark bookmark) {
		this.bookmark = bookmark;
		this.bookmark.setConversation(this);
	}

	public void deregisterWithBookmark() {
		if (this.bookmark != null) {
			this.bookmark.setConversation(null);
		}
	}

	public Bookmark getBookmark() {
		return this.bookmark;
	}

	public boolean hasDuplicateMessage(Message message) {
		synchronized (this.messages) {
			for (int i = this.messages.size() - 1; i >= 0; --i) {
				if (this.messages.get(i).equals(message)) {
					return true;
				}
			}
		}
		return false;
	}

	public Message findSentMessageWithBody(String body) {
		synchronized (this.messages) {
			for (int i = this.messages.size() - 1; i >= 0; --i) {
				Message message = this.messages.get(i);
				if ((message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) && message.getBody() != null && message.getBody().equals(body)) {
					return message;
				}
			}
			return null;
		}
	}

	public long getLastMessageTransmitted() {
		synchronized (this.messages) {
			for(int i = this.messages.size() - 1; i >= 0; --i) {
				Message message = this.messages.get(i);
				if (message.getStatus() == Message.STATUS_RECEIVED || message.isCarbon()) {
					return message.getTimeSent();
				}
			}
		}
		return 0;
	}

	public void setMutedTill(long value) {
		this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
	}

	public boolean isMuted() {
		return System.currentTimeMillis() < this.getLongAttribute(ATTRIBUTE_MUTED_TILL, 0);
	}

	public boolean setAttribute(String key, String value) {
		try {
			this.attributes.put(key, value);
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	public String getAttribute(String key) {
		try {
			return this.attributes.getString(key);
		} catch (JSONException e) {
			return null;
		}
	}

	public int getIntAttribute(String key, int defaultValue) {
		String value = this.getAttribute(key);
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	public long getLongAttribute(String key, long defaultValue) {
		String value = this.getAttribute(key);
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	public void add(Message message) {
		message.setConversation(this);
		synchronized (this.messages) {
			this.messages.add(message);
		}
	}

	public void addAll(int index, List<Message> messages) {
		synchronized (this.messages) {
			this.messages.addAll(index, messages);
		}
		account.getPgpDecryptionService().addAll(messages);
	}

	public void sort() {
		synchronized (this.messages) {
			Collections.sort(this.messages, new Comparator<Message>() {
				@Override
				public int compare(Message left, Message right) {
					if (left.getTimeSent() < right.getTimeSent()) {
						return -1;
					} else if (left.getTimeSent() > right.getTimeSent()) {
						return 1;
					} else {
						return 0;
					}
				}
			});
			for(Message message : this.messages) {
				message.untie();
			}
		}
	}

	public int unreadCount() {
		synchronized (this.messages) {
			int count = 0;
			for(int i = this.messages.size() - 1; i >= 0; --i) {
				if (this.messages.get(i).isRead()) {
					return count;
				}
				++count;
			}
			return count;
		}
	}

	public class Smp {
		public static final int STATUS_NONE = 0;
		public static final int STATUS_CONTACT_REQUESTED = 1;
		public static final int STATUS_WE_REQUESTED = 2;
		public static final int STATUS_FAILED = 3;
		public static final int STATUS_VERIFIED = 4;

		public String secret = null;
		public String hint = null;
		public int status = 0;
	}
}
