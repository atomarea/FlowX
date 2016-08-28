package net.atomarea.flowx.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.manuelpeinado.fadingactionbar.FadingActionBarHelper;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.services.XmppConnectionService.OnAccountUpdate;
import net.atomarea.flowx.services.XmppConnectionService.OnRosterUpdate;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.OnKeyStatusUpdated;
import net.atomarea.flowx.xmpp.OnUpdateBlocklist;
import net.atomarea.flowx.xmpp.XmppConnection;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;

import java.util.List;

import github.ankushsachdeva.emojicon.EmojiconTextView;

public class ContactDetailsActivity extends XmppActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist, OnKeyStatusUpdated {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    private Contact contact;
    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
        }
    };
    private OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (isChecked) {
                if (contact
                        .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    xmppConnectionService.sendPresencePacket(contact
                                    .getAccount(),
                            xmppConnectionService.getPresenceGenerator()
                                    .sendPresenceUpdatesTo(contact));
                } else {
                    contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                }
            } else {
                contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesTo(contact));
            }
        }
    };
    private OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (isChecked) {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .requestPresenceUpdatesFrom(contact));
            } else {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesFrom(contact));
            }
        }
    };
    private Jid accountJid;
    private Jid contactJid;
    private TextView contactJidTv;
    private TextView accountJidTv;
    private TextView status;
    private CheckBox send;
    private CheckBox receive;
    private RelativeLayout statusView;
    private BootstrapButton addContactButton;
    private LinearLayout keys;
    private TextView statusMessage;
    private ImageView avatar;
    private boolean showLastSeen = true;

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri() {
        if (contact != null) {
            return "xmpp:" + contact.getJid().toBareJid().toString();
        } else {
            return "";
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.fromString(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final InvalidJidException ignored) {
            }
            try {
                this.contactJid = Jid.fromString(getIntent().getExtras().getString("contact"));
            } catch (final InvalidJidException ignored) {
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share:
                shareUri();
                break;
            case R.id.action_delete_contact:
                builder.setTitle(getString(R.string.action_delete_contact))
                        .setMessage(
                                getString(R.string.remove_contact_text,
                                        contact.getJid()))
                        .setPositiveButton(getString(R.string.delete),
                                removeFromRoster).create().show();
                break;
            case R.id.action_edit_contact:
                Uri systemAccount = contact.getSystemAccount();
                if (systemAccount == null) {
                    quickEdit(contact.getDisplayName(), 0, new OnValueEdited() {

                        @Override
                        public void onValueEdited(String value) {
                            contact.setServerName(value);
                            ContactDetailsActivity.this.xmppConnectionService
                                    .pushContactToServer(contact);
                            populateView();
                        }
                    });
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(systemAccount);
                    startActivity(intent);
                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);
        if (contact == null) {
            return true;
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        return true;
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        BitmapDrawable bmd = new BitmapDrawable(this.getResources(), avatarService().get(contact, size.x));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(bmd);

        FadingActionBarHelper helper = new FadingActionBarHelper()
                .actionBarBackground(new ColorDrawable(ContextCompat.getColor(this, R.color.primary)))
                .headerView(iv)
                .contentLayout(R.layout.activity_contact_details);

        setContentView(helper.createView(this));

        helper.initActionBar(this);

        contactJidTv = (TextView) findViewById(R.id.details_contactjid);
        accountJidTv = (TextView) findViewById(R.id.details_account);
        statusMessage = (TextView) findViewById(R.id.status_message);
        avatar = (ImageView) findViewById(R.id.details_contact_photo);
        status = (TextView) findViewById(R.id.status);
        statusView = (RelativeLayout) findViewById(R.id.statusView);
        send = (CheckBox) findViewById(R.id.details_send_presence);
        receive = (CheckBox) findViewById(R.id.details_receive_presence);
        addContactButton = (BootstrapButton) findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddToRosterDialog(contact);
            }
        });
        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        BitmapDrawable bm = getQrCode();
        if (bm != null) ((ImageView) findViewById(R.id.iv_cqr)).setImageDrawable(bm);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showLastSeen = preferences.getBoolean("last_activity", true);

        if (getActionBar() == null) return; // lol

        getActionBar().setCustomView(R.layout.actionbar);
        getActionBar().setDisplayShowCustomEnabled(true);

        ((EmojiconTextView) getActionBar().getCustomView().findViewById(R.id.title)).setText(contact.getDisplayName());

        invalidateOptionsMenu();

        if (contact.showInRoster()) {
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
            status.setVisibility(View.VISIBLE);
            addContactButton.setVisibility(View.GONE);
            send.setOnCheckedChangeListener(null);
            receive.setOnCheckedChangeListener(null);

            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (statusMessages.size() == 0) {
                status.setVisibility(View.VISIBLE);
                statusView.setVisibility(View.VISIBLE);
                statusMessage.setText(R.string.no_status);
            } else {
                StringBuilder builder = new StringBuilder();
                statusMessage.setVisibility(View.VISIBLE);
                statusView.setVisibility(View.VISIBLE);
                status.setVisibility(View.VISIBLE);
                int s = statusMessages.size();
                for (int i = 0; i < s; ++i) {
                    if (s > 1) {
                        builder.append("");
                    }
                    builder.append(statusMessages.get(i));
                    if (i < s - 1) {
                        builder.append("\n");
                    }
                }
                statusMessage.setText(builder);
            }

            if (contact.getOption(Contact.Options.FROM)) {
                send.setText(R.string.send_presence_updates);
                send.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                send.setChecked(false);
                send.setText(R.string.send_presence_updates);
            } else {
                send.setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    send.setChecked(true);
                } else {
                    send.setChecked(false);
                }
            }
            if (contact.getOption(Contact.Options.TO)) {
                receive.setText(R.string.receive_presence_updates);
                receive.setChecked(true);
            } else {
                receive.setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    receive.setChecked(true);
                } else {
                    receive.setChecked(false);
                    statusView.setVisibility(View.GONE);
                    status.setVisibility(View.GONE);
                    statusMessage.setVisibility(View.GONE);
                    ((EmojiconTextView) getActionBar().getCustomView().findViewById(R.id.subtitle)).setText("...");
                }
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                receive.setEnabled(true);
                send.setEnabled(true);
            } else {
                receive.setEnabled(false);
                send.setEnabled(false);
            }
            send.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            receive.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
            addContactButton.setVisibility(View.VISIBLE);
            send.setVisibility(View.GONE);
            statusMessage.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        }

        if (contact.isBlocked()) {
            status.setVisibility(View.VISIBLE);
            ((EmojiconTextView) getActionBar().getCustomView().findViewById(R.id.subtitle)).setText(R.string.contact_blocked);
            statusMessage.setVisibility(View.VISIBLE);
            statusMessage.setText(R.string.contact_blocked);
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        } else {
            if (showLastSeen && contact.getLastseen() > 0) {
                ((EmojiconTextView) getActionBar().getCustomView().findViewById(R.id.subtitle)).setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
            } else {
                ((EmojiconTextView) getActionBar().getCustomView().findViewById(R.id.subtitle)).setText("...");
            }
        }

        if (contact.getPresences().size() > 1) {
            contactJidTv.setText(contact.getDisplayName() + " ("
                    + contact.getPresences().size() + ")");
        } else {
            contactJidTv.setText(contact.getDisplayName().toString());
        }
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getLocalpart();
        } else {
            account = contact.getAccount().getJid().toBareJid().toString();
        }
        accountJidTv.setText(getString(R.string.using_account, account));
    }

    @Override
    public void onBackendConnected() {
        if ((accountJid != null) && (contactJid != null)) {
            Account account = xmppConnectionService
                    .findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }
}
