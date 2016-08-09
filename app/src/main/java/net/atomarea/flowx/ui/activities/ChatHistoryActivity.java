package net.atomarea.flowx.ui.activities;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.adapter.ChatHistoryAdapter;
import net.atomarea.flowx.xmpp.ChatState;

import java.text.DateFormat;
import java.util.Date;

public class ChatHistoryActivity extends AppCompatActivity {

    private static ChatHistoryActivity instance;

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

        if (getIntent().getIntExtra(Data.EXTRA_CHAT_HISTORY_POSITION, 0) == -1) {
            finish();
            return;
        }
        chatHistory = Data.getChats().get(getIntent().getIntExtra(Data.EXTRA_CHAT_HISTORY_POSITION, 0));

        getSupportActionBar().setTitle(chatHistory.getRemoteContact().getName());
        getSupportActionBar().setSubtitle(DateFormat.getDateTimeInstance().format(new Date(chatHistory.getRemoteContact().getLastOnline())));

        editTextMessageInput = (EditText) findViewById(R.id.edit_message);
        recyclerViewChatHistory = (RecyclerView) findViewById(R.id.chat_history);
        recyclerViewChatHistory.setLayoutManager(linearLayoutManager = new LinearLayoutManager(this));
        linearLayoutManager.setStackFromEnd(true);
        recyclerViewChatHistory.setAdapter(new ChatHistoryAdapter(this, chatHistory));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = editTextMessageInput.getText().toString();
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().remove("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress()).apply();
                Data.getConnection().sendChatState(chatHistory, ChatState.State.Idle);
                Data.sendTextMessage(chatHistory, message);
                editTextMessageInput.setText("");
                recyclerViewChatHistory.getAdapter().notifyDataSetChanged();
                recyclerViewChatHistory.smoothScrollToPosition(chatHistory.getChatMessages().size() - 1);
            }
        });

        instance = this;

        Data.getConnection().sendReadMarker(chatHistory);

        String LastMessage = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress(), null);
        if (LastMessage != null) editTextMessageInput.setText(LastMessage);

        editTextMessageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Data.getConnection().sendChatState(chatHistory, ChatState.State.Writing);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
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
        if (v.getId() == R.id.bold_button) {
            editTextMessageInput.getText().append("<b></b>");
            editTextMessageInput.setSelection(editTextMessageInput.getText().length() - 4);
        }
        if (v.getId() == R.id.italic_button) {
            editTextMessageInput.getText().append("<i></i>");
            editTextMessageInput.setSelection(editTextMessageInput.getText().length() - 4);
        }
        if (v.getId() == R.id.underline_button) {
            editTextMessageInput.getText().append("<u></u>");
            editTextMessageInput.setSelection(editTextMessageInput.getText().length() - 4);
        }
    }

    public void refresh() {
        recyclerViewChatHistory.getAdapter().notifyDataSetChanged();
        if (chatHistory.getChatMessages().size() != 0)
            recyclerViewChatHistory.smoothScrollToPosition(chatHistory.getChatMessages().size() - 1);
        Data.getConnection().sendReadMarker(chatHistory);
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
    protected void onDestroy() {
        super.onDestroy();
        instance = null;

        if (!editTextMessageInput.getText().toString().trim().equals(""))
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress(), editTextMessageInput.getText().toString()).apply();
        else
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().remove("lastMessage:" + chatHistory.getRemoteContact().getXmppAddress()).apply();
    }
}
