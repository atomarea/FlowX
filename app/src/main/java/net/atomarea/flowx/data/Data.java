package net.atomarea.flowx.data;

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

    public static final String EXTRA_TOKEN = "data";
    public static final String EXTRA_CHAT_HISTORY_POSITION = "001";
    public static final String EXTRA_TOKEN_ACCOUNT = "002";

    private ArrayList<Account> Contacts;
    private ArrayList<ChatHistory> Chats;

    public Data(Context context) {
        Contacts = new ArrayList<>();
        Chats = new ArrayList<>();

        Contacts.add(new Account(context, "test@flowx.im", "Random status"));
        Contacts.add(new Account(context, "lol@flowx.im", "Random status4"));
        Contacts.add(new Account(context, "othertest@flowx.im", "Random status3"));
        Contacts.add(new Account(context, "yoloo@flowx.im", "Random status2"));

        Chats.add(new ChatHistory("01", Contacts.get(0)));
        Chats.add(new ChatHistory("02", Contacts.get(2)));
        Chats.add(new ChatHistory("03", Contacts.get(3)));

        Chats.get(0).getChatMessages().add(new ChatMessage("Hey! Look at <b>this</b> <i>cool</i> formatting <u>Tricks</u>!", ChatMessage.Type.Text, false, System.currentTimeMillis() - 1000 * 60 * 60 * 25));
        Chats.get(0).getChatMessages().add(new ChatMessage("Yeah! Freaking cool!", ChatMessage.Type.Text, true, System.currentTimeMillis() - 1000 * 60 * 20));
        ChatMessage a = new ChatMessage(null, ChatMessage.Type.Image, true, System.currentTimeMillis() - 1000 * 60 * 3);
        ChatMessage b = new ChatMessage(null, ChatMessage.Type.Image, false, System.currentTimeMillis());
        Chats.get(0).getChatMessages().add(a);
        Chats.get(0).getChatMessages().add(b);

        for (int i = 0; i < 3; i++) {
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <b>Message</b> " + Math.random(), ChatMessage.Type.Text, true, System.currentTimeMillis()));
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <i>Message again</i> " + Math.random(), ChatMessage.Type.Text, false, System.currentTimeMillis()));
            Chats.get(1).getChatMessages().add(new ChatMessage("Some <b>Message</b> " + Math.random(), ChatMessage.Type.Text, true, System.currentTimeMillis()));
        }
    }

    public void clean() {
        Iterator<ChatHistory> chatHistoryIterator = Chats.iterator();
        while (chatHistoryIterator.hasNext()) {
            if (chatHistoryIterator.next().getChatMessages().size() == 0)
                chatHistoryIterator.remove();
        }
    }

    public void refresh(Context context) {
        for (Account c : Contacts) {
            c.reloadName(context);
        }
    }

    public ArrayList<Account> getContacts() {
        return Contacts;
    }

    public ArrayList<ChatHistory> getChats() {
        return Chats;
    }

    public int getChatHistoryPosition(Account contact) {
        for (int i = 0; i < Chats.size(); i++) {
            if (Chats.get(i).getRemoteContact().getXmppAddress().equals(contact.getXmppAddress()))
                return i;
        }
        Chats.add(new ChatHistory(contact.getXmppAddress(), contact));
        return Chats.size() - 1;
    }

    public void loadBitmap(Context context, BitmapLoadedCallback callback, ChatMessage message) {
        if (!message.getType().equals(ChatMessage.Type.Image)) return;
        new AsyncBitmapLoaderTask(context, callback).execute(message);
    }

    public interface BitmapLoadedCallback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    public class AsyncBitmapLoaderTask extends AsyncTask<ChatMessage, Void, Bitmap> {

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
