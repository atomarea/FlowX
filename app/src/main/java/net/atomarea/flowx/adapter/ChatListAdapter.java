package net.atomarea.flowx.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.activities.ChatHistoryActivity;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    private Context context;
    private Data data;

    public ChatListAdapter(Context context, Data data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_chat_list, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        ChatHistory chatHistory = data.getChats().get(position);
        holder.ContactName.setText(chatHistory.getRemoteContact().getName());
        if (chatHistory.getLatestChatMessage() != null) {
            if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.Text)
                holder.LastMessage.setText(Html.fromHtml(chatHistory.getLatestChatMessage().getData()));
            else
                holder.LastMessage.setText("TODO");
        }
        holder.ChatRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent chatHistoryActivity = new Intent(context, ChatHistoryActivity.class);
                chatHistoryActivity.putExtra(Data.EXTRA_TOKEN, data);
                chatHistoryActivity.putExtra(Data.EXTRA_CHAT_HISTORY_POSITION, holder.getAdapterPosition());
                context.startActivity(chatHistoryActivity);
            }
        });
        holder.ContactPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        holder.QuickReplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    @Override
    public int getItemCount() {
        return data.getChats().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout ChatRow;
        private ImageView ContactPicture;
        private TextView ContactName;
        private TextView LastMessage;
        private ImageView QuickReplyButton;

        public ViewHolder(View v) {
            super(v);
            ChatRow = (LinearLayout) v.findViewById(R.id.chat_row);
            ContactPicture = (ImageView) v.findViewById(R.id.contact_picture);
            ContactName = (TextView) v.findViewById(R.id.contact_name);
            LastMessage = (TextView) v.findViewById(R.id.last_message);
            QuickReplyButton = (ImageView) v.findViewById(R.id.quick_reply_button);
        }

    }

}
