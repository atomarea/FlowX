package net.atomarea.flowx.data;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatHistory implements Serializable {

    private String Identifier;
    private Account RemoteContact;
    private ArrayList<ChatMessage> ChatMessages;

    public ChatHistory(String Identifier, Account RemoteContact) {
        this.Identifier = Identifier;
        this.RemoteContact = RemoteContact;
        ChatMessages = new ArrayList<>();
    }

    public String getIdentifier() {
        return Identifier;
    }

    public void setIdentifier(String identifier) {
        Identifier = identifier;
    }

    public Account getRemoteContact() {
        return RemoteContact;
    }

    public void setRemoteContact(Account remoteContact) {
        RemoteContact = remoteContact;
    }

    public ArrayList<ChatMessage> getChatMessages() {
        return ChatMessages;
    }

    public ChatMessage getLatestChatMessage() {
        if (ChatMessages.size() - 1 < 0) return null;
        return ChatMessages.get(ChatMessages.size() - 1);
    }
}
