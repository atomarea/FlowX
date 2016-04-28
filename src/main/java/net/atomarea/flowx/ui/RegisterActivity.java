package net.atomarea.flowx.ui;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.beardedhen.androidbootstrap.BootstrapEditText;

import java.util.Set;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.services.XmppConnectionService.OnCaptchaRequested;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.services.XmppConnectionService.OnAccountUpdate;
import net.atomarea.flowx.ui.adapter.KnownHostsAdapter;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.OnKeyStatusUpdated;
import net.atomarea.flowx.xmpp.XmppConnection;
import net.atomarea.flowx.xmpp.XmppConnection.Features;
import net.atomarea.flowx.xmpp.forms.Data;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.pep.Avatar;

public class RegisterActivity extends XmppActivity implements OnAccountUpdate,
        OnKeyStatusUpdated, OnCaptchaRequested, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast {

    private BootstrapEditText mAccountJid;
    private BootstrapEditText mPassword;
    private BootstrapEditText mPasswordConfirm;
    private CheckBox mRegisterNew;
    private BootstrapButton mSaveButton;

    private TextView mAccountJidLabel;
    private LinearLayout mNamePort;
    private EditText mHostname;
    private EditText mPort;
    private AlertDialog mCaptchaDialog = null;

    private Jid jidToEdit;
    private boolean mInitMode = false;
    private boolean mShowOptions = false;
    private Account mAccount;
    private String messageFingerprint;

    private boolean mFetchingAvatar = false;

    private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            final String password = mPassword.getText().toString();
            final String passwordConfirm = mPasswordConfirm.getText().toString();

            if (!mInitMode && passwordChangedInMagicCreateMode()) {
                gotoChangePassword(password);
                return;
            }
            if (mInitMode && mAccount != null) {
                mAccount.setOption(Account.OPTION_DISABLED, false);
            }
            if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !accountInfoEdited()) {
                mAccount.setOption(Account.OPTION_DISABLED, false);
                xmppConnectionService.updateAccount(mAccount);
                return;
            }
            final boolean registerNewAccount = mRegisterNew.isChecked() && !Config.DISALLOW_REGISTRATION_IN_UI;
            if (Config.DOMAIN_LOCK != null && mAccountJid.getText().toString().contains("@")) {
                mAccountJid.setError(getString(R.string.invalid_username));
                mAccountJid.requestFocus();
                return;
            }
            final Jid jid;
            try {
                if (Config.DOMAIN_LOCK != null) {
                    jid = Jid.fromParts(mAccountJid.getText().toString(), Config.DOMAIN_LOCK, null);
                } else {
                    jid = Jid.fromString(mAccountJid.getText().toString());
                }
            } catch (final InvalidJidException e) {
                if (Config.DOMAIN_LOCK != null) {
                    mAccountJid.setError(getString(R.string.invalid_username));
                } else {
                    mAccountJid.setError(getString(R.string.invalid_jid));
                }
                mAccountJid.requestFocus();
                return;
            }
            String hostname = null;
            int numericPort = 5222;
            if (mShowOptions) {
                hostname = mHostname.getText().toString();
                final String port = mPort.getText().toString();
                if (hostname.contains(" ")) {
                    mHostname.setError(getString(R.string.not_valid_hostname));
                    mHostname.requestFocus();
                    return;
                }
                try {
                    numericPort = Integer.parseInt(port);
                    if (numericPort < 0 || numericPort > 65535) {
                        mPort.setError(getString(R.string.not_a_valid_port));
                        mPort.requestFocus();
                        return;
                    }

                } catch (NumberFormatException e) {
                    mPort.setError(getString(R.string.not_a_valid_port));
                    mPort.requestFocus();
                    return;
                }
            }

            if (jid.isDomainJid()) {
                if (Config.DOMAIN_LOCK != null) {
                    mAccountJid.setError(getString(R.string.invalid_username));
                } else {
                    mAccountJid.setError(getString(R.string.invalid_jid));
                }
                mAccountJid.requestFocus();
                return;
            }
            if (registerNewAccount) {
                if (!password.equals(passwordConfirm)) {
                    mPasswordConfirm.setError(getString(R.string.passwords_do_not_match));
                    mPasswordConfirm.requestFocus();
                    return;
                }
            }
            if (mAccount != null) {
                if (mInitMode && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                    mAccount.setOption(Account.OPTION_MAGIC_CREATE, mAccount.getPassword().contains(password));
                }
                mAccount.setJid(jid);
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                mAccountJid.setError(null);
                mPasswordConfirm.setError(null);
                mAccount.setPassword(password);
                mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
                xmppConnectionService.updateAccount(mAccount);
            } else {
                if (xmppConnectionService.findAccountByJid(jid) != null) {
                    mAccountJid.setError(getString(R.string.account_already_exists));
                    mAccountJid.requestFocus();
                    return;
                }
                mAccount = new Account(jid.toBareJid(), password);
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                mAccount.setOption(Account.OPTION_USETLS, true);
                mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
                mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
                xmppConnectionService.createAccount(mAccount);
            }
            mHostname.setError(null);
            mPort.setError(null);
            if (!mAccount.isOptionSet(Account.OPTION_DISABLED)
                    && !registerNewAccount
                    && !mInitMode) {
                finish();
            } else {
                updateSaveButton();
                updateAccountInformation(true);
            }

        }
    };

    public void refreshUiReal() {
        invalidateOptionsMenu();
        if (mAccount != null
                && mAccount.getStatus() != Account.State.ONLINE
                && mFetchingAvatar) {
            startActivity(new Intent(getApplicationContext(),
                    ManageAccountActivity.class));
            finish();
        } else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
            if (!mFetchingAvatar) {
                mFetchingAvatar = true;
                xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
            }
        } else {
            updateSaveButton();
        }
        if (mAccount != null) {
            updateAccountInformation(false);
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    private final UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {

        @Override
        public void userInputRequried(final PendingIntent pi, final Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void success(final Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void error(final int errorCode, final Avatar avatar) {
            finishInitialSetup(avatar);
        }
    };
    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            updateSaveButton();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {

        }
    };

    private final OnClickListener mAvatarClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            if (mAccount != null) {
                final Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
                startActivity(intent);
            }
        }
    };

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                final Intent intent;
                final XmppConnection connection = mAccount.getXmppConnection();
                final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
                if (avatar != null || (connection != null && !connection.getFeatures().pep())) {
                    intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                    if (wasFirstAccount) {
                        intent.putExtra("init", true);
                    }
                } else {
                    intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                    intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
                    intent.putExtra("setup", true);
                }
                if (wasFirstAccount) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                }
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BATTERY_OP) {
            updateAccountInformation(mAccount == null);
        }
    }

    protected void updateSaveButton() {
        boolean accountInfoEdited = accountInfoEdited();

        if (!mInitMode && passwordChangedInMagicCreateMode()) {
            this.mSaveButton.setText(R.string.change_password);
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
        } else if (accountInfoEdited && !mInitMode) {
            this.mSaveButton.setText(R.string.save);
            this.mSaveButton.setEnabled(true);
        } else if (mAccount != null && (mAccount.getStatus() == Account.State.CONNECTING || mFetchingAvatar)) {
            this.mSaveButton.setEnabled(false);
            this.mSaveButton.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !mInitMode) {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setText(R.string.enable);
        } else {
            this.mSaveButton.setEnabled(true);
            if (!mInitMode) {
                if (mAccount != null && mAccount.isOnlineAndConnected()) {
                    this.mSaveButton.setText(R.string.save);
                    if (!accountInfoEdited()) {
                        this.mSaveButton.setEnabled(false);
                    }
                } else {
                    this.mSaveButton.setText(R.string.connect);
                }
            } else {
                this.mSaveButton.setText(R.string.next);
            }
        }
    }


    protected boolean accountInfoEdited() {
        if (this.mAccount == null) {
            return false;
        }
        return jidEdited() ||
                !this.mAccount.getPassword().equals(this.mPassword.getText().toString()) ||
                !this.mAccount.getHostname().equals(this.mHostname.getText().toString()) ||
                !String.valueOf(this.mAccount.getPort()).equals(this.mPort.getText().toString());
    }

    protected boolean jidEdited() {
        final String unmodified;
        if (Config.DOMAIN_LOCK != null) {
            unmodified = this.mAccount.getJid().getLocalpart();
        } else {
            unmodified = this.mAccount.getJid().toBareJid().toString();
        }
        return !unmodified.equals(this.mAccountJid.getText().toString());
    }

    protected boolean passwordChangedInMagicCreateMode() {
        return mAccount != null
                && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
                && !this.mAccount.getPassword().equals(this.mPassword.getText().toString())
                && !this.jidEdited()
                && mAccount.isOnlineAndConnected();
    }

    @Override
    protected String getShareableUri() {
        if (mAccount != null) {
            return mAccount.getShareableUri();
        } else {
            return "";
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        this.mAccountJid = (BootstrapEditText) findViewById(R.id.account_jid);
        this.mAccountJid.addTextChangedListener(this.mTextWatcher);
        this.mAccountJidLabel = (TextView) findViewById(R.id.account_jid_label);
        if (Config.DOMAIN_LOCK != null) {
            this.mAccountJidLabel.setText(R.string.username);
            this.mAccountJid.setHint(R.string.username_hint);
        }
        this.mPassword = (BootstrapEditText) findViewById(R.id.account_password);
        this.mPassword.addTextChangedListener(this.mTextWatcher);
        this.mPasswordConfirm = (BootstrapEditText) findViewById(R.id.account_password_confirm);
        this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
        this.mNamePort = (LinearLayout) findViewById(R.id.name_port);
        this.mHostname = (EditText) findViewById(R.id.hostname);
        this.mHostname.addTextChangedListener(mTextWatcher);
        this.mPort = (EditText) findViewById(R.id.port);
        this.mPort.setText("5222");
        this.mPort.addTextChangedListener(mTextWatcher);
        this.mSaveButton = (BootstrapButton) findViewById(R.id.save_button);
        this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
        final OnCheckedChangeListener OnCheckedShowConfirmPassword = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView,
                                         final boolean isChecked) {
                if (isChecked) {
                    mPasswordConfirm.setVisibility(View.VISIBLE);
                } else {
                    mPasswordConfirm.setVisibility(View.GONE);
                }
                updateSaveButton();
            }
        };
        this.mRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);
        if (Config.DISALLOW_REGISTRATION_IN_UI) {
            this.mRegisterNew.setVisibility(View.GONE);
        }
    }

    private void gotoChangePassword(String newPassword) {
        final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
        changePasswordIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
        if (newPassword != null) {
            changePasswordIntent.putExtra("password", newPassword);
        }
        startActivity(changePasswordIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent() != null) {
            try {
                this.jidToEdit = Jid.fromString(getIntent().getStringExtra("jid"));
            } catch (final InvalidJidException | NullPointerException ignored) {
                this.jidToEdit = null;
            }
            this.mInitMode = getIntent().getBooleanExtra("init", false) || this.jidToEdit == null;
            this.messageFingerprint = getIntent().getStringExtra("fingerprint");
            if (!mInitMode) {
                this.mRegisterNew.setVisibility(View.GONE);
                if (getActionBar() != null) {
                    getActionBar().setTitle(getString(R.string.account_details));
                }
            } else {
                if (getActionBar() != null) {
                    getActionBar().setTitle(R.string.action_add_account);
                }
            }
        }
        SharedPreferences preferences = getPreferences();
        boolean useTor = Config.FORCE_ORBOT || preferences.getBoolean("use_tor", false);
        this.mShowOptions = useTor || preferences.getBoolean("show_connection_options", false);
        mHostname.setHint(useTor ? R.string.hostname_or_onion : R.string.hostname_example);
        this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onBackendConnected() {
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            if (this.mAccount != null) {
                this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
                if (this.mAccount.getPrivateKeyAlias() != null) {
                    this.mPassword.setHint(R.string.authenticate_with_certificate);
                    if (this.mInitMode) {
                        this.mPassword.requestFocus();
                    }
                }
                updateAccountInformation(true);
            }
        } else if ((Config.MAGIC_CREATE_DOMAIN == null && this.xmppConnectionService.getAccounts().size() == 0)
                || (this.mAccount != null && this.mAccount == xmppConnectionService.getPendingAccount())) {
            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(false);
                getActionBar().setDisplayShowHomeEnabled(false);
                getActionBar().setHomeButtonEnabled(false);
            }
        }
        if (Config.DOMAIN_LOCK == null) {
            final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                    android.R.layout.simple_list_item_1,
                    xmppConnectionService.getKnownHosts());
        }
        updateSaveButton();
        invalidateOptionsMenu();
    }

    private void changePresence() {
        Intent intent = new Intent(this, SetPresenceActivity.class);
        intent.putExtra(SetPresenceActivity.EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
        startActivity(intent);
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.updateKeyInAccount(mAccount, alias);
        }
    }

    private void updateAccountInformation(boolean init) {
        if (init) {
            this.mAccountJid.getEditableText().clear();
            if (Config.DOMAIN_LOCK != null) {
                this.mAccountJid.getEditableText().append(this.mAccount.getJid().getLocalpart());
            } else {
                this.mAccountJid.getEditableText().append(this.mAccount.getJid().toBareJid().toString());
            }
            this.mPassword.setText(this.mAccount.getPassword());
            this.mHostname.setText("");
            this.mHostname.getEditableText().append(this.mAccount.getHostname());
            this.mPort.setText("");
            this.mPort.getEditableText().append(String.valueOf(this.mAccount.getPort()));
            this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

        }
        if (!mInitMode) {
        }
        if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(this.mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }
        if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data,
                                   final Bitmap captcha) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final ImageView view = new ImageView(this);
        final LinearLayout layout = new LinearLayout(this);
        final EditText input = new EditText(this);

        view.setImageBitmap(captcha);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);

        input.setHint(getString(R.string.captcha_hint));

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(view);
        layout.addView(input);

        builder.setTitle(getString(R.string.captcha_required));
        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String rc = input.getText().toString();
                        data.put("username", account.getUsername());
                        data.put("password", account.getPassword());
                        data.put("ocr", rc);
                        data.submit();

                        if (xmppConnectionServiceBound) {
                            xmppConnectionService.sendCreateAccountWithCaptchaPacket(
                                    account, id, data);
                        }
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            }
        });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
                    mCaptchaDialog.dismiss();
                }
                mCaptchaDialog = builder.create();
                mCaptchaDialog.show();
            }
        });
    }

    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RegisterActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
