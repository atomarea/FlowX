package net.atomarea.flowx.database;

import android.provider.BaseColumns;

/**
 * Created by Tom on 08.08.2016.
 */
public final class ContactContract {

    public ContactContract() {
    }

    public static abstract class ContactEntry implements BaseColumns {
        public static final String TABLE_NAME = "contact_table";
        public static final String COLUMN_NAME_XMPP_ADDRESS = "xmpp_address";
        public static final String COLUMN_NAME_CUSTOM_NAME = "custom_name";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_LAST_ONLINE = "last_online";
    }

}
