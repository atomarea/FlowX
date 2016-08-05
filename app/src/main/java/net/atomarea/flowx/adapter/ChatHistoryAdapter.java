package net.atomarea.flowx.adapter;

import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;

import java.text.DateFormat;
import java.util.Date;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {

    private ChatHistory chatHistory;

    public ChatHistoryAdapter(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout_id = (viewType == 0 ? R.layout.row_chat_history_sent : R.layout.row_chat_history_recv);
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout_id, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ChatMessage chatMessage = chatHistory.getChatMessages().get(position);
        holder.Message.setText(Html.fromHtml(chatMessage.getData()));
        holder.Info.setText(DateFormat.getDateTimeInstance().format(new Date(chatMessage.getTime())));
    }

    @Override
    public int getItemCount() {
        return chatHistory.getChatMessages().size();
    }

    @Override
    public int getItemViewType(int position) {
        return (chatHistory.getChatMessages().get(position).isSent() ? 0 : 1);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView Message;
        private TextView Info;

        public ViewHolder(View v) {
            super(v);
            Message = (TextView) v.findViewById(R.id.message);
            Info = (TextView) v.findViewById(R.id.info);
        }

    }

}
