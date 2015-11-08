package net.atomarea.flowx.ui;

import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.AdapterView;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.xmpp.OnUpdateBlocklist;
import net.atomarea.flowx.xmpp.jid.Jid;

import java.util.Collections;

public class BlocklistActivity extends AbstractSearchableListItemActivity implements OnUpdateBlocklist {

    private Account account = null;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(final AdapterView<?> parent,
                                           final View view,
                                           final int position,
                                           final long id) {
                BlockContactDialog.show(parent.getContext(), xmppConnectionService, (Contact) getListItems().get(position));
                return true;
            }
        });
    }

    @Override
    public void onBackendConnected() {
        for (final Account account : xmppConnectionService.getAccounts()) {
            if (account.getJid().toString().equals(getIntent().getStringExtra("account"))) {
                this.account = account;
                break;
            }
        }
        filterContacts();
    }

    @Override
    protected void filterContacts(final String needle) {
        getListItems().clear();
        if (account != null) {
            for (final Jid jid : account.getBlocklist()) {
                final Contact contact = account.getRoster().getContact(jid);
                if (contact.match(needle) && contact.isBlocked()) {
                    getListItems().add(contact);
                }
            }
            Collections.sort(getListItems());
        }
        getListItemAdapter().notifyDataSetChanged();
    }

    protected void refreshUiReal() {
        final Editable editable = getSearchEditText().getText();
        if (editable != null) {
            filterContacts(editable.toString());
        } else {
            filterContacts();
        }
    }

    @Override
    public void OnUpdateBlocklist(final OnUpdateBlocklist.Status status) {
        refreshUi();
    }
}
