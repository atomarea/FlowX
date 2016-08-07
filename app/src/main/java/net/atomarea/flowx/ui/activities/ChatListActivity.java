package net.atomarea.flowx.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.adapter.ChatListAdapter;
import net.atomarea.flowx.ui.other.DrawableItemDecoration;
import net.atomarea.flowx.ui.settings.SettingsActivity;

public class ChatListActivity extends AppCompatActivity {

    private static ChatListActivity instance;

    private RecyclerView recyclerViewChatList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerViewChatList = (RecyclerView) findViewById(R.id.chat_list);
        recyclerViewChatList.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChatList.addItemDecoration(new DrawableItemDecoration(this, R.drawable.divider));
        recyclerViewChatList.setAdapter(new ChatListAdapter(this));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ChatListActivity.this, ContactsActivity.class));
            }
        });

        instance = this;

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Snackbar.make(findViewById(R.id.toolbar), getResources().getString(R.string.logged_in_as, Data.getConnection().getLocalUser()), Snackbar.LENGTH_LONG).show();
            }
        }, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Data.clean();
        recyclerViewChatList.getAdapter().notifyDataSetChanged();
    }

    public void refresh() {
        recyclerViewChatList.getAdapter().notifyDataSetChanged();
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
