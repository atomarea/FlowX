package net.atomarea.flowx.xmpp;

import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.util.Log;

import net.atomarea.flowx.ServerConfig;
import net.atomarea.flowx.data.Account;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.database.DatabaseHelper;
import net.atomarea.flowx.database.DbHelper;
import net.atomarea.flowx.notification.NotificationHandler;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by Tom on 06.08.2016.
 */
public class ServerConnection implements Serializable, StanzaListener {

    private static final String TAG = "FX XMPP";

    private XMPPTCPConnection xmppConnection;
    private String LocalUser, LocalUserWoRes;
    private boolean connectionDropped = false;

    // TODO <message id='185339fa-962f-418f-aa3b-6a763eea1f37' type='chat' to='tom@flowx.im' from='dom1nic@flowx.im/mobile'><body>https://flowx.im:5281/upload/35d21ccf-f9c0-4e79-966c-742279821c45/20160807_132659588_1853.jpg</body><markable xmlns='urn:xmpp:chat-markers:0'/><request xmlns='urn:xmpp:receipts'/><x xmlns='jabber:x:oob'><url>https://flowx.im:5281/upload/35d21ccf-f9c0-4e79-966c-742279821c45/20160807_132659588_1853.jpg</url></x></message>

    private Handler postHandler;

    public ServerConnection() {
        postHandler = new Handler();
    }

    public void login(final String username, final String password) throws SmackException, IOException, XMPPException {
        XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);
        config.setServiceName(ServerConfig.ServerIP);
        config.setHost(ServerConfig.ServerIP);
        config.setPort(ServerConfig.ServerPort);
        config.setDebuggerEnabled(false);
        config.setResource("FlowX-App");
        config.setConnectTimeout(10000);

        LocalUser = username + "@flowx.im/FlowX-App";
        LocalUserWoRes = username + "@flowx.im";

        ProviderManager.addExtensionProvider(ReceivedReceipt.ELEMENT, ReceivedReceipt.NAMESPACE, new ReceivedReceipt.Provider());
        ProviderManager.addExtensionProvider(ReadReceipt.ELEMENT, ReadReceipt.NAMESPACE, new ReadReceipt.Provider());
        ProviderManager.addExtensionProvider(ChatState.ELEMENT, ChatState.NAMESPACE, new ChatState.Provider());
        ProviderManager.removeIQProvider("vCard", "vcard-temp");
        ProviderManager.addIQProvider("vCard", "vcard-temp", new VCardProvider());

        xmppConnection = new XMPPTCPConnection(config.build());
        xmppConnection.addAsyncStanzaListener(this, null);
        xmppConnection.connect();
        xmppConnection.login(username, password);

