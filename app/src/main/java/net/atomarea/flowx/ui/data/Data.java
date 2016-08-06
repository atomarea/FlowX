package net.atomarea.flowx.ui.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import net.atomarea.flowx.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Tom on 04.08.2016.
 */
public class Data implements Serializable {

    public static final String EXTRA_CHAT_HISTORY_POSITION = "001";
    public static final String EXTRA_CONTACT_POSITION = "002";
    public static final String EXTRA_TOKEN_CHAT_MESSAGE = "003";

    private static ArrayList<Account> Contacts;
    private static ArrayList<ChatHistory> Chats;

    public static void init(Context context) {
        Contacts = new ArrayList<>();
        Chats = new ArrayList<>();

        Contacts.add(new Account(context, "test@flowx.im", "Random status"));
        Contacts.add(new Account(context, "lol@flowx.im", "Random status4"));
        Contacts.add(new Account(context, "othertest@flowx.im", "Random status3"));
        Contacts.add(new Account(context, "yoloo@flowx.im", "Random status2"));

        Chats.add(new ChatHistory("01", Contacts.get(0)));
        Chats.add(new ChatHistory("02", Contacts.get(2)));
        Chats.add(new ChatHistory("03", Contacts.get(3)));

        Chats.get(0).getChatMessages().add(new ChatMessage("Hey! <u>Look at</u> <b>these</b> <i>cool</i> formatting Tricks!", ChatMessage.Type.Text, true, System.currentTimeMillis() - 1000 * 62 * 60 * 26));
        Chats.get(0).getChatMessages().get(0).setState(ChatMessage.State.ReadByContact);
        Chats.get(0).getChatMessages().add(new ChatMessage("Yeah! Freaking cool!", ChatMessage.Type.Text, false, System.currentTimeMillis() - 1000 * 62 * 60 * 25));
        ChatMessage a = new ChatMessage(null, ChatMessage.Type.Image, true, System.currentTimeMillis() - 1000 * 62 * 59 * 25);
        a.setState(ChatMessage.State.DeliveredToContact);
        Chats.get(0).getChatMessages().add(a);
        a = new ChatMessage("That was a <b>very</b> nice place!", ChatMessage.Type.Text, true, System.currentTimeMillis() - 1000 * 62 * 23 * 14);
        a.setState(ChatMessage.State.DeliveredToServer);
        Chats.get(0).getChatMessages().add(a);
        a = new ChatMessage("<i>Really</i>", ChatMessage.Type.Text, true, System.currentTimeMillis() - 1000 * 43);
        a.setState(ChatMessage.State.NotDelivered);
        Chats.get(0).getChatMessages().add(a);

        Chats.get(1).getChatMessages().add(new ChatMessage(null, ChatMessage.Type.Audio, true, System.currentTimeMillis()));
        Chats.get(1).getChatMessages().add(new ChatMessage(null, ChatMessage.Type.Audio, false, System.currentTimeMillis()));
    }

    public static void clean() {
        Iterator<ChatHistory> chatHistoryIterator = Chats.iterator();
        while (chatHistoryIterator.hasNext()) {
            if (chatHistoryIterator.next().getChatMessages().size() == 0)
                chatHistoryIterator.remove();
        }
    }

    public static void refresh(Context context) {
        for (Account c : Contacts) {
            c.reloadName(context);
        }
    }

    public static ArrayList<Account> getContacts() {
        return Contacts;
    }

    public static ArrayList<ChatHistory> getChats() {
        return Chats;
    }

    public static int getChatHistoryPosition(Account contact) {
        for (int i = 0; i < Chats.size(); i++) {
            if (Chats.get(i).getRemoteContact().getXmppAddress().equals(contact.getXmppAddress()))
                return i;
        }
        Chats.add(new ChatHistory(contact.getXmppAddress(), contact));
        return Chats.size() - 1;
    }

    public static int getAccountPosition(Account remoteContact) {
        for (int i = 0; i < Contacts.size(); i++) {
            if (remoteContact.equals(Contacts.get(i))) return i;
        }
        return -1;
    }

    public static void loadBitmap(Context context, BitmapLoadedCallback callback, ChatMessage message) {
        if (!message.getType().equals(ChatMessage.Type.Image)) return;
        new AsyncBitmapLoaderTask(context, callback).execute(message);
    }

    public interface BitmapLoadedCallback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    public static class AsyncBitmapLoaderTask extends AsyncTask<ChatMessage, Void, Bitmap> {

        private Context context;
        private BitmapLoadedCallback callback;

        public AsyncBitmapLoaderTask(Context context, BitmapLoadedCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected Bitmap doInBackground(ChatMessage... params) {
            return BitmapFactory.decodeStream(context.getResources().openRawResource(R.raw.test_image));
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            callback.onBitmapLoaded(bitmap);
        }
    }

}
