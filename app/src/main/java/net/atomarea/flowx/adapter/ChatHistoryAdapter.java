package net.atomarea.flowx.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {

    private Context context;
    private Data data;
    private ChatHistory chatHistory;

    public ChatHistoryAdapter(Context context, Data data, ChatHistory chatHistory) {
        this.context = context;
        this.data = data;
        this.chatHistory = chatHistory;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = R.layout.row_msg_error;
        if (viewType < 0) {
            viewType *= -1;
            if (viewType == ChatMessage.Type.Text.ordinal() + 1)
                layoutId = R.layout.row_msg_text_sent;
            if (viewType == ChatMessage.Type.Image.ordinal() + 1)
                layoutId = R.layout.row_msg_image_sent;
        } else {
            if (viewType == ChatMessage.Type.Text.ordinal() + 1)
                layoutId = R.layout.row_msg_text_recv;
            if (viewType == ChatMessage.Type.Image.ordinal() + 1)
                layoutId = R.layout.row_msg_image_recv;
        }
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        ChatMessage chatMessage = chatHistory.getChatMessages().get(position);
        if (chatMessage.getType().equals(ChatMessage.Type.Text))
            holder.Message.setText(Html.fromHtml(chatMessage.getData()));
        if (chatMessage.getType().equals(ChatMessage.Type.Image)) {
            holder.MessageImage.setImageDrawable(null);
            data.loadBitmap(context, new Data.BitmapLoadedCallback() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap) {
                    holder.MessageImage.setImageDrawable(new BitmapDrawable(context.getResources(), bitmap));
                }
            }, chatMessage);
        }
        if (holder.Info != null) {
            Date date = new Date(chatMessage.getTime());
            Calendar cal = Calendar.getInstance();
            Calendar cur = Calendar.getInstance();
            cal.setTime(date);
            if (cal.get(Calendar.DAY_OF_MONTH) == cur.get(Calendar.DAY_OF_MONTH) && cal.get(Calendar.MONTH) == cur.get(Calendar.MONTH) && cal.get(Calendar.YEAR) == cur.get(Calendar.YEAR))
                holder.Info.setText(DateFormat.getTimeInstance().format(date));
            else
                holder.Info.setText(DateFormat.getDateTimeInstance().format(date));
        }
    }

    @Override
    public int getItemCount() {
        return chatHistory.getChatMessages().size();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage chatMessage = chatHistory.getChatMessages().get(position);
        int type = chatMessage.getType().ordinal() + 1;
        if (chatMessage.isSent()) type *= -1;
        return type;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView Message;
        private ImageView MessageImage;
        private TextView Info;

        public ViewHolder(View v) {
            super(v);
            Message = (TextView) v.findViewById(R.id.message);
            MessageImage = (ImageView) v.findViewById(R.id.message_image);
            Info = (TextView) v.findViewById(R.id.info);
        }

    }

}