        Data.setConnection(this);
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        String from = null;
        if (packet.getFrom() != null)
            from = packet.getFrom().split("/")[0];
        /*String to = null;
        if (packet.getTo() != null)
            to = packet.getTo().split("/")[0];*/
        if (packet instanceof RosterPacket) {
            RosterPacket rosterPacket = (RosterPacket) packet;
            SQLiteDatabase db = DatabaseHelper.get().getWritableDatabase();
            for (RosterPacket.Item i : rosterPacket.getRosterItems())
                DbHelper.checkContact(db, i.getUser(), i.getName());
            Data.doRefresh();
        } else if (packet instanceof Message) {
            Message message = (Message) packet;
            if (message.getType().equals(Message.Type.chat) && message.getBody() != null) {
                SQLiteDatabase db = DatabaseHelper.get().getWritableDatabase();
                DbHelper.checkContact(db, from, null);
                DbHelper.insertMessage(db, from, message.getStanzaId(), message.getBody(), ChatMessage.Type.Text, false, System.currentTimeMillis(), ChatMessage.State.DeliveredToContact);
                sendReceivedMarker(from, message.getStanzaId());
                Data.doRefresh();
                NotificationHandler.create(Data.getApplicationContext());
            } else if (message.getBody() != null) {
                Log.i(TAG, "RECV " + message.getBody());
            } else {
                for (ExtensionElement ee : message.getExtensions()) {
                    if (ee.getNamespace().equals(ReceivedReceipt.NAMESPACE)) {
                        ReceivedReceipt receivedReceipt = (ReceivedReceipt) ee;
                        DbHelper.updateMessage(DatabaseHelper.get().getWritableDatabase(), from, receivedReceipt.getID(), ChatMessage.State.DeliveredToContact);
                        Data.doRefresh();
                    }
                    if (ee.getNamespace().equals(ReadReceipt.NAMESPACE)) {
                        ReadReceipt readReceipt = (ReadReceipt) ee;
                        DbHelper.updateMessage(DatabaseHelper.get().getWritableDatabase(), from, readReceipt.getID(), ChatMessage.State.ReadByContact);
                        Data.doRefresh();
                    }
                    if (ee.getNamespace().equals(ChatState.NAMESPACE)) {
                        ChatState chatState = (ChatState) ee;
                        ChatHistory chatHistory = Data.getChatHistoryNullable(chatState.getXmppAddress());
                        if (chatHistory != null) {
                            chatHistory.setChatState(chatState.getState());
                            Data.doUiRefresh();
                        }
                    }
                }
            }
        } else if (packet instanceof Presence) {
            Presence presence = (Presence) packet;
            SQLiteDatabase db = DatabaseHelper.get().getWritableDatabase();
            DbHelper.updateContact(db, from, presence.getStatus(), System.currentTimeMillis());
            Data.doRefresh(true, false);
        } else Log.d(TAG, "Packet: " + packet.getClass().getSimpleName());
    }

    public void sendMessage(final Account contact, final ChatMessage chatMessage) {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                Message message = new Message();
                message.setFrom(LocalUser);
                message.setTo(contact.getXmppAddress());
                message.setBody(chatMessage.getData());
                message.setType(Message.Type.chat);
                message.setSubject(chatMessage.getType().name());
                message.setStanzaId(chatMessage.getID());
                if (send(message))
                    chatMessage.setState(ChatMessage.State.DeliveredToServer);
                Data.doUiRefresh();
            }
        });
    }

    public void sendReceivedMarker(final String xmppAddress, final String messageId) {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                Message message = new Message();
                message.setFrom(LocalUser);
                message.setTo(xmppAddress);
                message.setBody(null);
                message.setSubject(ReceivedReceipt.NAMESPACE);
                message.setType(Message.Type.chat);
                message.setStanzaId(messageId);
                message.addExtension(new ReceivedReceipt(messageId));
                send(message);
            }
        });
    }

    public void sendReadMarker(final ChatHistory chatHistory) {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ChatMessage chatMessage : chatHistory.getChatMessages()) {
                    if (!chatMessage.isSent()) {
                        if (!chatMessage.getState().equals(ChatMessage.State.ReadByContact)) {
                            Message message = new Message();
                            message.setFrom(LocalUser);
                            message.setTo(chatHistory.getRemoteContact().getXmppAddress());
                            message.setBody(null);
                            message.setSubject(ReadReceipt.NAMESPACE);
                            message.setType(Message.Type.chat);
                            message.setStanzaId(chatMessage.getID());
                            message.addExtension(new ReadReceipt(chatMessage.getID()));
                            DbHelper.updateMessage(DatabaseHelper.get().getWritableDatabase(), chatHistory.getRemoteContact().getXmppAddress(), chatMessage.getID(), ChatMessage.State.ReadByContact);
                            Data.doRefresh();
                            send(message);
                        }
                    }
                }
            }
        });
    }

    private long lastSentChatState = 0;

    public void sendChatState(final ChatHistory chatHistory, final ChatState.State state) {
        if (state.equals(ChatState.State.Idle) || System.currentTimeMillis() - 2000 > lastSentChatState)
            postHandler.post(new Runnable() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.setFrom(LocalUser);
                    message.setTo(chatHistory.getRemoteContact().getXmppAddress());
                    message.setBody(null);
                    message.setSubject(ChatState.NAMESPACE);
                    message.setType(Message.Type.chat);
                    message.setStanzaId("FXCS" + System.currentTimeMillis());
                    message.addExtension(new ChatState(LocalUserWoRes, state));
                    send(message);
                    lastSentChatState = System.currentTimeMillis();
                }
            });
    }

    public void sendContactsUpdate() {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                RosterPacket rp = new RosterPacket();
                rp.setFrom(LocalUser);
                for (Account contact : Data.getContacts()) {
                    rp.getRosterItems().add(new RosterPacket.Item(contact.getXmppAddress(), contact.getName()));
                }
                try {
                    xmppConnection.sendStanza(rp);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean send(Message message) {
        try {
            xmppConnection.sendStanza(message);
            return true;
        } catch (SmackException.NotConnectedException e) {
        }
        return false;
    }

    public void disconnect() {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                xmppConnection.disconnect();
                xmppConnection.instantShutdown(); // prevent cached messages to be sent before disconnect
                Log.i(TAG, "Connection was shut down");
            }
        });
    }

    public boolean hasDropped() {
        return connectionDropped;
    }

    public String getLocalUser() {
        return LocalUser;
    }

    public Handler getPostHandler() {
        return postHandler;
    }

    public XMPPTCPConnection getRawConnection() {
        return xmppConnection;
    }

}
