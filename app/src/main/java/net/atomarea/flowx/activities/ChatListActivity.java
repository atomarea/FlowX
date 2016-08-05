package net.atomarea.flowx.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.atomarea.flowx.R;
import net.atomarea.flowx.adapter.ChatListAdapter;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.other.DrawableItemDecoration;
import net.atomarea.flowx.settings.SettingsActivity;

public class ChatListActivity extends AppCompatActivity {

    private Data data;

    private RecyclerView recyclerViewChatList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        data = (Data) getIntent().getSerializableExtra(Data.EXTRA_TOKEN);

        recyclerViewChatList = (RecyclerView) findViewById(R.id.chat_list);
        recyclerViewChatList.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChatList.addItemDecoration(new DrawableItemDecoration(this, R.drawable.divider));
        recyclerViewChatList.setAdapter(new ChatListAdapter(this, data));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent contactsActivity = new Intent(ChatListActivity.this, ContactsActivity.class);
                contactsActivity.putExtra(Data.EXTRA_TOKEN, data);
                startActivity(contactsActivity);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        data.clean();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
