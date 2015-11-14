package net.atomarea.flowx.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.services.XmppConnectionService.OnAccountUpdate;
import net.atomarea.flowx.ui.adapter.AccountAdapter;

import java.util.ArrayList;
import java.util.List;

public class ManageAccountActivity extends XmppActivity implements OnAccountUpdate, KeyChainAliasCallback, XmppConnectionService.OnAccountCreated {

    protected Account selectedAccount = null;

    protected final List<Account> accountList = new ArrayList<>();
    protected ListView accountListView;
    protected AccountAdapter mAccountAdapter;

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        synchronized (this.accountList) {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
        }
        invalidateOptionsMenu();
        mAccountAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.manage_accounts);

        accountListView = (ListView) findViewById(R.id.account_list);
        this.mAccountAdapter = new AccountAdapter(this, accountList);
        accountListView.setAdapter(this.mAccountAdapter);
        /*accountListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View view,
                                    int position, long arg3) {
                //switchToAccount(accountList.get(position));
            }
        });*/
        registerForContextMenu(accountListView);

        getActionBar().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ManageAccountActivity.this.getMenuInflater().inflate(
                R.menu.manageaccounts_context, menu);
        AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
        this.selectedAccount = accountList.get(acmi.position);
        if (this.selectedAccount.isOptionSet(Account.OPTION_DISABLED)) {
            menu.findItem(R.id.mgmt_account_disable).setVisible(false);
            menu.findItem(R.id.mgmt_account_announce_pgp).setVisible(false);
            menu.findItem(R.id.mgmt_account_publish_avatar).setVisible(false);
        } else {
            menu.findItem(R.id.mgmt_account_enable).setVisible(false);
            menu.findItem(R.id.mgmt_account_announce_pgp).setVisible(!Config.HIDE_PGP_IN_UI);
        }
    }

    @Override
    void onBackendConnected() {
        this.accountList.clear();
        this.accountList.addAll(xmppConnectionService.getAccounts());
        mAccountAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu);

        getMenuInflater().inflate(R.menu.edit_account_menu, menu);
        MenuItem enableAll = menu.findItem(R.id.action_enable_all);
        MenuItem addAccount = menu.findItem(R.id.action_add_account);

        if (!accountsLeftToEnable()) {
            enableAll.setVisible(false);
        }
        MenuItem disableAll = menu.findItem(R.id.action_disable_all);
        if (!accountsLeftToDisable()) {
            disableAll.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mgmt_account_publish_avatar:
                publishAvatar(selectedAccount);
                return true;
            case R.id.mgmt_account_disable:
                disableAccount(selectedAccount);
                return true;
            case R.id.mgmt_account_enable:
                enableAccount(selectedAccount);
                return true;
            case R.id.mgmt_account_delete:
                deleteAccount(selectedAccount);
                return true;
            case R.id.mgmt_account_announce_pgp:
                publishOpenPGPPublicKey(selectedAccount);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
    private void edit (Account account) {
        Intent intent = new Intent(getApplicationContext(), EditAccountActivity.class);
        intent.putExtra("account", account.getJid().toBareJid().toString());
        startActivity(intent);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_account:
                startActivity(new Intent(getApplicationContext(),
                        EditAccountActivity.class));
                break;
            case R.id.action_show_qr_code:
                        showQrCode();
                break;
            case R.id.action_disable_all:
                disableAllAccounts();
                break;
            case R.id.action_enable_all:
                enableAllAccounts();
                break;
            case R.id.action_edit_account:
                if (accountList.size() > 0) {
                    switchToAccount(accountList.get(0));
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    public boolean onNavigateUp() {
        if (xmppConnectionService.getConversations().size() == 0) {
            Intent contactsIntent = new Intent(this,
                    StartConversationActivity.class);
            contactsIntent.setFlags(
                    // if activity exists in stack, pop the stack and go back to it
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            // otherwise, make a new task for it
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            // don't use the new activity animation; finish
                            // animation runs instead
                            Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(contactsIntent);
            finish();
            return true;
        } else {
            return super.onNavigateUp();
        }
    }

    public void onClickTglAccountState(Account account, boolean enable) {
        if (enable) {
            enableAccount(account);
        } else {
            disableAccount(account);
        }
    }

    private void addAccountFromKey() {
        KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
    }

    private void publishAvatar(Account account) {
        Intent intent = new Intent(getApplicationContext(),
                PublishProfilePictureActivity.class);
        intent.putExtra("account", account.getJid().toString());
        startActivity(intent);
    }
    private void disableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            disableAccount(account);
        }
    }

    private boolean accountsLeftToDisable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean accountsLeftToEnable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isOptionSet(Account.OPTION_DISABLED)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void enableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isOptionSet(Account.OPTION_DISABLED)) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            enableAccount(account);
        }
    }

    private void disableAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, true);
        xmppConnectionService.updateAccount(account);
    }

    private void enableAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, false);
        xmppConnectionService.updateAccount(account);
    }

    private void publishOpenPGPPublicKey(Account account) {
        if (ManageAccountActivity.this.hasPgp()) {
            announcePgp(account, null);
        } else {
            this.showInstallPgpDialog();
        }
    }

    private void deleteAccount(final Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                ManageAccountActivity.this);
        builder.setTitle(getString(R.string.mgmt_account_are_you_sure));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.mgmt_account_delete_confirm_text));
        builder.setPositiveButton(getString(R.string.delete),
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xmppConnectionService.deleteAccount(account);
                        selectedAccount = null;
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ANNOUNCE_PGP) {
                announcePgp(selectedAccount, null);
            }
        }
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.createAccountFromKey(alias, this);
        }
    }

    @Override
    public void onAccountCreated(Account account) {
        switchToAccount(account, true);
    }

    @Override
    public void informUser(final int r) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ManageAccountActivity.this, r, Toast.LENGTH_LONG).show();
            }
        });
    }
}
