package net.atomarea.flowx.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.FileDescriptorBitmapDecoder;
import com.bumptech.glide.load.resource.bitmap.VideoBitmapDecoder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.XmppAxolotlSession;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.MucOptions;
import net.atomarea.flowx.entities.Presences;
import net.atomarea.flowx.services.AvatarService;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.services.XmppConnectionService.XmppConnectionBinder;
import net.atomarea.flowx.ui.widget.Switch;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.ExceptionHelper;
import net.atomarea.flowx.xmpp.OnKeyStatusUpdated;
import net.atomarea.flowx.xmpp.OnUpdateBlocklist;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.java.otr4j.session.SessionID;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public abstract class XmppActivity extends FragmentActivity {

    public static final String EXTRA_ACCOUNT = "account";
    protected static final int REQUEST_ANNOUNCE_PGP = 0x0101;
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x0102;
    protected static final int REQUEST_CHOOSE_PGP_ID = 0x0103;
    protected static final int REQUEST_BATTERY_OP = 0x13849ff;
    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    protected boolean registeredListeners = false;

    protected int mPrimaryTextColor;
    protected int mSecondaryTextColor;
    protected int mTertiaryTextColor;
    protected int mPrimaryBackgroundColor;
    protected int mSecondaryBackgroundColor;
    protected int mColorRed;
    protected int mColorOrange;
    protected int mColorGreen;
    protected int mPrimaryColor;
    protected boolean mUseSubject = true;
    protected int mTheme;
    protected boolean mUsingEnterKey = false;
    protected Toast mToast;
    protected void hideToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    protected void replaceToast(String msg) {
        hideToast();
        mToast = Toast.makeText(this, msg ,Toast.LENGTH_LONG);
        mToast.show();
    }

    protected Runnable onOpenPGPKeyPublished = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(XmppActivity.this, R.string.openpgp_has_been_published, Toast.LENGTH_SHORT).show();
        }
    };
    protected ConferenceInvite mPendingConferenceInvite = null;
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionBinder binder = (XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            if (!registeredListeners && shouldRegisterListeners()) {
                registerListeners();
                registeredListeners = true;
            }
            onBackendConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };
    private DisplayMetrics metrics;
    private long mLastUiRefresh = 0;
    private Handler mRefreshUiHandler = new Handler();
    private Runnable mRefreshUiRunnable = new Runnable() {
        @Override
        public void run() {
            mLastUiRefresh = SystemClock.elapsedRealtime();
            refreshUiReal();
        }
    };
    private UiCallback<Conversation> adhocCallback = new UiCallback<Conversation>() {
        @Override
        public void success(final Conversation conversation) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchToConversation(conversation);
                    hideToast();                }
            });
        }

        @Override
        public void error(final int errorCode, Conversation object) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    replaceToast(getString(errorCode));                }
            });
        }

        @Override
        public void userInputRequried(PendingIntent pi, Conversation object) {

        }
    };

    protected final void refreshUi() {
        final long diff = SystemClock.elapsedRealtime() - mLastUiRefresh;
        if (diff > Config.REFRESH_UI_INTERVAL) {
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            runOnUiThread(mRefreshUiRunnable);
        } else {
            final long next = Config.REFRESH_UI_INTERVAL - diff;
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            mRefreshUiHandler.postDelayed(mRefreshUiRunnable, next);
        }
    }

    abstract protected void refreshUiReal();

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            connectToBackend();
        } else {
            if (!registeredListeners) {
                this.registerListeners();
                this.registeredListeners = true;
            }
            this.onBackendConnected();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected boolean shouldRegisterListeners() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return !isDestroyed() && !isFinishing();
        } else {
            return !isFinishing();
        }
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            if (registeredListeners) {
                this.unregisterListeners();
                this.registeredListeners = false;
            }
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    protected void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();

        if (focus != null) {

            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public boolean hasPgp() {
        return xmppConnectionService.getPgpEngine() != null;
    }

    public void showInstallPgpDialog() {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.openkeychain_required));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getText(R.string.openkeychain_required_long));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setNeutralButton(getString(R.string.restart),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (xmppConnectionServiceBound) {
                            unbindService(mConnection);
                            xmppConnectionServiceBound = false;
                        }
                        stopService(new Intent(XmppActivity.this,
                                XmppConnectionService.class));
                        finish();
                    }
                });
        builder.setPositiveButton(getString(R.string.install),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri
                                .parse("market://details?id=org.sufficientlysecure.keychain");
                        Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                uri);
                        PackageManager manager = getApplicationContext()
                                .getPackageManager();
                        List<ResolveInfo> infos = manager
                                .queryIntentActivities(marketIntent, 0);
                        if (infos.size() > 0) {
                            startActivity(marketIntent);
                        } else {
                            uri = Uri.parse("http://www.openkeychain.org/");
                            Intent browserIntent = new Intent(
                                    Intent.ACTION_VIEW, uri);
                            startActivity(browserIntent);
                        }
                        finish();
                    }
                });
        builder.create().show();
    }

    abstract void onBackendConnected();

    protected void registerListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.setOnConversationListChangedListener((XmppConnectionService.OnConversationUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.setOnAccountListChangedListener((XmppConnectionService.OnAccountUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnCaptchaRequested) {
            this.xmppConnectionService.setOnCaptchaRequestedListener((XmppConnectionService.OnCaptchaRequested) this);
        }
        if (this instanceof XmppConnectionService.OnRosterUpdate) {
            this.xmppConnectionService.setOnRosterUpdateListener((XmppConnectionService.OnRosterUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
            this.xmppConnectionService.setOnMucRosterUpdateListener((XmppConnectionService.OnMucRosterUpdate) this);
        }
        if (this instanceof OnUpdateBlocklist) {
            this.xmppConnectionService.setOnUpdateBlocklistListener((OnUpdateBlocklist) this);
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.setOnShowErrorToastListener((XmppConnectionService.OnShowErrorToast) this);
        }
        if (this instanceof OnKeyStatusUpdated) {
            this.xmppConnectionService.setOnKeyStatusUpdatedListener((OnKeyStatusUpdated) this);
        }
    }

    protected void unregisterListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.removeOnConversationListChangedListener();
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.removeOnAccountListChangedListener();
        }
        if (this instanceof XmppConnectionService.OnCaptchaRequested) {
            this.xmppConnectionService.removeOnCaptchaRequestedListener();
        }
        if (this instanceof XmppConnectionService.OnRosterUpdate) {
            this.xmppConnectionService.removeOnRosterUpdateListener();
        }
        if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
            this.xmppConnectionService.removeOnMucRosterUpdateListener();
        }
        if (this instanceof OnUpdateBlocklist) {
            this.xmppConnectionService.removeOnUpdateBlocklistListener();
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.removeOnShowErrorToastListener();
        }
        if (this instanceof OnKeyStatusUpdated) {
            this.xmppConnectionService.removeOnNewKeysAvailableListener();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                final Intent intent = new Intent(getApplicationContext(), EditAccountActivity.class);
                Account mAccount = xmppConnectionService.getAccounts().get(0);
                intent.putExtra("jid", mAccount.getJid().toBareJid().toString());
                intent.putExtra("init", false);
                startActivity(intent);
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metrics = getResources().getDisplayMetrics();
        ExceptionHelper.init(getApplicationContext());
        mPrimaryTextColor = getResources().getColor(R.color.black87);
        mSecondaryTextColor = getResources().getColor(R.color.black54);
        mTertiaryTextColor = getResources().getColor(R.color.black12);
        mColorRed = getResources().getColor(R.color.red800);
        mColorOrange = getResources().getColor(R.color.orange500);
        mColorGreen = getResources().getColor(R.color.green500);
        mPrimaryColor = getResources().getColor(R.color.primary);
        mPrimaryBackgroundColor = getResources().getColor(R.color.grey50);
        mSecondaryBackgroundColor = getResources().getColor(R.color.grey200);
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        this.mUsingEnterKey = usingEnterKey();
        mUseSubject = getPreferences().getBoolean("use_subject", true);
        final ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    protected boolean isOptimizingBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return !pm.isIgnoringBatteryOptimizations(getPackageName());
        } else {
            return false;
        }
    }

    protected boolean usingEnterKey() {
        return getPreferences().getBoolean("display_enter_key", false);
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
    }

    public boolean useSubjectToIdentifyConference() {
        return mUseSubject;
    }

    public void switchToConversation(Conversation conversation) {
        switchToConversation(conversation, null, false);
    }

    public void switchToConversation(Conversation conversation, String text,
                                     boolean newTask) {
        switchToConversation(conversation, text, null, false, newTask);
    }

    public void highlightInMuc(Conversation conversation, String nick) {
        switchToConversation(conversation, null, nick, false, false);
    }

    public void privateMsgInMuc(Conversation conversation, String nick) {
        switchToConversation(conversation, null, nick, true, false);
    }

    private void switchToConversation(Conversation conversation, String text, String nick, boolean pm, boolean newTask) {
        Intent viewConversationIntent = new Intent(this,
                ConversationActivity.class);
        viewConversationIntent.setAction(Intent.ACTION_VIEW);
        viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
                conversation.getUuid());
        if (text != null) {
            viewConversationIntent.putExtra(ConversationActivity.TEXT, text);
        }
        if (nick != null) {
            viewConversationIntent.putExtra(ConversationActivity.NICK, nick);
            viewConversationIntent.putExtra(ConversationActivity.PRIVATE_MESSAGE, pm);
        }
        viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
        if (newTask) {
            viewConversationIntent.setFlags(viewConversationIntent.getFlags()
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            viewConversationIntent.setFlags(viewConversationIntent.getFlags()
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        startActivity(viewConversationIntent);
        finish();
    }

    public void switchToContactDetails(Contact contact) {
        switchToContactDetails(contact, null);
    }

    public void switchToContactDetails(Contact contact, String messageFingerprint) {
        Intent intent = new Intent(this, ContactDetailsActivity.class);
        intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
        intent.putExtra(EXTRA_ACCOUNT, contact.getAccount().getJid().toBareJid().toString());
        intent.putExtra("contact", contact.getJid().toString());
        intent.putExtra("fingerprint", messageFingerprint);
        startActivity(intent);
    }

    public void switchToAccount(Account account) {
        switchToAccount(account, false);
    }

    public void switchToAccount(Account account, boolean init) {
        Intent intent = new Intent(this, EditAccountActivity.class);
        intent.putExtra("jid", account.getJid().toBareJid().toString());
        intent.putExtra("init", init);
        startActivity(intent);
    }

    protected void inviteToConversation(Conversation conversation) {
        Intent intent = new Intent(getApplicationContext(),
                ChooseContactActivity.class);
        List<String> contacts = new ArrayList<>();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            for (MucOptions.User user : conversation.getMucOptions().getUsers(false)) {
                Jid jid = user.getRealJid();
                if (jid != null) {
                    contacts.add(jid.toBareJid().toString());
                }
            }
        } else {
            contacts.add(conversation.getJid().toBareJid().toString());
        }
        intent.putExtra("filter_contacts", contacts.toArray(new String[contacts.size()]));
        intent.putExtra("conversation", conversation.getUuid());
        intent.putExtra("multiple", true);
        intent.putExtra("show_enter_jid", false);
        intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().toBareJid().toString());
        startActivityForResult(intent, REQUEST_INVITE_TO_CONVERSATION);
    }

    protected void announcePgp(Account account, final Conversation conversation, final Runnable onSuccess) {
        if (account.getPgpId() == 0) {
            choosePgpSignId(account);
        } else {
            String status = null;
            if (manuallyChangePresence()) {
                status = account.getPresenceStatusMessage();
            }
            if (status == null) {
                status = "";
            }
            xmppConnectionService.getPgpEngine().generateSignature(account, status, new UiCallback<Account>() {

                @Override
                public void userInputRequried(PendingIntent pi,
                                              Account account) {
                    try {
                        startIntentSenderForResult(pi.getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                    } catch (final SendIntentException ignored) {
                    }
                }

                @Override
                public void success(Account account) {
                    xmppConnectionService.databaseBackend.updateAccount(account);
                    xmppConnectionService.sendPresence(account);
                    if (conversation != null) {
                        conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                        xmppConnectionService.databaseBackend.updateConversation(conversation);
                        refreshUi();
                    }
                    if (onSuccess != null) {
                        runOnUiThread(onSuccess);
                    }
                }

                @Override
                public void error(int error, Account account) {
                    displayErrorDialog(error);
                }
            });
        }
    }

    protected boolean noAccountUsesPgp() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getPgpId() != 0) {
                return false;
            }
        }
        return true;
    }


    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void setListItemBackgroundOnView(View view) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
        } else {
            view.setBackground(getResources().getDrawable(R.drawable.greybackground));
        }
    }

    protected void choosePgpSignId(Account account) {
        xmppConnectionService.getPgpEngine().chooseKey(account, new UiCallback<Account>() {
            @Override
            public void success(Account account1) {
            }

            @Override
            public void error(int errorCode, Account object) {

            }

            @Override
            public void userInputRequried(PendingIntent pi, Account object) {
                try {
                    startIntentSenderForResult(pi.getIntentSender(),
                            REQUEST_CHOOSE_PGP_ID, null, 0, 0, 0);
                } catch (final SendIntentException ignored) {
                }
            }
        });
    }

    protected void displayErrorDialog(final int errorCode) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        XmppActivity.this);
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setTitle(getString(R.string.error));
                builder.setMessage(errorCode);
                builder.setNeutralButton(R.string.accept, null);
                builder.create().show();
            }
        });

    }

    protected void showAddToRosterDialog(final Conversation conversation) {
        showAddToRosterDialog(conversation.getContact());
    }

    protected void showAddToRosterDialog(final Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(contact.getJid().getLocalpart().toString());
        builder.setMessage(getString(R.string.not_in_roster));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add_contact),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Jid jid = contact.getJid();
                        Account account = contact.getAccount();
                        Contact contact = account.getRoster().getContact(jid);
                        xmppConnectionService.createContact(contact);
                    }
                });
        builder.create().show();
    }

    private void showAskForPresenceDialog(final Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(contact.getJid().toString());
        builder.setMessage(R.string.request_presence_updates);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.request_now,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (xmppConnectionServiceBound) {
                            xmppConnectionService.sendPresencePacket(contact
                                    .getAccount(), xmppConnectionService
                                    .getPresenceGenerator()
                                    .requestPresenceUpdatesFrom(contact));
                        }
                    }
                });
        builder.create().show();
    }

    private void warnMutalPresenceSubscription(final Conversation conversation,
                                               final OnPresenceSelected listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(conversation.getContact().getJid().toString());
        builder.setMessage(R.string.without_mutual_presence_updates);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ignore, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                conversation.setNextCounterpart(null);
                if (listener != null) {
                    listener.onPresenceSelected();
                }
            }
        });
        builder.create().show();
    }

    protected void quickEdit(String previousValue, int hint, OnValueEdited callback) {
        quickEdit(previousValue, callback, hint, false);
    }

    protected void quickPasswordEdit(String previousValue, OnValueEdited callback) {
        quickEdit(previousValue, callback, R.string.password, true);
    }

    @SuppressLint("InflateParams")
    private void quickEdit(final String previousValue,
                           final OnValueEdited callback,
                           final int hint,
                           boolean password) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.quickedit, null);
        final EditText editor = (EditText) view.findViewById(R.id.editor);
        OnClickListener mClickListener = new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = editor.getText().toString();
                if (!value.equals(previousValue) && value.trim().length() > 0) {
                    callback.onValueEdited(value);
                }
            }
        };
        if (password) {
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setPositiveButton(R.string.accept, mClickListener);
        } else {
            builder.setPositiveButton(R.string.edit, mClickListener);
        }
        if (hint != 0) {
            editor.setHint(hint);
        }
        editor.requestFocus();
        editor.setText("");
        if (previousValue != null) {
            editor.getText().append(previousValue);
        }
        builder.setView(view);
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    protected boolean addFingerprintRow(LinearLayout keys, final Account account, final String fingerprint, boolean highlight, View.OnClickListener onKeyClickedListener) {
        final XmppAxolotlSession.Trust trust = account.getAxolotlService()
                .getFingerprintTrust(fingerprint);
        if (trust == null) {
            return false;
        }
        return addFingerprintRowWithListeners(keys, account, fingerprint, highlight, trust, true,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        account.getAxolotlService().setFingerprintTrust(fingerprint,
                                (isChecked) ? XmppAxolotlSession.Trust.TRUSTED :
                                        XmppAxolotlSession.Trust.UNTRUSTED);
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        account.getAxolotlService().setFingerprintTrust(fingerprint,
                                XmppAxolotlSession.Trust.UNTRUSTED);
                        v.setEnabled(true);
                    }
                },
                onKeyClickedListener

        );
    }

    protected boolean addFingerprintRowWithListeners(LinearLayout keys, final Account account,
                                                     final String fingerprint,
                                                     boolean highlight,
                                                     XmppAxolotlSession.Trust trust,
                                                     boolean showTag,
                                                     CompoundButton.OnCheckedChangeListener
                                                             onCheckedChangeListener,
                                                     View.OnClickListener onClickListener,
                                                     View.OnClickListener onKeyClickedListener) {
        if (trust == XmppAxolotlSession.Trust.COMPROMISED) {
            return false;
        }
        View view = getLayoutInflater().inflate(R.layout.contact_key, keys, false);
        TextView key = (TextView) view.findViewById(R.id.key);
        key.setOnClickListener(onKeyClickedListener);
        TextView keyType = (TextView) view.findViewById(R.id.key_type);
        keyType.setOnClickListener(onKeyClickedListener);
        Switch trustToggle = (Switch) view.findViewById(R.id.tgl_trust);
        trustToggle.setVisibility(View.VISIBLE);
        trustToggle.setOnCheckedChangeListener(onCheckedChangeListener);
        trustToggle.setOnClickListener(onClickListener);
        final View.OnLongClickListener purge = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showPurgeKeyDialog(account, fingerprint);
                return true;
            }
        };
        view.setOnLongClickListener(purge);
        key.setOnLongClickListener(purge);
        keyType.setOnLongClickListener(purge);
        boolean x509 = Config.X509_VERIFICATION
                && (trust == XmppAxolotlSession.Trust.TRUSTED_X509 || trust == XmppAxolotlSession.Trust.INACTIVE_TRUSTED_X509);
        switch (trust) {
            case UNTRUSTED:
            case TRUSTED:
            case TRUSTED_X509:
                trustToggle.setChecked(trust.trusted(), false);
                trustToggle.setEnabled(!Config.X509_VERIFICATION || trust != XmppAxolotlSession.Trust.TRUSTED_X509);
                if (Config.X509_VERIFICATION && trust == XmppAxolotlSession.Trust.TRUSTED_X509) {
                    trustToggle.setOnClickListener(null);
                }
                key.setTextColor(getPrimaryTextColor());
                keyType.setTextColor(getSecondaryTextColor());
                break;
            case UNDECIDED:
                trustToggle.setChecked(false, false);
                trustToggle.setEnabled(false);
                key.setTextColor(getPrimaryTextColor());
                keyType.setTextColor(getSecondaryTextColor());
                break;
            case INACTIVE_UNTRUSTED:
            case INACTIVE_UNDECIDED:
                trustToggle.setOnClickListener(null);
                trustToggle.setChecked(false, false);
                trustToggle.setEnabled(false);
                key.setTextColor(getTertiaryTextColor());
                keyType.setTextColor(getTertiaryTextColor());
                break;
            case INACTIVE_TRUSTED:
            case INACTIVE_TRUSTED_X509:
                trustToggle.setOnClickListener(null);
                trustToggle.setChecked(true, false);
                trustToggle.setEnabled(false);
                key.setTextColor(getTertiaryTextColor());
                keyType.setTextColor(getTertiaryTextColor());
                break;
        }

        if (showTag) {
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
        } else {
            keyType.setVisibility(View.GONE);
        }
        if (highlight) {
            keyType.setTextColor(getResources().getColor(R.color.accent));
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509_selected_message : R.string.omemo_fingerprint_selected_message));
        } else {
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
        }

        key.setText(CryptoHelper.prettifyFingerprint(fingerprint.substring(2)));
        keys.addView(view);
        return true;
    }

    public void showPurgeKeyDialog(final Account account, final String fingerprint) {
        Builder builder = new Builder(this);
        builder.setTitle(getString(R.string.purge_key));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.purge_key_desc_part1)
                + "\n\n" + CryptoHelper.prettifyFingerprint(fingerprint.substring(2))
                + "\n\n" + getString(R.string.purge_key_desc_part2));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.purge_key),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        account.getAxolotlService().purgeKey(fingerprint);
                        refreshUi();
                    }
                });
        builder.create().show();
    }

    public boolean hasStoragePermission(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    public void selectPresence(final Conversation conversation,
                               final OnPresenceSelected listener) {
        final Contact contact = conversation.getContact();
        if (conversation.hasValidOtrSession()) {
            SessionID id = conversation.getOtrSession().getSessionID();
            Jid jid;
            try {
                jid = Jid.fromString(id.getAccountID() + "/" + id.getUserID());
            } catch (InvalidJidException e) {
                jid = null;
            }
            conversation.setNextCounterpart(jid);
            listener.onPresenceSelected();
        } else if (!contact.showInRoster()) {
            showAddToRosterDialog(conversation);
        } else {
            Presences presences = contact.getPresences();
            if (presences.size() == 0) {
                if (!contact.getOption(Contact.Options.TO)
                        && !contact.getOption(Contact.Options.ASKING)
                        && contact.getAccount().getStatus() == Account.State.ONLINE) {
                    showAskForPresenceDialog(contact);
                } else if (!contact.getOption(Contact.Options.TO)
                        || !contact.getOption(Contact.Options.FROM)) {
                    warnMutalPresenceSubscription(conversation, listener);
                } else {
                    conversation.setNextCounterpart(null);
                    listener.onPresenceSelected();
                }
            } else if (presences.size() == 1) {
                String presence = presences.asStringArray()[0];
                try {
                    conversation.setNextCounterpart(Jid.fromParts(contact.getJid().getLocalpart(), contact.getJid().getDomainpart(), presence));
                } catch (InvalidJidException e) {
                    conversation.setNextCounterpart(null);
                }
                listener.onPresenceSelected();
            } else {
                final StringBuilder presence = new StringBuilder();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.choose_presence));
                final String[] presencesArray = presences.asStringArray();
                int preselectedPresence = 0;
                for (int i = 0; i < presencesArray.length; ++i) {
                    if (presencesArray[i].equals(contact.getLastPresence())) {
                        preselectedPresence = i;
                        break;
                    }
                }
                presence.append(presencesArray[preselectedPresence]);
                builder.setSingleChoiceItems(presencesArray,
                        preselectedPresence,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                presence.delete(0, presence.length());
                                presence.append(presencesArray[which]);
                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            conversation.setNextCounterpart(Jid.fromParts(contact.getJid().getLocalpart(), contact.getJid().getDomainpart(), presence.toString()));
                        } catch (InvalidJidException e) {
                            conversation.setNextCounterpart(null);
                        }
                        listener.onPresenceSelected();
                    }
                });
                builder.create().show();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            mPendingConferenceInvite = ConferenceInvite.parse(data);
            if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
                mPendingConferenceInvite.execute(this);
                mToast = Toast.makeText(this, R.string.creating_conference,Toast.LENGTH_LONG);
                mToast.show();
                mPendingConferenceInvite = null;
            }
        }
    }

    public int getTertiaryTextColor() {
        return this.mTertiaryTextColor;
    }

    public int getSecondaryTextColor() {
        return this.mSecondaryTextColor;
    }

    public int getPrimaryTextColor() {
        return this.mPrimaryTextColor;
    }

    public int getWarningTextColor() {
        return this.mColorRed;
    }

    public int getOnlineColor() {
        return this.mColorGreen;
    }

    public int getPrimaryBackgroundColor() {
        return this.mPrimaryBackgroundColor;
    }

    public int getSecondaryBackgroundColor() {
        return this.mSecondaryBackgroundColor;
    }

    public int getPixel(int dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return ((int) (dp * metrics.density));
    }

    public boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);
        if (mClipBoardManager != null) {
            ClipData mClipData = ClipData.newPlainText(label, text);
            mClipBoardManager.setPrimaryClip(mClipData);
            return true;
        }
        return false;
    }

    protected void registerNdefPushMessageCallback() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
                @Override
                public NdefMessage createNdefMessage(NfcEvent nfcEvent) {
                    return new NdefMessage(new NdefRecord[]{
                            NdefRecord.createUri(getShareableUri()),
                            NdefRecord.createApplicationRecord("net.atomarea.flowx")
                    });
                }
            }, this);
        }
    }

    protected boolean neverCompressPictures() {
        return getPreferences().getString("picture_compression", "auto").equals("never");
    }

    protected boolean manuallyChangePresence() {
        return getPreferences().getBoolean("manually_change_presence", true);
    }

    protected void unregisterNdefPushMessageCallback() {

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.setNdefPushMessageCallback(null, this);
        }
    }

    protected String getShareableUri() {
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.getShareableUri() != null) {
            this.registerNdefPushMessageCallback();
        }
    }

    protected int findTheme() {
        if (getPreferences().getBoolean("use_larger_font", true)) {
            return R.style.ConversationsTheme_LargerText;
        } else {
            return R.style.ConversationsTheme_LargerText;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        this.unregisterNdefPushMessageCallback();
    }

    protected void showQrCode() {
        String uri = getShareableUri();
        if (uri != null) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            final int width = (size.x < size.y ? size.x : size.y);
            Bitmap bitmap = createQrCodeBitmap(uri, width);
            ImageView view = new ImageView(this);
            view.setImageBitmap(bitmap);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(view);
            builder.create().show();
        }
    }

    public BitmapDrawable getQrCode() {
        String uri = getShareableUri();
        if (uri != null) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            final int width = (size.x < size.y ? size.x : size.y);
            Bitmap bitmap = createQrCodeBitmap(uri, width);
            return new BitmapDrawable(getResources(), bitmap);
        }
        return null;
    }

    protected Bitmap createQrCodeBitmap(String input, int size) {
        Log.d(Config.LOGTAG, "qr code requested size: " + size);
        try {
            final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            final BitMatrix result = QR_CODE_WRITER.encode(input, BarcodeFormat.QR_CODE, size, size, hints);
            final int width = result.getWidth();
            final int height = result.getHeight();
            final int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
                }
            }
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Log.d(Config.LOGTAG, "output size: " + width + "x" + height);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (final WriterException e) {
            return null;
        }
    }

    protected Account extractAccount(Intent intent) {
        String jid = intent != null ? intent.getStringExtra(EXTRA_ACCOUNT) : null;
        try {
            return jid != null ? xmppConnectionService.findAccountByJid(Jid.fromString(jid)) : null;
        } catch (InvalidJidException e) {
            return null;
        }
    }

    public AvatarService avatarService() {
        return xmppConnectionService.getAvatarService();
    }

    public void loadBitmap(Message message, ImageView imageView) {
        File bm;
        bm = xmppConnectionService.getFileBackend().getFile(message, true);
        Glide.with(this)
                .load(bm)
                .override(600, 600)
                .fitCenter()
                //.centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .into(imageView);
        //Log.d(Config.LOGTAG,"Load image with glide");
    }

    public void loadVideoPreview(Message message, ImageView imageView) {
        File vp = xmppConnectionService.getFileBackend().getFile(message, true);
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            //use one of overloaded setDataSource() functions to set your data source
            retriever.setDataSource(this, Uri.fromFile(vp));
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long microSecond = Long.parseLong(time);
            int duration = (int) Math.ceil(microSecond / 2); //preview at half of video
            BitmapPool bitmapPool = Glide.get(getApplicationContext()).getBitmapPool();
            VideoBitmapDecoder videoBitmapDecoder = new VideoBitmapDecoder(duration);
            FileDescriptorBitmapDecoder fileDescriptorBitmapDecoder = new FileDescriptorBitmapDecoder(videoBitmapDecoder, bitmapPool, DecodeFormat.PREFER_ARGB_8888);
            Glide.with(getApplicationContext())
                    .load(vp)
                    .asBitmap()
                    .override(600, 600)
                    .fitCenter()
                    //.centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.RESULT)
                    .videoDecoder(fileDescriptorBitmapDecoder)
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected interface OnValueEdited {
        public void onValueEdited(String value);
    }

    public interface OnPresenceSelected {
        public void onPresenceSelected();
    }

    public static class ConferenceInvite {
        private String uuid;
        private List<Jid> jids = new ArrayList<>();

        public static ConferenceInvite parse(Intent data) {
            ConferenceInvite invite = new ConferenceInvite();
            invite.uuid = data.getStringExtra("conversation");
            if (invite.uuid == null) {
                return null;
            }
            try {
                if (data.getBooleanExtra("multiple", false)) {
                    String[] toAdd = data.getStringArrayExtra("contacts");
                    for (String item : toAdd) {
                        invite.jids.add(Jid.fromString(item));
                    }
                } else {
                    invite.jids.add(Jid.fromString(data.getStringExtra("contact")));
                }
            } catch (final InvalidJidException ignored) {
                return null;
            }
            return invite;
        }

        public void execute(XmppActivity activity) {
            XmppConnectionService service = activity.xmppConnectionService;
            Conversation conversation = service.findConversationByUuid(this.uuid);
            if (conversation == null) {
                return;
            }
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                for (Jid jid : jids) {
                    service.invite(conversation, jid);
                }
            } else {
                jids.add(conversation.getJid().toBareJid());
                service.createAdhocConference(conversation.getAccount(), null, jids, activity.adhocCallback);
            }
        }
    }
}
