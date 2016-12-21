package net.atomarea.flowx.parser;

import android.util.Log;
import android.util.Pair;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.crypto.axolotl.XmppAxolotlMessage;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Bookmark;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.MucOptions;
import net.atomarea.flowx.entities.Presence;
import net.atomarea.flowx.entities.ServiceDiscoveryResult;
import net.atomarea.flowx.http.HttpConnectionManager;
import net.atomarea.flowx.services.MessageArchiveService;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.utils.Xmlns;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.OnMessagePacketReceived;
import net.atomarea.flowx.xmpp.chatstate.ChatState;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.pep.Avatar;
import net.atomarea.flowx.xmpp.stanzas.MessagePacket;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

    private static final List<String> CLIENTS_SENDING_HTML_IN_OTR = Arrays.asList("Pidgin", "Adium", "Trillian");

    public MessageParser(XmppConnectionService service) {
        super(service);
    }

    private boolean extractChatState(Conversation conversation, final MessagePacket packet) {
        ChatState state = ChatState.parse(packet);
        if (state != null && conversation != null) {
            final Account account = conversation.getAccount();
            Jid from = packet.getFrom();
            if (from.toBareJid().equals(account.getJid().toBareJid())) {
                conversation.setOutgoingChatState(state);
                if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
                    mXmppConnectionService.markRead(conversation);
                    activateGracePeriod(account);
                }
                return false;
            } else {
                return conversation.setIncomingChatState(state);
            }
        }
        return false;
    }

    private static boolean clientMightSendHtml(Account account, Jid from) {
        String resource = from.getResourcepart();
        if (resource == null) {
            return false;
        }
        Presence presence = account.getRoster().getContact(from).getPresences().getPresences().get(resource);
        ServiceDiscoveryResult disco = presence == null ? null : presence.getServiceDiscoveryResult();
        if (disco == null) {
            return false;
        }
        return hasIdentityKnowForSendingHtml(disco.getIdentities());
    }

    private static boolean hasIdentityKnowForSendingHtml(List<ServiceDiscoveryResult.Identity> identities) {
        for (ServiceDiscoveryResult.Identity identity : identities) {
            if (identity.getName() != null) {
                if (CLIENTS_SENDING_HTML_IN_OTR.contains(identity.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Message parseAxolotlChat(Element axolotlMessage, Jid from, Conversation conversation, int status) {
        AxolotlService service = conversation.getAccount().getAxolotlService();
        XmppAxolotlMessage xmppAxolotlMessage;
        try {
            xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.toBareJid());
        } catch (Exception e) {
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().toBareJid() + ": invalid omemo message received " + e.getMessage());
            return null;
        }
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = service.processReceivingPayloadMessage(xmppAxolotlMessage);
        if (plaintextMessage != null) {
            Message finishedMessage = new Message(conversation, plaintextMessage.getPlaintext(), Message.ENCRYPTION_AXOLOTL, status);
            finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount()) + " Received Message with session fingerprint: " + plaintextMessage.getFingerprint());
            return finishedMessage;
        } else {
            return null;
        }
    }

    private class Invite {
        Jid jid;
        String password;

        Invite(Jid jid, String password) {
            this.jid = jid;
            this.password = password;
        }

        public boolean execute(Account account) {
            if (jid != null) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid, true);
                if (!conversation.getMucOptions().online()) {
                    conversation.getMucOptions().setPassword(password);
                    mXmppConnectionService.databaseBackend.updateConversation(conversation);
                    mXmppConnectionService.joinMuc(conversation);
                    mXmppConnectionService.updateConversationUi();
                }
                return true;
            }
            return false;
        }
    }

    private Invite extractInvite(Element message) {
        Element x = message.findChild("x", "http://jabber.org/protocol/muc#user");
        if (x != null) {
            Element invite = x.findChild("invite");
            if (invite != null) {
                Element pw = x.findChild("password");
                return new Invite(message.getAttributeAsJid("from"), pw != null ? pw.getContent() : null);
            }
        } else {
            x = message.findChild("x", "jabber:x:conference");
            if (x != null) {
                return new Invite(x.getAttributeAsJid("jid"), x.getAttribute("password"));
            }
        }
        return null;
    }

    private static String extractStanzaId(Element packet, Jid by) {
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("stanza-id")
                    && Xmlns.STANZA_IDS.equals(child.getNamespace())
                    && by.equals(child.getAttributeAsJid("by"))) {
                return child.getAttribute("id");
            }
        }
        return null;
    }

    private void parseEvent(final Element event, final Jid from, final Account account) {
        Element items = event.findChild("items");
        String node = items == null ? null : items.getAttribute("node");
        if ("urn:xmpp:avatar:metadata".equals(node)) {
            Avatar avatar = Avatar.parseMetadata(items);
            if (avatar != null) {
                avatar.owner = from.toBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (account.getJid().toBareJid().equals(from)) {
                        if (account.setAvatar(avatar.getFilename())) {
                            mXmppConnectionService.databaseBackend.updateAccount(account);
                        }
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else {
                        Contact contact = account.getRoster().getContact(from);
                        contact.setAvatar(avatar);
                        mXmppConnectionService.getAvatarService().clear(contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi();
                    }
                } else if (mXmppConnectionService.isDataSaverDisabled()) {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }
        } else if ("http://jabber.org/protocol/nick".equals(node)) {
            Element i = items.findChild("item");
            Element nick = i == null ? null : i.findChild("nick", "http://jabber.org/protocol/nick");
            if (nick != null && nick.getContent() != null) {
                Contact contact = account.getRoster().getContact(from);
                contact.setPresenceName(nick.getContent());
                mXmppConnectionService.getAvatarService().clear(account);
                mXmppConnectionService.updateConversationUi();
                mXmppConnectionService.updateAccountUi();
            }
        } else if (AxolotlService.PEP_DEVICE_LIST.equals(node)) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received PEP device list update from " + from + ", processing...");
            Element item = items.findChild("item");
            Set<Integer> deviceIds = mXmppConnectionService.getIqParser().deviceIds(item);
            AxolotlService axolotlService = account.getAxolotlService();
            axolotlService.registerDevices(from, deviceIds);
            mXmppConnectionService.updateAccountUi();
        }
    }

    private boolean handleErrorMessage(Account account, MessagePacket packet) {
        if (packet.getType() == MessagePacket.TYPE_ERROR) {
            Jid from = packet.getFrom();
            if (from != null) {
                Message message = mXmppConnectionService.markMessage(account,
                        from.toBareJid(),
                        packet.getId(),
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                if (message != null) {
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket original) {
        if (handleErrorMessage(account, original)) {
            return;
        }
        final MessagePacket packet;
        Long timestamp = null;
        final boolean isForwarded;
        boolean isCarbon = false;
        String serverMsgId = null;
        final Element fin = original.findChild("fin", "urn:xmpp:mam:0");
        if (fin != null) {
            mXmppConnectionService.getMessageArchiveService().processFin(fin, original.getFrom());
            return;
        }
        final Element result = original.findChild("result", "urn:xmpp:mam:0");
        final MessageArchiveService.Query query = result == null ? null : mXmppConnectionService.getMessageArchiveService().findQuery(result.getAttribute("queryid"));
        if (query != null && query.validFrom(original.getFrom())) {
            Pair<MessagePacket, Long> f = original.getForwardedMessagePacket("result", "urn:xmpp:mam:0");
            if (f == null) {
                return;
            }
            timestamp = f.second;
            packet = f.first;
            isForwarded = true;
            serverMsgId = result.getAttribute("id");
            query.incrementMessageCount();
        } else if (query != null) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received mam result from invalid sender");
            return;
        } else if (original.fromServer(account)) {
            Pair<MessagePacket, Long> f;
            f = original.getForwardedMessagePacket("received", "urn:xmpp:carbons:2");
            f = f == null ? original.getForwardedMessagePacket("sent", "urn:xmpp:carbons:2") : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
            isForwarded = isCarbon;
        } else {
            packet = original;
            isForwarded = false;
        }

        if (timestamp == null) {
            timestamp = AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }
        final String body = packet.getBody();
        final Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        final String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");
        final Element replaceElement = packet.findChild("replace", "urn:xmpp:message-correct:0");
        final Element oob = packet.findChild("x", "jabber:x:oob");
        final boolean isOob = oob != null && body != null && body.equals(oob.findChildContent("url"));
        final String replacementId = replaceElement == null ? null : replaceElement.getAttribute("id");
        final Element axolotlEncrypted = packet.findChild(XmppAxolotlMessage.CONTAINERTAG, AxolotlService.PEP_PREFIX);
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final String remoteMsgId = packet.getId();
        boolean notify = false;

        if (from == null) {
            Log.d(Config.LOGTAG, "no from in: " + packet.toString());
            return;
        }

        boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        boolean isProperlyAddressed = (to != null) && (!to.isBareJid() || account.countPresences() == 0);
        boolean isMucStatusMessage = from.isBareJid() && mucUserElement != null && mucUserElement.hasChild("status");
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            counterpart = to != null ? to : account.getJid();
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
        }

        Invite invite = extractInvite(packet);
        if (invite != null && invite.execute(account)) {
            return;
        }

        if (!isTypeGroupChat
                && query == null
                && extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), packet)) {
            mXmppConnectionService.updateConversationUi();
        }

        if ((body != null || pgpEncrypted != null || axolotlEncrypted != null) && !isMucStatusMessage) {
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat, query);
            final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;
            if (isTypeGroupChat) {
                if (counterpart.getResourcepart().equals(conversation.getMucOptions().getActualNick())) {
                    status = Message.STATUS_SEND_RECEIVED;
                    isCarbon = true; //not really carbon but received from another resource
                    if (mXmppConnectionService.markMessage(conversation, remoteMsgId, status)) {
                        return;
                    } else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
                        Message message = conversation.findSentMessageWithBody(packet.getBody());
                        if (message != null) {
                            mXmppConnectionService.markMessage(message, status);
                            return;
                        }
                    }
                } else {
                    status = Message.STATUS_RECEIVED;
                }
            }
            Message message = null;
            if (body != null && body.startsWith("?OTR")) {
                if (!isForwarded && !isTypeGroupChat && isProperlyAddressed && !conversationMultiMode) {

                } else {
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": ignoring OTR message from " + from + " isForwarded=" + Boolean.toString(isForwarded) + ", isProperlyAddressed=" + Boolean.valueOf(isProperlyAddressed));
                    message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
                }
            } else if (axolotlEncrypted != null && Config.supportOmemo()) {
                Jid origin;
                if (conversationMultiMode) {
                    final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                    origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                    if (origin == null) {
                        Log.d(Config.LOGTAG, "axolotl message in non anonymous conference received");
                        return;
                    }
                } else {
                    origin = from;
                }
                message = parseAxolotlChat(axolotlEncrypted, origin, conversation, status);
                if (message == null) {
                    return;
                }
                if (conversationMultiMode) {
                    message.setTrueCounterpart(origin);
                }
            } else {
                message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
            }

            if (serverMsgId == null) {
                final Jid by;
                final boolean safeToExtract;
                if (isTypeGroupChat) {
                    by = conversation.getJid().toBareJid();
                    safeToExtract = true; //conversation.getMucOptions().hasFeature(Xmlns.STANZA_IDS);
                } else {
                    by = account.getJid().toBareJid();
                    safeToExtract = true; //account.getXmppConnection().getFeatures().stanzaIds();
                }
                if (safeToExtract) {
                    serverMsgId = extractStanzaId(packet, by);
                }
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);
            message.setOob(isOob);
            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
            if (conversationMultiMode) {
                final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                Jid trueCounterpart;
                if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                    trueCounterpart = message.getTrueCounterpart();
                } else if (Config.PARSE_REAL_JID_FROM_MUC_MAM) {
                    trueCounterpart = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                } else {
                    trueCounterpart = fallback;
                }
                if (trueCounterpart != null && trueCounterpart.toBareJid().equals(account.getJid().toBareJid())) {
                    status = isTypeGroupChat ? Message.STATUS_SEND_RECEIVED : Message.STATUS_SEND;
                }
                message.setStatus(status);
                message.setTrueCounterpart(trueCounterpart);
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            } else {
                updateLastseen(account, from);
            }

            if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
                Message replacedMessage = conversation.findMessageWithRemoteIdAndCounterpart(replacementId,
                        counterpart,
                        message.getStatus() == Message.STATUS_RECEIVED,
                        message.isCarbon());
                if (replacedMessage != null) {
                    final boolean fingerprintsMatch = replacedMessage.getFingerprint() == null
                            || replacedMessage.getFingerprint().equals(message.getFingerprint());
                    final boolean trueCountersMatch = replacedMessage.getTrueCounterpart() != null
                            && replacedMessage.getTrueCounterpart().equals(message.getTrueCounterpart());
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    if (fingerprintsMatch && (trueCountersMatch || !conversationMultiMode) && !duplicate) {
                        Log.d(Config.LOGTAG, "replaced message '" + replacedMessage.getBody() + "' with '" + message.getBody() + "'");
                        synchronized (replacedMessage) {
                            final String uuid = replacedMessage.getUuid();
                            replacedMessage.setUuid(UUID.randomUUID().toString());
                            replacedMessage.setBody(message.getBody());
                            replacedMessage.setEdited(replacedMessage.getRemoteMsgId());
                            replacedMessage.setRemoteMsgId(remoteMsgId);
                            replacedMessage.setEncryption(message.getEncryption());
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
                                replacedMessage.markUnread();
                            }
                            mXmppConnectionService.updateMessage(replacedMessage, uuid);
                            mXmppConnectionService.getNotificationService().updateNotification(false);
                            if (mXmppConnectionService.confirmMessages() && remoteMsgId != null && !isForwarded && !isTypeGroupChat) {
                                sendMessageReceipts(account, packet);
                            }
                        }
                        return;
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received message correction but verification didn't check out");
                    }
                }
            }

            boolean checkForDuplicates = query != null
                    || (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                    || message.getType() == Message.TYPE_PRIVATE;
            if (checkForDuplicates && conversation.hasDuplicateMessage(message)) {
                Log.d(Config.LOGTAG, "skipping duplicate message from " + message.getCounterpart().toString() + " " + message.getBody());
                return;
            }

            if (query != null && query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
                conversation.prepend(message);
            } else {
                conversation.add(message);
            }

            if (query == null || query.getWith() == null) { //either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (query == null) {
                mXmppConnectionService.updateConversationUi();
            }

            if (mXmppConnectionService.confirmMessages()
                    && message.trusted()
                    && remoteMsgId != null
                    && !isForwarded
                    && !isTypeGroupChat) {
                sendMessageReceipts(account, packet);
            }
            if (message.getEncryption() == Message.ENCRYPTION_NONE || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
            final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
            if (message.trusted() && message.treatAsDownloadable() != Message.Decision.NEVER && manager.getAutoAcceptFileSize() > 0) {
                manager.createNewDownloadConnection(message);
            } else if (notify) {
                if (query == null) {
                    mXmppConnectionService.getNotificationService().push(message);
                } else if (query.getWith() == null) { // mam catchup
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                }
            }
        } else if (!packet.hasChild("body")) { //no body
            Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
            if (isTypeGroupChat) {
                if (packet.hasChild("subject")) {
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        String subject = packet.findChildContent("subject");
                        conversation.getMucOptions().setSubject(subject);
                        final Bookmark bookmark = conversation.getBookmark();
                        if (bookmark != null && bookmark.getBookmarkName() == null) {
                            if (bookmark.setBookmarkName(subject)) {
                                mXmppConnectionService.pushBookmarks(account);
                            }
                        }
                        mXmppConnectionService.updateConversationUi();
                        return;
                    }
                }
            }
            if (conversation != null && mucUserElement != null && from.isBareJid()) {
                for (Element child : mucUserElement.getChildren()) {
                    if ("status".equals(child.getName())) {
                        try {
                            int code = Integer.parseInt(child.getAttribute("code"));
                            if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
                                mXmppConnectionService.fetchConferenceConfiguration(conversation);
                                break;
                            }
                        } catch (Exception e) {
                            //ignored
                        }
                    } else if ("item".equals(child.getName())) {
                        MucOptions.User user = AbstractParser.parseItem(conversation, child);
                        Log.d(Config.LOGTAG, account.getJid() + ": changing affiliation for "
                                + user.getRealJid() + " to " + user.getAffiliation() + " in "
                                + conversation.getJid().toBareJid());
                        if (!user.realJidMatchesAccount()) {
                            conversation.getMucOptions().updateUser(user);
                            mXmppConnectionService.getAvatarService().clear(conversation);
                            mXmppConnectionService.updateMucRosterUi();
                            mXmppConnectionService.updateConversationUi();
                        }
                    }
                }
            }
        }


        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        if (received != null && !packet.fromAccount(account)) {
            mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }
        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            if (packet.fromAccount(account)) {
                Conversation conversation = mXmppConnectionService.find(account, counterpart.toBareJid());
                if (conversation != null) {
                    mXmppConnectionService.markRead(conversation);
                }
            } else {
                final Message displayedMessage = mXmppConnectionService.markMessage(account, from.toBareJid(), displayed.getAttribute("id"), Message.STATUS_SEND_DISPLAYED);
                Message message = displayedMessage == null ? null : displayedMessage.prev();
                while (message != null
                        && message.getStatus() == Message.STATUS_SEND_RECEIVED
                        && message.getTimeSent() < displayedMessage.getTimeSent()) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                    message = message.prev();
                }
            }
        }

        Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != null) {
            parseEvent(event, from, account);
        }

        String nick = packet.findChildContent("nick", "http://jabber.org/protocol/nick");
        if (nick != null) {
            Contact contact = account.getRoster().getContact(from);
            contact.setPresenceName(nick);
        }
    }

    private static Jid getTrueCounterpart(Element mucUserElement, Jid fallback) {
        final Element item = mucUserElement == null ? null : mucUserElement.findChild("item");
        Jid result = item == null ? null : item.getAttributeAsJid("jid");
        return result != null ? result : fallback;
    }

    private void sendMessageReceipts(Account account, MessagePacket packet) {
        ArrayList<String> receiptsNamespaces = new ArrayList<>();
        if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
            receiptsNamespaces.add("urn:xmpp:chat-markers:0");
        }
        if (packet.hasChild("request", "urn:xmpp:receipts")) {
            receiptsNamespaces.add("urn:xmpp:receipts");
        }
        if (receiptsNamespaces.size() > 0) {
            MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
                    packet,
                    receiptsNamespaces,
                    packet.getType());
            mXmppConnectionService.sendMessagePacket(account, receipt);
        }
    }

    private static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getPreferences().getLong("race_period_length", 144) * 1000;
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": activating grace period till " + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }
}
