package net.atomarea.flowx.xmpp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.atomarea.flowx.ServerConfig;
import net.atomarea.flowx.data.Account;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
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
    private String LocalUser;

    private Handler postHandler;

    public void login(String username, String password) throws SmackException, IOException, XMPPException {
        XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);
        config.setServiceName(ServerConfig.ServerIP);
        config.setHost(ServerConfig.ServerIP);
        config.setPort(ServerConfig.ServerPort);
        config.setDebuggerEnabled(true);
        config.setResource("FlowX-App");

        LocalUser = username + "@flowx.im/FlowX-App";

        xmppConnection = new XMPPTCPConnection(config.build());
        xmppConnection.addAsyncStanzaListener(this, null);
        xmppConnection.connect();
        xmppConnection.login(username, password);

        Data.setConnection(this);

        new Thread() { // TODO: CHANGE TO ANDROID SERVICE :)
            public void run() {
                Looper.prepare();
                postHandler = new Handler();

                while (true) {
                    Looper.loop();
                }
            }
        }.start();
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        String from = null;
        if (packet.getFrom() != null)
            from = packet.getFrom().split("/")[0];
        if (packet instanceof RosterPacket) {
            RosterPacket rosterPacket = (RosterPacket) packet;
            for (RosterPacket.Item i : rosterPacket.getRosterItems()) {
                Log.i(TAG, i.getName() + " " + i.getUser());
                Data.getContacts().add(new Account(i.getName(), i.getUser(), null));
            }
        } else if (packet instanceof Message) {
            Message message = (Message) packet;
            Log.i(TAG, "Message: " + message.getFrom() + " : " + message.getBody());
            if (message.getType().equals(Message.Type.chat) && message.getBody() != null) {
                Account contact = Data.getAccountByXmpp(from);
                if (contact != null) {
                    ChatHistory chatHistory = Data.getChatHistory(contact);
                    if (chatHistory != null) {
                        ChatMessage chatMessage = new ChatMessage(message.getStanzaId(), message.getBody(), ChatMessage.Type.Text, false, System.currentTimeMillis());
                        chatHistory.getChatMessages().add(chatMessage);
                    }
                }
            } else if (message.getBody() != null) {
                Log.i(TAG, "RECV " + message.getBody());
            } else {
                for (ExtensionElement ee : message.getExtensions()) {
                    if (ee.getElementName().equals("received")) {
                        ChatMessage chatMessage = Data.getChatMessage(message.getStanzaId());
                        if (chatMessage != null)
                            chatMessage.setState(ChatMessage.State.DeliveredToContact);
                    } else if (ee.getElementName().equals("displayed")) {

                        ChatMessage chatMessage = Data.getChatMessage(message.getStanzaId());
                        if (chatMessage != null)
                            chatMessage.setState(ChatMessage.State.ReadByContact);
                    }
                }
            }
        } else if (packet instanceof Presence) {
            Presence presence = (Presence) packet;
            Log.d(TAG, "Presence: " + presence.getFrom() + " " + presence.getStatus());
            Account contact = Data.getAccountByXmpp(from);
            if (contact != null)
                contact.setStatus(presence.getStatus());
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
                try {
                    xmppConnection.sendStanza(message);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void disconnect() {
        postHandler.post(new Runnable() {
            @Override
            public void run() {
                xmppConnection.disconnect();
            }
        });
    }

}
