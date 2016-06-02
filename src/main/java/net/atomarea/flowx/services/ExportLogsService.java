package net.atomarea.flowx.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.persistance.DatabaseBackend;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.ui.UpdaterActivity;
import net.atomarea.flowx.xmpp.jid.Jid;

public class ExportLogsService extends Service {

    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String DIRECTORY_STRING_FORMAT = FileBackend.getConversationsDirectory() + "/chats/%s";
    private static final String MESSAGE_STRING_FORMAT = "(%s) %s: %s\n";
    private static final int NOTIFICATION_ID = 1;
    private static AtomicBoolean running = new AtomicBoolean(false);
    private DatabaseBackend mDatabaseBackend;
    private List<Account> mAccounts;

    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        mAccounts = mDatabaseBackend.getAccounts();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running.compareAndSet(false, true)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ExportDatabase();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    export();
                    stopForeground(true);
                    running.set(false);
                    stopSelf();
                }
            }).start();
        }
        return START_NOT_STICKY;
    }

    private void export() {
        List<Conversation> conversations = mDatabaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
        conversations.addAll(mDatabaseBackend.getConversations(Conversation.STATUS_ARCHIVED));
        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext());
        mBuilder.setContentTitle(getString(R.string.notification_export_logs_title))
                .setSmallIcon(R.drawable.ic_import_export_white_24dp)
                .setProgress(conversations.size(), 0, false);
        startForeground(NOTIFICATION_ID, mBuilder.build());

        int progress = 0;
        for (Conversation conversation : conversations) {
            writeToFile(conversation);
            progress++;
            mBuilder.setProgress(conversations.size(), progress, false);
            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
    }

    private void writeToFile(Conversation conversation) {
        Jid accountJid = resolveAccountUuid(conversation.getAccountUuid());
        Jid contactJid = conversation.getJid();

        File dir = new File(String.format(DIRECTORY_STRING_FORMAT,accountJid.toBareJid().toString()));
        dir.mkdirs();

        BufferedWriter bw = null;
        try {
            for (Message message : mDatabaseBackend.getMessagesIterable(conversation)) {
                if (message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) {
                    String date = simpleDateFormat.format(new Date(message.getTimeSent()));
                    if (bw == null) {
                        bw = new BufferedWriter(new FileWriter(
                                new File(dir, contactJid.toBareJid().toString() + ".txt")));
                    }
                    String jid = null;
                    switch (message.getStatus()) {
                        case Message.STATUS_RECEIVED:
                            jid = getMessageCounterpart(message);
                            break;
                        case Message.STATUS_SEND:
                        case Message.STATUS_SEND_RECEIVED:
                        case Message.STATUS_SEND_DISPLAYED:
                            jid = accountJid.toBareJid().toString();
                            break;
                    }
                    if (jid != null) {
                        String body = message.hasFileOnRemoteHost() ? message.getFileParams().url.toString() : message.getBody();
                        bw.write(String.format(MESSAGE_STRING_FORMAT, date, jid,
                                body.replace("\\\n", "\\ \n").replace("\n", "\\ \n")));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private Jid resolveAccountUuid(String accountUuid) {
        for (Account account : mAccounts) {
            if (account.getUuid().equals(accountUuid)) {
                return account.getJid();
            }
        }
        return null;
    }

    private String getMessageCounterpart(Message message) {
        String trueCounterpart = (String) message.getContentValues().get(Message.TRUE_COUNTERPART);
        if (trueCounterpart != null) {
            return trueCounterpart;
        } else {
            return message.getCounterpart().toString();
        }
    }

    public void ExportDatabase() throws IOException {

        // Get hold of the db:
        InputStream myInput = new FileInputStream(this.getDatabasePath(DatabaseBackend.DATABASE_NAME));

        // Set the output folder on the SDcard
        File directory = new File(FileBackend.getConversationsDirectory() + "/.Database/");

        // Create the folder if it doesn't exist:
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Set the output file stream up:
        OutputStream myOutput = new FileOutputStream(directory.getPath() + "/Database.bak");

        // Transfer bytes from the input file to the output file
        byte[] buffer = new byte[1024];
        int length;
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }

        // Close and clear the streams
        myOutput.flush();
        myOutput.close();
        myInput.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}