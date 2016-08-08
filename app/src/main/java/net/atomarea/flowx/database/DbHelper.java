package net.atomarea.flowx.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.atomarea.flowx.data.ChatMessage;

/**
 * Created by Tom on 08.08.2016.
 */
public class DbHelper {

    public static void checkContact(SQLiteDatabase db, String xmppAddress, String customName) {
        Cursor contactCursor = db.query(ContactContract.ContactEntry.TABLE_NAME, new String[]{ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS}, ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS + " LIKE ?", new String[]{xmppAddress}, null, null, ContactContract.ContactEntry._ID + " ASC");
        if (contactCursor.getCount() != 1) {
            if (contactCursor.getCount() != 0)
                db.delete(ContactContract.ContactEntry.TABLE_NAME, ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS + " LIKE ?", new String[]{xmppAddress});
            ContentValues contactDetails = new ContentValues();
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS, xmppAddress);
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME, (customName == null ? xmppAddress.split("@")[0] : customName));
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_STATUS, "");
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_LAST_ONLINE, "0");
            db.insert(ContactContract.ContactEntry.TABLE_NAME, null, contactDetails);
        } else if (customName != null) {
            ContentValues contactDetails = new ContentValues();
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME, customName);
            db.update(ContactContract.ContactEntry.TABLE_NAME, contactDetails, ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS + " LIKE ?", new String[]{xmppAddress});
        }
        contactCursor.close();
    }

    public static void insertMessage(SQLiteDatabase db, String remoteXmppAddress, String messageId, String messageBody, ChatMessage.Type messageType, boolean isSent, long messageTime, ChatMessage.State state) {
        ContentValues messageDetails = new ContentValues();
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_REMOTE_XMPP_ADDRESS, remoteXmppAddress);
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_ID, messageId);
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_BODY, messageBody);
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_TYPE, messageType.name());
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_SENT, (isSent ? "1" : "0"));
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_STATE, state.name());
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_TIME, String.valueOf(messageTime));
        db.insert(MessageContract.MessageEntry.TABLE_NAME, null, messageDetails);
    }

    public static void updateMessage(SQLiteDatabase db, String remoteXmppAddress, String messageId, ChatMessage.State state) {
        ContentValues messageDetails = new ContentValues();
        messageDetails.put(MessageContract.MessageEntry.COLUMN_NAME_STATE, state.name());
        db.update(MessageContract.MessageEntry.TABLE_NAME, messageDetails, MessageContract.MessageEntry.COLUMN_NAME_REMOTE_XMPP_ADDRESS + " LIKE ? AND " + MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_ID + " LIKE ?", new String[]{remoteXmppAddress, messageId});
    }

    public static void updateContact(SQLiteDatabase db, String remoteXmppAddress, String status, long time) {
        ContentValues contactDetails = new ContentValues();
        if (status != null)
            contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_STATUS, status);
        contactDetails.put(ContactContract.ContactEntry.COLUMN_NAME_LAST_ONLINE, String.valueOf(time));
        db.update(ContactContract.ContactEntry.TABLE_NAME, contactDetails, ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS + " LIKE ?", new String[]{remoteXmppAddress});
    }

}
