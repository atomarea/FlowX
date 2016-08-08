package net.atomarea.flowx.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Tom on 08.08.2016.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static DatabaseHelper databaseHelper;

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "FlowX.db";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ContactContract.ContactEntry.TABLE_NAME + " (" +
                ContactContract.ContactEntry._ID + " INTEGER PRIMARY KEY," +
                ContactContract.ContactEntry.COLUMN_NAME_XMPP_ADDRESS + " TEXT," +
                ContactContract.ContactEntry.COLUMN_NAME_CUSTOM_NAME + " TEXT," +
                ContactContract.ContactEntry.COLUMN_NAME_STATUS + " TEXT," +
                ContactContract.ContactEntry.COLUMN_NAME_LAST_ONLINE + " TEXT" +
                ")");
        db.execSQL("CREATE TABLE " + MessageContract.MessageEntry.TABLE_NAME + " (" +
                MessageContract.MessageEntry._ID + " INTEGER PRIMARY KEY," +
                MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_ID + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_REMOTE_XMPP_ADDRESS + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_TYPE + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_MESSAGE_BODY + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_SENT + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_STATE + " TEXT," +
                MessageContract.MessageEntry.COLUMN_NAME_TIME + " TEXT"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // simply delete on upgrade of db...
        db.execSQL("DROP TABLE IF EXISTS " + ContactContract.ContactEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MessageContract.MessageEntry.TABLE_NAME);
        onCreate(db);
    }

    private static Context applicationContext;

    public static void setApplicationContext(Context context) {
        applicationContext = context;
    }

    public static DatabaseHelper get() {
        if (databaseHelper == null) databaseHelper = new DatabaseHelper(applicationContext);
        return databaseHelper;
    }
}