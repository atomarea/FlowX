package net.atomarea.flowx.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.database.DatabaseHelper;
import net.atomarea.flowx.database.MessageContract;

import java.util.ArrayList;

/**
 * Created by Tom on 08.08.2016.
 */
public class NotificationHandler {

    public static void create(Context context) {
        new NotificationLoaderTask(context).execute();
    }

    public static class NotificationLoaderTask extends AsyncTask<Void, Void, Notification> {

        private Context context;

        public NotificationLoaderTask(Context context) {
            this.context = context;
        }

        @Override
        protected Notification doInBackground(Void... params) {
            ArrayList<ChatMessage> unreadMessages = new ArrayList<>();

            SQLiteDatabase db = DatabaseHelper.get().getReadableDatabase();

            Cursor messagesCursor = db.query(MessageContract.MessageEntry.TABLE_NAME, new String[]{
                    MessageContract.MessageEntry.COLUMN_NAME_REMOTE_XMPP_ADDRESS,
                    MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_TYPE,
                    MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_BODY
            }, MessageContract.MessageEntry.COLUMN_NAME_STATE + " LIKE ? AND " + MessageContract.MessageEntry.COLUMN_NAME_SENT + " LIKE ?", new String[]{ChatMessage.State.DeliveredToContact.name(), "0"}, null, null, MessageContract.MessageEntry.COLUMN_NAME_TIME + " ASC");

            boolean messagesState = messagesCursor.moveToFirst();
            while (messagesState) {
                unreadMessages.add(new ChatMessage("0", messagesCursor.getString(messagesCursor.getColumnIndex(MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_BODY)), ChatMessage.Type.valueOf(messagesCursor.getString(messagesCursor.getColumnIndex(MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_TYPE))), false, 0));
                messagesState = messagesCursor.moveToNext();
            }

            messagesCursor.close();

            if (unreadMessages.size() == 0) return null;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setContentTitle(context.getResources().getString(R.string.app_name));
            builder.setSmallIcon(R.drawable.ic_launcher);
            NotificationCompat.InboxStyle style;
            builder.setStyle(style = new NotificationCompat.InboxStyle());
            for (int i = 0; i < 6 && i < unreadMessages.size(); i++) {
                style.addLine(unreadMessages.get(i).getData());
            }
            style.setSummaryText(context.getResources().getString(R.string.unread_messages, unreadMessages.size()));
            builder.setContentText(context.getResources().getString(R.string.unread_messages, unreadMessages.size()));

            return builder.build();
        }

        @Override
        protected void onPostExecute(Notification notification) {
            super.onPostExecute(notification);
            if (notification != null)
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notification);
        }
    }

}
