package net.atomarea.flowx.database;

import android.provider.BaseColumns;

/**
 * Created by Tom on 08.08.2016.
 */
public class MessageContract {

    public MessageContract() {
    }

    public static abstract class MessageEntry implements BaseColumns {
        public static final String TABLE_NAME = "messages_table";
        public static final String COLUMN_NAME_MESSAGE_ID = "message_id";
        public static final String COLUMN_NAME_REMOTE_XMPP_ADDRESS = "xmpp_address";
        public static final String COLUMN_NAME_MESSAGE_TYPE = "message_type";
        public static final String COLUMN_NAME_MESSAGE_BODY = "message_body";
        public static final String COLUMN_NAME_SENT = "sent";
        public static final String COLUMN_NAME_TIME = "time";
        public static final String COLUMN_NAME_STATE = "state";

    }

}
