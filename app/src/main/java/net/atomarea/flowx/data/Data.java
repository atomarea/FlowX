package net.atomarea.flowx.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;

import net.atomarea.flowx.R;
import net.atomarea.flowx.database.ContactContract;
import net.atomarea.flowx.database.DatabaseHelper;
import net.atomarea.flowx.xmpp.ServerConnection;

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

    private static ServerConnection connection;

    private static Handler handler;

    public static void initMain() {
        handler = new Handler();
    }

    public static void init(Context context) {
        Contacts = new ArrayList<>();
        Chats = new ArrayList<>();

        recacheFromDb();
    }

    public static void recacheFromDb() {
        SQLiteDatabase db = DatabaseHelper.get().getReadableDatabase();
        // *** CONTACTS QUERY ***
        Cursor contactsCursor = db.query(ContactContract.ContactEntry.TABLE_NAME, new String[]{
                ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME,
                ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS,
                ContactContract.ContactEntry.COLUMN_NAME_STATUS,
                ContactContract.ContactEntry.COLUMN_NAME_LAST_ONLINE
        }, ContactContract.ContactEntry._ID + " LIKE ?", new String[]{"%"}, null, null, ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME + " ASC");
        boolean contactsState = contactsCursor.moveToFirst();
        while (contactsState) {
            String custom_name = contactsCursor.getString(contactsCursor.getColumnIndex(ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME));
            String xmpp_address = contactsCursor.getString(contactsCursor.getColumnIndex(ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS));
            String status = contactsCursor.getString(contactsCursor.getColumnIndex(ContactContract.ContactEntry.COLUMN_NAME_STATUS));
            String last_online = contactsCursor.getString(contactsCursor.getColumnIndex(ContactContract.ContactEntry.COLUMN_NAME_LAST_ONLINE));
            Contacts.add(new Account(custom_name, xmpp_address, status, last_online));
            contactsState = contactsCursor.moveToNext();
        }
    }

    public static ServerConnection getConnection() {
        return connection;
    }

    public static void setConnection(ServerConnection connection) {
        Data.connection = connection;
    }

    public static Handler getHandler() {
        return handler;
    }

    public static void clean() {
        if (Chats != null) {
            Iterator<ChatHistory> chatHistoryIterator = Chats.iterator();
            while (chatHistoryIterator.hasNext()) {
                if (chatHistoryIterator.next().getChatMessages().size() == 0)
                    chatHistoryIterator.remove();
            }
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

    public static ChatHistory getChatHistory(Account contact) {
        int pos = getChatHistoryPosition(contact);
        if (pos != -1) return Chats.get(pos);
        else {

        }
        return null;
    }

    public static int getAccountPosition(Account remoteContact) {
        for (int i = 0; i < Contacts.size(); i++) {
            if (remoteContact.equals(Contacts.get(i))) return i;
        }
        return -1;
    }

    public static Account getAccountByXmpp(String XmppAddress) {
        for (Account c : Contacts) {
            if (c.getXmppAddress().equals(XmppAddress)) return c;
        }
        return null;
    }

    public static ChatMessage getChatMessage(String ID) {
        for (ChatHistory ch : Chats) {
            for (ChatMessage cm : ch.getChatMessages()) {
                if (cm.getID().equals(ID)) return cm;
            }
        }
        return null;
    }

    public static boolean sendTextMessage(ChatHistory chatHistory, String message) {
        if (getConnection() != null) {
            ChatMessage chatMessage = new ChatMessage("FX" + System.currentTimeMillis(), message, ChatMessage.Type.Text, true, System.currentTimeMillis());
            chatHistory.getChatMessages().add(chatMessage);
            getConnection().sendMessage(chatHistory.getRemoteContact(), chatMessage);
            return true;
        }
        return false;
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
