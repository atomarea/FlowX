package net.atomarea.flowx.data;

import android.content.Context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Tom on 04.08.2016.
 */
public class Data implements Serializable {

    public static final String EXTRA_TOKEN = "data";
    public static final String EXTRA_CHAT_HISTORY_POSITION = "001";

    private ArrayList<Account> Contacts;
    private ArrayList<ChatHistory> Chats;

    public Data(Context context) {
        Contacts = new ArrayList<>();
        Chats = new ArrayList<>();

        Contacts.add(new Account(context, "test@flowx.im"));
        Contacts.add(new Account(context, "lol@flowx.im"));
        Contacts.add(new Account(context, "othertest@flowx.im"));
        Contacts.add(new Account(context, "yoloo@flowx.im"));

        Chats.add(new ChatHistory("01", Contacts.get(0)));
        Chats.add(new ChatHistory("02", Contacts.get(2)));
        Chats.add(new ChatHistory("03", Contacts.get(3)));

        for (int i = 0; i < 100; i++) {
            Chats.get(0).getChatMessages().add(new ChatMessage("Some <b>Message</b> " + Math.random(), ChatMessage.Type.Text, true, System.currentTimeMillis()));
            Chats.get(0).getChatMessages().add(new ChatMessage("Some <i>Message again</i> " + Math.random(), ChatMessage.Type.Text, false, System.currentTimeMillis()));
        }

        for (int i = 0; i < 10; i++) {
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <b>Message</b> " + Math.random(), ChatMessage.Type.Text, true, System.currentTimeMillis()));
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <i>Message again</i> " + Math.random(), ChatMessage.Type.Text, false, System.currentTimeMillis()));
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <b>Message</b> " + Math.random(), ChatMessage.Type.Text, true, System.currentTimeMillis()));
        }

        Iterator<ChatHistory> chatHistoryIterator = Chats.iterator();
        while (chatHistoryIterator.hasNext()) {
            if (chatHistoryIterator.next().getChatMessages().size() == 0)
                chatHistoryIterator.remove();
        }
    }

    public ArrayList<Account> getContacts() {
        return Contacts;
    }

    public ArrayList<ChatHistory> getChats() {
        return Chats;
    }
}
