package net.atomarea.flowx.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.ui.adapter.ConversationAdapter;
import net.atomarea.flowx.xmpp.XmppConnection;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;


public class ShareWithActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

    private boolean mReturnToPrevious = false;
    private static final String STATE_SHARING_IS_RUNNING = "state_sharing_is_running";
    static boolean ContactChosen = false;
    static boolean IntentReceived = false;
    boolean SharingIsRunning = false;

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    private class Share {
        public List<Uri> uris = new ArrayList<>();
        public boolean image;
        public boolean video;
        public String account;
        public String contact;
        public String text;
        public String uuid;
        public boolean multiple = false;
    }

    private Share share;

    private static final int REQUEST_START_NEW_CONVERSATION = 0x0501;
    private ListView mListView;
    private ConversationAdapter mAdapter;
    private List<Conversation> mConversations = new ArrayList<>();
    private Toast mToast;
    private AtomicInteger attachmentCounter = new AtomicInteger(0);

    private UiCallback<Message> attachFileCallback = new UiCallback<Message>() {

        @Override
        public void userInputRequried(PendingIntent pi, Message object) {
            // TODO Auto-generated method stub

        }

        @Override
        public void success(final Message message) {
            xmppConnectionService.sendMessage(message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (attachmentCounter.decrementAndGet() <= 0) {
                        int resId;
                        if (share.image && share.multiple) {
                            resId = R.string.shared_images_with_x;
                        } else if (share.image) {
                            resId = R.string.shared_image_with_x;
                        } else if (share.video) {
                            resId = R.string.shared_video_with_x;
                        } else {
                            resId = R.string.shared_file_with_x;
                        }
                        replaceToast(getString(resId, message.getConversation().getName()));
                        if (mReturnToPrevious) {
                            finish();
                        } else {
                            switchToConversation(message.getConversation());
                        }
                    }
                }
            });
        }

        @Override
        public void error(final int errorCode, Message object) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    replaceToast(getString(errorCode));
                    if (attachmentCounter.decrementAndGet() <= 0) {
                        finish();
                    }
                }
            });
        }
    };

    protected void hideToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    protected void replaceToast(String msg) {
        hideToast();
        mToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        mToast.show();
    }

    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_NEW_CONVERSATION
                && resultCode == RESULT_OK) {
            share.contact = data.getStringExtra("contact");
            share.account = data.getStringExtra(EXTRA_ACCOUNT);
        }
        if (xmppConnectionServiceBound
                && share != null
                && share.contact != null
                && share.account != null) {
            share();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }

        setContentView(R.layout.share_with);
        setTitle(getString(R.string.title_activity_sharewith));

        mListView = (ListView) findViewById(R.id.choose_conversation_list);
        mAdapter = new ConversationAdapter(this, this.mConversations);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                share(mConversations.get(position));
            }
        });

        if (savedInstanceState != null) {
            SharingIsRunning = savedInstanceState.getBoolean(STATE_SHARING_IS_RUNNING, false);
        }
        if (!SharingIsRunning) {
            Log.d(Config.LOGTAG, "ShareWithActivity onCreate: state restored");
            this.share = new Share();
        } else {
            Log.d(Config.LOGTAG, "ShareWithActivity onCreate: shring running, finish()");
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share_with, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                final Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
                startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        } else {
            IntentReceived = true;
        }
        Log.d(Config.LOGTAG, "ShareWithActivity onStart() getIntent " + intent.toString());
        this.mReturnToPrevious = getPreferences().getBoolean("return_to_previous", false);
        final String type = intent.getType();
        final String action = intent.getAction();
        Log.d(Config.LOGTAG, "action: " + action + ", type:" + type);
        share.uuid = intent.getStringExtra("uuid");
        if (Intent.ACTION_SEND.equals(action)) {
            final String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            Log.d(Config.LOGTAG, "ShareWithActivity onStart() Uri: " + uri);
            if (type != null && uri != null && (text == null || !type.equals("text/plain"))) {
                this.share.uris.clear();
                this.share.uris.add(uri);
                this.share.image = type.startsWith("image/") || isImage(uri);
                this.share.video = type.startsWith("video/") || isVideo(uri);
            } else {
                if (subject != null) {
                    this.share.text = format("[%s]%n%s", subject, text);
                } else {
                    this.share.text = text;
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            this.share.image = type != null && type.startsWith("image/");
            if (!this.share.image) {
                return;
            }
            this.share.uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }
        if (xmppConnectionServiceBound) {
            if (share.uuid != null) {
                share();
            } else {
                xmppConnectionService.populateWithOrderedConversations(mConversations, this.share.uris.size() == 0);
            }
        }

    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        Log.d(Config.LOGTAG, "ShareWithActivity onSaveInstanceState: IntentReceived: " + IntentReceived + " ContactChosen: " + ContactChosen);
        if (IntentReceived && ContactChosen) {
            Log.d(Config.LOGTAG, "ShareWithActivity onSaveInstanceState: state saved");
            savedInstanceState.putBoolean(STATE_SHARING_IS_RUNNING, true);
        } else {
            Log.d(Config.LOGTAG, "ShareWithActivity onSaveInstanceState: sharing is running, do nothing at this point");
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    protected boolean isImage(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("image/"));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return false;
        }
    }

    protected boolean isVideo(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("video/"));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return false;
        }
    }

    @Override
    void onBackendConnected() {
        if (xmppConnectionServiceBound && share != null
                && ((share.contact != null && share.account != null) || share.uuid != null)) {
            share();
            return;
        }
        refreshUiReal();
    }

    private void share() {
        final Conversation conversation;
        if (share.uuid != null) {
            conversation = xmppConnectionService.findConversationByUuid(share.uuid);
            if (conversation == null) {
                return;
            }
        } else {
            Account account;
            try {
                account = xmppConnectionService.findAccountByJid(Jid.fromString(share.account));
            } catch (final InvalidJidException e) {
                account = null;
            }
            if (account == null) {
                return;
            }

            try {
                conversation = xmppConnectionService
                        .findOrCreateConversation(account, Jid.fromString(share.contact), false);
            } catch (final InvalidJidException e) {
                return;
            }
        }
        ContactChosen = true;
        share(conversation);
    }

    private void share(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
        mListView.setEnabled(false);
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            if (share.uuid == null) {
            } else {
                Toast.makeText(this, R.string.openkeychain_not_installed, Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }
        if (share.uris.size() != 0) {
            OnPresenceSelected callback = new OnPresenceSelected() {
                @Override
                public void onPresenceSelected() {
                    attachmentCounter.set(share.uris.size());
                    if (share.image) {
                        Log.d(Config.LOGTAG, "ShareWithActivity share() image " + share.uris.size() + " uri(s) " + share.uris.toString());
                        share.multiple = share.uris.size() > 1;
                        replaceToast(getString(share.multiple ? R.string.preparing_images : R.string.preparing_image));
                        for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
                            ShareWithActivity.this.xmppConnectionService
                                    .attachImageToConversation(conversation, i.next(),
                                            attachFileCallback);
                        }
                    } else if (share.video) {
                        Log.d(Config.LOGTAG, "ShareWithActivity share() video " + share.uris.size() + " uri(s) " + share.uris.toString());
                        replaceToast(getString(R.string.preparing_video));
                        ShareWithActivity.this.xmppConnectionService
                                .attachVideoToConversation(conversation, share.uris.get(0),
                                        attachFileCallback);
                    } else {
                        Log.d(Config.LOGTAG, "ShareWithActivity share() file " + share.uris.size() + " uri(s) " + share.uris.toString());
                        replaceToast(getString(R.string.preparing_file));
                        ShareWithActivity.this.xmppConnectionService
                                .attachFileToConversation(conversation, share.uris.get(0),
                                        attachFileCallback);
                    }
                }
            };
            if (account.httpUploadAvailable()
                    && ((share.image && !neverCompressPictures())
                    || share.video
                    || conversation.getMode() == Conversation.MODE_MULTI
                    || FileBackend.allFilesUnderSize(this, share.uris, max))
                    && conversation.getNextEncryption() != Message.ENCRYPTION_OTR) {
                callback.onPresenceSelected();
            } else {
                selectPresence(conversation, callback);
            }
        } else {
            if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty()) {
                final OnPresenceSelected callback = new OnPresenceSelected() {
                    @Override
                    public void onPresenceSelected() {
                        Message message = new Message(conversation, share.text, conversation.getNextEncryption());
                        if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                            message.setCounterpart(conversation.getNextCounterpart());
                        }
                        xmppConnectionService.sendMessage(message);
                        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
                        finish();
                    }
                };
                if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                    selectPresence(conversation, callback);
                } else {
                    callback.onPresenceSelected();
                }
            } else {
                final OnPresenceSelected callback = new OnPresenceSelected() {
                    @Override
                    public void onPresenceSelected() {
                        Message message = new Message(conversation, share.text, conversation.getNextEncryption());
                        if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                            message.setCounterpart(conversation.getNextCounterpart());
                        }
                        xmppConnectionService.sendMessage(message);
                        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
                        switchToConversation(message.getConversation());
                    }
                };
                if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                    selectPresence(conversation, callback);
                } else {
                    callback.onPresenceSelected();
                }
            }
        }

    }

    public void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (attachmentCounter.get() >= 1) {
            replaceToast(getString(R.string.sharing_files_please_wait));
        } else {
            super.onBackPressed();
        }
    }
}