package net.atomarea.flowx.ui.activities;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import net.atomarea.flowx.R;
import net.atomarea.flowx.ui.adapter.ContactsListAdapter;
import net.atomarea.flowx.ui.other.DrawableItemDecoration;

public class ContactsActivity extends AppCompatActivity {

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
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
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

}
