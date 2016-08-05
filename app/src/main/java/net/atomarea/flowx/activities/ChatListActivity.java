package net.atomarea.flowx.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;

import net.atomarea.flowx.R;
import net.atomarea.flowx.adapter.ChatListAdapter;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.other.DrawableItemDecoration;

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
        fab.setOnClickListener(new View.OnClickListener() {
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
}
