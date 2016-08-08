package net.atomarea.flowx.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.async.AvatarImageUpdater;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.activities.ChatHistoryActivity;
import net.atomarea.flowx.ui.activities.ContactDetailActivity;
import net.atomarea.flowx.ui.view.ReadIndicatorView;
import net.atomarea.flowx.xmpp.ChatState;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    private Context context;

    public ChatListAdapter(Context context) {
        this.context = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_chat_list, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        ChatHistory chatHistory = Data.getChats().get(position);
        holder.ContactName.setText(chatHistory.getRemoteContact().getName());
        if (chatHistory.getChatState() != null && !chatHistory.getChatState().equals(ChatState.State.Idle) && chatHistory.getChatStateTimeout() > System.currentTimeMillis()) {
            ChatState.State state = chatHistory.getChatState();
            if (state.equals(ChatState.State.Writing)) {
                holder.LastMessage.setText(Html.fromHtml("<b>" + context.getResources().getString(R.string.state_writing) + "</b>"));
                holder.LastMessage.setTextColor(ContextCompat.getColor(context, R.color.colorAccent2));
            }
        } else if (chatHistory.getLatestChatMessage() != null) {
            holder.LastMessage.setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_light));
            if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.Text)
                holder.LastMessage.setText(Html.fromHtml(chatHistory.getLatestChatMessage().getData()));
            else {
                if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.Image)
                    holder.LastMessage.setText(R.string.image);
                else if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.Audio)
                    holder.LastMessage.setText(R.string.audio);
                else if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.Video)
                    holder.LastMessage.setText(R.string.video);
                else if (chatHistory.getLatestChatMessage().getType() == ChatMessage.Type.File)
                    holder.LastMessage.setText(R.string.file);
            }
            if (!chatHistory.getLatestChatMessage().isSent())
                holder.readIndicator.setVisibility(View.GONE);
            else {
                holder.readIndicator.init(ContextCompat.getColor(context, R.color.colorPrimary));
                holder.readIndicator.setVisibility(View.VISIBLE);
                holder.readIndicator.setChatMessage(chatHistory.getLatestChatMessage());
            }
        }
        if (chatHistory.getLatestChatMessage() == null)
            holder.readIndicator.setVisibility(View.GONE);
        if (holder.ContactPicture != null)
            new AvatarImageUpdater(chatHistory.getRemoteContact().getXmppAddress(), holder.ContactPicture).execute();
        holder.ChatRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent chatHistoryActivity = new Intent(context, ChatHistoryActivity.class);
                chatHistoryActivity.putExtra(Data.EXTRA_CHAT_HISTORY_POSITION, holder.getAdapterPosition());
                context.startActivity(chatHistoryActivity);
            }
        });
        holder.ContactPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent contactDetailActivity = new Intent(context, ContactDetailActivity.class);
                contactDetailActivity.putExtra(Data.EXTRA_CONTACT_POSITION, Data.getAccountPosition(Data.getChats().get(holder.getAdapterPosition()).getRemoteContact()));
                context.startActivity(contactDetailActivity);
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
        return Data.getChats().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout ChatRow;
        private ReadIndicatorView readIndicator;
        private ImageView ContactPicture;
        private TextView ContactName;
        private TextView LastMessage;
        private ImageView QuickReplyButton;

        public ViewHolder(View v) {
            super(v);
            ChatRow = (LinearLayout) v.findViewById(R.id.chat_row);
            readIndicator = (ReadIndicatorView) v.findViewById(R.id.read_indicator);
            ContactPicture = (ImageView) v.findViewById(R.id.contact_picture);
            ContactName = (TextView) v.findViewById(R.id.contact_name);
            LastMessage = (TextView) v.findViewById(R.id.last_message);
            QuickReplyButton = (ImageView) v.findViewById(R.id.quick_reply_button);
        }

    }

}
