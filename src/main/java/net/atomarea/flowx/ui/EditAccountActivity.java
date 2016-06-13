package net.atomarea.flowx.ui;

import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.services.XmppConnectionService.OnAccountUpdate;
import net.atomarea.flowx.xmpp.OnKeyStatusUpdated;
import net.atomarea.flowx.xmpp.XmppConnection;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.pep.Avatar;

import java.util.Set;

public class EditAccountActivity extends XmppActivity implements OnAccountUpdate,
        OnKeyStatusUpdated, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast {

    private EditText mAccountJid;
    private TextView mAccountJidLabel;
    private TextView mStatusMessage;
    private TextView mStatus;
    private RelativeLayout mStatusView;
    private ImageView mAvatar;
    private LinearLayout mNamePort;
    private EditText mHostname;
    private EditText mPort;

    private Jid jidToEdit;
    private boolean mInitMode = false;
    private boolean mShowOptions = false;
    private Account mAccount;
    private RelativeLayout mQR_View;
    private TextView mQR_Text;
    private TextView mAccount_info;


    private boolean mFetchingAvatar = false;

    private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {

            if (!mInitMode && passwordChangedInMagicCreateMode()) {
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
            if (mAccount != null) {
                if (mInitMode && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                }
                mAccount.setJid(jid);
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                mAccountJid.setError(null);
                xmppConnectionService.updateAccount(mAccount);
            } else {
                if (xmppConnectionService.findAccountByJid(jid) != null) {
                    mAccountJid.setError(getString(R.string.account_already_exists));
                    mAccountJid.requestFocus();
                    return;
                }
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                mAccount.setOption(Account.OPTION_USETLS, true);
                mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
                xmppConnectionService.createAccount(mAccount);
            }
            mHostname.setError(null);
            mPort.setError(null);
            if (!mAccount.isOptionSet(Account.OPTION_DISABLED)
                    && !mInitMode) {
                finish();
            } else {
                updateAccountInformation(true);
            }

        }
    };

    public void refreshUiReal() {
        invalidateOptionsMenu();
        if (mAccount != null
                && mAccount.getStatus() != Account.State.ONLINE
                && mFetchingAvatar) {
            finish();
        } else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
            if (!mFetchingAvatar) {
                mFetchingAvatar = true;
                xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
            }
        } else {
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



    protected boolean accountInfoEdited() {
        if (this.mAccount == null) {
            return false;
        }
        return jidEdited() ||
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
        setContentView(R.layout.activity_edit_account);
        this.mAccountJid = (EditText) findViewById(R.id.account_jid);
        this.mAccountJid.addTextChangedListener(this.mTextWatcher);
        this.mAccountJidLabel = (TextView) findViewById(R.id.account_jid_label);
        this.mStatusMessage = (TextView) findViewById(R.id.status_message);
        this.mStatus = (TextView) findViewById(R.id.status);
        this.mStatusView = (RelativeLayout) findViewById(R.id.statusView);
        if (Config.DOMAIN_LOCK != null) {
            this.mAccountJidLabel.setText(R.string.username);
            this.mAccountJid.setHint(R.string.username_hint);
        }
        this.mAvatar = (ImageView) findViewById(R.id.avater);
        this.mAvatar.setOnClickListener(this.mAvatarClickListener);
        this.mAccount_info = (TextView) findViewById(R.id.account_info);
        this.mQR_View = (RelativeLayout) findViewById(R.id.qrview);
        this.mQR_Text = (TextView) findViewById(R.id.qrcode);

        this.mNamePort = (LinearLayout) findViewById(R.id.name_port);
        this.mHostname = (EditText) findViewById(R.id.hostname);
        this.mHostname.addTextChangedListener(mTextWatcher);
        this.mPort = (EditText) findViewById(R.id.port);
        this.mPort.setText("5222");
        this.mPort.addTextChangedListener(mTextWatcher);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);
        final MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
        final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        final MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);
        final MenuItem clearDevices = menu.findItem(R.id.action_clear_devices);
        final MenuItem changePresence = menu.findItem(R.id.action_change_presence);

        if (mAccount != null && mAccount.isOnlineAndConnected()) {
            if (!mAccount.getXmppConnection().getFeatures().blocking()) {
                showBlocklist.setVisible(false);
            }
            if (!mAccount.getXmppConnection().getFeatures().register()) {
                changePassword.setVisible(false);
            }

            Set<Integer> otherDevices = mAccount.getAxolotlService().getOwnDeviceIds();
            if (otherDevices == null || otherDevices.isEmpty() || !Config.supportOmemo()) {
                clearDevices.setVisible(false);
            }
            changePresence.setVisible(true);
        } else {
            showQrCode.setVisible(false);
            showBlocklist.setVisible(false);
            changePassword.setVisible(false);
            clearDevices.setVisible(false);
            changePresence.setVisible(false);

        }
        return true;
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
            if (!mInitMode) {
                if (getActionBar() != null) {
                    getActionBar().setTitle(getString(R.string.account_details));
                }
            } else {
                this.mAvatar.setVisibility(View.GONE);
                if (getActionBar() != null) {
                    getActionBar().setTitle(R.string.action_add_account);
                }
            }
        }
        SharedPreferences preferences = getPreferences();
        this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onBackendConnected() {
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            if (this.mAccount != null) {
                this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
                if (this.mAccount.getPrivateKeyAlias() != null) {
                    if (this.mInitMode) {
                    }
                }
                updateAccountInformation(true);
            }
        }
        if(mAccount.getPresenceStatusMessage() == null) {
            mStatusMessage.setText(R.string.no_status);
            mStatusView.setVisibility(View.VISIBLE);
            mStatus.setVisibility(View.VISIBLE);
        } else {
            mStatusMessage.setText(mAccount.getPresenceStatusMessage());
        }
        if ((Config.MAGIC_CREATE_DOMAIN == null && this.xmppConnectionService.getAccounts().size() == 0)
                || (this.mAccount != null && this.mAccount == xmppConnectionService.getPendingAccount())) {
            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(false);
                getActionBar().setDisplayShowHomeEnabled(false);
                getActionBar().setHomeButtonEnabled(false);
            }
        }
        if (Config.DOMAIN_LOCK == null) {
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_block_list:
                final Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
                showBlocklistIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
                startActivity(showBlocklistIntent);
                break;
            case R.id.action_PublishProfilePictureActivity:
                final Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toBareJid().toString());
                startActivity(intent);
                break;
            case R.id.action_check_updates:
                startActivity(new Intent(this, UpdaterActivity.class));
                break;
            case R.id.action_restart:
                if (xmppConnectionServiceBound) {
                    unbindService(mConnection);
                    xmppConnectionServiceBound = false;
                }
                stopService(new Intent(EditAccountActivity.this,
                        XmppConnectionService.class));
                finish();
                break;
            case R.id.action_change_password_on_server:
                gotoChangePassword(null);
                break;
            case R.id.action_clear_devices:
                showWipePepDialog();
                break;
            case R.id.resetkeys:
                showRegenerateAxolotlKeyDialog();
                break;
            case R.id.action_renew_certificate:
                renewCertificate();
                break;
            case R.id.action_change_presence:
                changePresence();
                break;
            case R.id.action_change_presence2:
                changePresence();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void gotoChangePassword(String newPassword) {
        final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
        changePasswordIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
        if (newPassword != null) {
            changePasswordIntent.putExtra("password", newPassword);
        }
        startActivity(changePasswordIntent);
    }

    private void renewCertificate() {
        KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
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
            this.mHostname.setText("");
            this.mHostname.getEditableText().append(this.mAccount.getHostname());
            this.mPort.setText("");
            this.mPort.getEditableText().append(String.valueOf(this.mAccount.getPort()));
            this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

        }
        if (!mInitMode) {
            this.mAvatar.setVisibility(View.VISIBLE);
            this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(180)));
            BitmapDrawable bm = getQrCode();
            if (bm != null) ((ImageView) findViewById(R.id.iv_cqr)).setImageDrawable(bm);
        }
        if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mAccount_info.setVisibility(View.GONE);
            this.mQR_View.setVisibility(View.GONE);
            this.mQR_Text.setVisibility(View.GONE);
            this.mAvatar.setVisibility(View.GONE);
            this.mAccountJid.setEnabled(true);
            Intent intent = new Intent(EditAccountActivity.this, RegisterActivity.class);
            this.startActivity(intent);
        } else {
        }
        if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
        }
    }

    public void showRegenerateAxolotlKeyDialog() {
        Builder builder = new Builder(this);
        builder.setTitle(R.string.regenerate_omemo_key);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(R.string.clear_other_devices_desc);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().regenerateKeys(false);
                    }
                });
        builder.create().show();
    }

    public void showWipePepDialog() {
        Builder builder = new Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.accept),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().wipeOtherPepDevices();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }


    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
