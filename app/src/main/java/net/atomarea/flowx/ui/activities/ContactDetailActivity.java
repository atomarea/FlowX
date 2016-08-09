package net.atomarea.flowx.ui.activities;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.async.AvatarImageUpdater;
import net.atomarea.flowx.data.Account;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.dialog.Dialog;

public class ContactDetailActivity extends AppCompatActivity {

    private static ContactDetailActivity instance;

    private Account contact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_detail);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        contact = Data.getContacts().get(getIntent().getIntExtra(Data.EXTRA_CONTACT_POSITION, 0));

        getSupportActionBar().setTitle(contact.getName());

        if (contact.getStatus() == null || contact.getStatus().trim().equals(""))
            findViewById(R.id.card_status).setVisibility(View.GONE);
        else ((TextView) findViewById(R.id.status)).setText(contact.getStatus());

        new AvatarImageUpdater(contact.getXmppAddress(), ((ImageView) findViewById(R.id.contact_picture))).execute(true);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog.newInstance(ContactDetailActivity.this, R.layout.dialog_rename_contact, R.string.title_rename_contact, R.string.action_rename_contact, new Dialog.OnClickListener() {
                    @Override
                    public void onPositiveButtonClicked(View rootView) {
                        String newName = ((EditText) rootView.findViewById(R.id.rename_contact)).getText().toString();
                        if (!newName.trim().equals("")) {
                            Data.getConnection().updateRosterEntryName(contact, newName);
                        }
                    }
                }).show();
            }
        });

        instance = this;
    }

    public void refresh() {
    }

    public static void doRefresh() {
        if (instance != null) instance.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (instance != null && !instance.isFinishing())
                    instance.refresh();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
