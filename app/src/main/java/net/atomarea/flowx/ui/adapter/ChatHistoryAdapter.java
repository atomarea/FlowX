package net.atomarea.flowx.ui.adapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatHistory;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.activities.ChatHistoryActivity;
import net.atomarea.flowx.ui.activities.ImageViewerActivity;
import net.atomarea.flowx.ui.view.ReadIndicatorView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {

    private ChatHistoryActivity activity;
    private ChatHistory chatHistory;

    public ChatHistoryAdapter(ChatHistoryActivity activity, ChatHistory chatHistory) {
        this.activity = activity;
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
        final ChatMessage chatMessage = chatHistory.getChatMessages().get(position);
        if (holder.ReadIndicator != null) holder.ReadIndicator.setChatMessage(chatMessage);
        if (chatMessage.getType().equals(ChatMessage.Type.Text))
            holder.Message.setText(Html.fromHtml(chatMessage.getData().replaceAll("\n", "<br />")));
        if (chatMessage.getType().equals(ChatMessage.Type.Image)) {
            holder.Progress.setVisibility(View.VISIBLE);
            holder.MessageImage.setImageDrawable(null);
            holder.MessageImage.setOnClickListener(null);
            Data.loadBitmap(activity, new Data.BitmapLoadedCallback() {
                @Override
                public void onBitmapLoaded(final Bitmap bitmap) {
                    holder.MessageImage.setImageDrawable(new BitmapDrawable(activity.getResources(), bitmap));
                    holder.MessageImage.setAlpha(0f);
                    holder.MessageImage.animate().setStartDelay(400).alpha(1f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            holder.Progress.setVisibility(View.GONE);
                            holder.MessageImage.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent imageViewer = new Intent(activity, ImageViewerActivity.class);
                                    imageViewer.putExtra(Data.EXTRA_TOKEN_CHAT_MESSAGE, chatMessage);
                                    if (Build.VERSION.SDK_INT >= 16)
                                        activity.startActivity(imageViewer, ActivityOptionsCompat.makeSceneTransitionAnimation(activity, holder.MessageImage, "image").toBundle());
                                    else
                                        activity.startActivity(imageViewer);
                                }
                            });
                        }
                    }).start();
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

        private ReadIndicatorView ReadIndicator;
        private TextView Message;
        private ImageView MessageImage;
        private TextView Info;

        private ProgressBar Progress;

        public ViewHolder(View v) {
            super(v);
            ReadIndicator = (ReadIndicatorView) v.findViewById(R.id.read_indicator);
            Message = (TextView) v.findViewById(R.id.message);
            MessageImage = (ImageView) v.findViewById(R.id.message_image);
            Info = (TextView) v.findViewById(R.id.info);

            Progress = (ProgressBar) v.findViewById(R.id.progress);
        }

    }

}
