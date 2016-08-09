package net.atomarea.flowx.ui.activities;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.adapter.ContactsListAdapter;
import net.atomarea.flowx.ui.dialog.Dialog;
import net.atomarea.flowx.ui.other.DrawableItemDecoration;

public class ContactsActivity extends AppCompatActivity {

    private static ContactsActivity instance;

    private RecyclerView recyclerViewContactsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerViewContactsList = (RecyclerView) findViewById(R.id.contacts_list);
        recyclerViewContactsList.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewContactsList.addItemDecoration(new DrawableItemDecoration(this, R.drawable.divider));
        recyclerViewContactsList.setAdapter(new ContactsListAdapter(this));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog.newInstance(ContactsActivity.this, R.layout.dialog_add_contact, R.string.title_add_contact, R.string.action_add_contact, new Dialog.OnClickListener() {
                    @Override
                    public void onPositiveButtonClicked(View rootView) {
                        String xmppAddress = ((EditText) rootView.findViewById(R.id.add_contact)).getText().toString();
                        if (!xmppAddress.equals("")) {
                            xmppAddress += "@flowx.im";
                            Data.getConnection().addRosterEntry(xmppAddress);
                        }

                    }
                }).show();
            }
        });

        instance = this;
    }

    public void refresh() {
        recyclerViewContactsList.getAdapter().notifyDataSetChanged();
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
