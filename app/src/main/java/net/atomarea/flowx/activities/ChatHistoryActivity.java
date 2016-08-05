package net.atomarea.flowx.activities;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import net.atomarea.flowx.R;
import net.atomarea.flowx.adapter.ChatHistoryAdapter;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.Data;

public class ChatHistoryActivity extends AppCompatActivity {

    private ChatHistory chatHistory;

    private EditText editTextMessageInput;
    private RecyclerView recyclerViewChatHistory;
    private LinearLayoutManager linearLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        chatHistory = Data.getChats().get(getIntent().getIntExtra(Data.EXTRA_CHAT_HISTORY_POSITION, 0));

        getSupportActionBar().setTitle(chatHistory.getRemoteContact().getName());

        editTextMessageInput = (EditText) findViewById(R.id.edit_message);
        recyclerViewChatHistory = (RecyclerView) findViewById(R.id.chat_history);
        recyclerViewChatHistory.setLayoutManager(linearLayoutManager = new LinearLayoutManager(this));
        linearLayoutManager.setStackFromEnd(true);
        recyclerViewChatHistory.setAdapter(new ChatHistoryAdapter(this, chatHistory));

        String LastMessage = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress(), null);
        if (LastMessage != null) editTextMessageInput.setText(LastMessage);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onFormatButtonClick(View v) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!editTextMessageInput.getText().toString().trim().equals(""))
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress(), editTextMessageInput.getText().toString()).apply();
        else
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().remove("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress()).apply();
    }
}
