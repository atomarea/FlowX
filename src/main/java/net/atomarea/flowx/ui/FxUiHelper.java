package net.atomarea.flowx.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.utils.UIHelper;

import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by Tom on 10.05.2016.
 */
public class FxUiHelper {

    public static void loadAvatar(Conversation conversation, ImageView imageView, int dp) {
        if (cancelPotentialWork(conversation, imageView)) {
            final Bitmap bm = FxUi.App.avatarService().get(conversation, getPixel(dp), true);
            if (bm != null) {
                cancelPotentialWork(conversation, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName()));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(FxUi.App.getResources(),
                        null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(conversation);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }

    }

    public static boolean cancelPotentialWork(Conversation conversation, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Conversation oldConversation = bitmapWorkerTask.conversation;
            if (oldConversation == null || conversation != oldConversation)
                bitmapWorkerTask.cancel(true);
            else return false;
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Conversation conversation = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Conversation... params) {
            return FxUi.App.avatarService().get(params[0], getPixel(56), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    public static int getPixel(int dp) {
        DisplayMetrics metrics = FxUi.App.getResources().getDisplayMetrics();
        return ((int) (dp * metrics.density));
    }

    public static boolean isMessageReceived(Message m) {
        return m.getType() != Message.TYPE_STATUS && m.getStatus() <= Message.STATUS_RECEIVED;
    }

}
