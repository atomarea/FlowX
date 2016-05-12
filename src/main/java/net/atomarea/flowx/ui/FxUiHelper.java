package net.atomarea.flowx.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.crypto.axolotl.XmppAxolotlSession;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.utils.UIHelper;

import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

import github.ankushsachdeva.emojicon.EmojiconEditText;
import github.ankushsachdeva.emojicon.EmojiconGridView;
import github.ankushsachdeva.emojicon.EmojiconsPopup;
import github.ankushsachdeva.emojicon.emoji.Emojicon;

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
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView, dp);
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

    public static Bitmap createCircleBitmap(Bitmap bitmapimg) {
        Bitmap output = Bitmap.createBitmap(bitmapimg.getWidth(),
                bitmapimg.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmapimg.getWidth(),
                bitmapimg.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmapimg.getWidth() / 2,
                bitmapimg.getHeight() / 2, bitmapimg.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmapimg, rect, rect, paint);
        return output;
    }

    public static BitmapDrawable loadAvatarForToolbar(Conversation c) {
        return new BitmapDrawable(FxUi.App.getResources(), createCircleBitmap(loadAvatar(c, 33)));
    }

    public static Bitmap loadAvatar(Conversation conv, int dp) {
        return FxUi.App.avatarService().get(conv, getPixel(dp), false);
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
        private int dp;

        public BitmapWorkerTask(ImageView imageView, int adp) {
            imageViewReference = new WeakReference<>(imageView);
            dp = adp;
        }

        @Override
        protected Bitmap doInBackground(Conversation... params) {
            return FxUi.App.avatarService().get(params[0], getPixel(dp), isCancelled());
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

    public static void sendMessage(EmojiconEditText emojiEditText, Conversation conversation, FxUi activity) {
        String body = emojiEditText.getText().toString().trim();
        if (body.length() == 0 || conversation == null) return;
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            if (conversation.getNextCounterpart() != null) {
                message.setCounterpart(conversation.getNextCounterpart());
                message.setType(Message.TYPE_PRIVATE);
            }
        }
        if (conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL) {
            if (!axolotlTrustKeys(ConversationActivity.REQUEST_TRUST_KEYS_TEXT, activity))
                sendMessage(message, activity);
        } else sendMessage(message, activity);
        emojiEditText.setText("");
        emojiEditText.requestFocus();
    }

    public static void sendMessage(Message message, FxUi activity) {
        activity.xmppConnectionService.sendMessage(message);
    }

    public static boolean axolotlTrustKeys(int requestCode, FxUi activity) {
        return axolotlTrustKeys(requestCode, ConversationActivity.ATTACHMENT_CHOICE_INVALID, activity);
    }

    public static boolean axolotlTrustKeys(int requestCode, int attachmentChoice, FxUi activity) {
        if (FxUi.App.dConversation == null) return false;
        AxolotlService service = FxUi.App.dConversation.getAccount().getAxolotlService();
        Contact contact = FxUi.App.dConversation.getContact();
        boolean hasUndecidedOwn = !service.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED).isEmpty();
        boolean hasUndecidedContact = !service.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED, contact.getJid()).isEmpty();
        boolean hasPendingKeys = !service.findDevicesWithoutSession(FxUi.App.dConversation).isEmpty();
        boolean hasNoTrustedKeys = service.getNumTrustedKeys(FxUi.App.dConversation.getContact().getJid()) == 0;
        if (hasUndecidedOwn || hasUndecidedContact || hasPendingKeys || hasNoTrustedKeys) {
            service.createSessionsIfNeeded(FxUi.App.dConversation);
            Intent intent = new Intent(activity, TrustKeysActivity.class);
            intent.putExtra("contact", FxUi.App.dConversation.getContact().getJid().toBareJid().toString());
            intent.putExtra(ConversationActivity.EXTRA_ACCOUNT, FxUi.App.dConversation.getAccount().getJid().toBareJid().toString());
            intent.putExtra("choice", attachmentChoice);
            intent.putExtra("has_no_trusted", hasNoTrustedKeys);
            activity.startActivityForResult(intent, requestCode);
            return true;
        } else return false;
    }

    public static EmojiconsPopup initEmojiKeyboard(View root, FxUi app, final EmojiconEditText mEditMessage) {
        final EmojiconsPopup mEmojiKeyboard = new EmojiconsPopup(root, app);
        mEmojiKeyboard.setSizeForSoftKeyboard();

        mEmojiKeyboard.setOnSoftKeyboardOpenCloseListener(new EmojiconsPopup.OnSoftKeyboardOpenCloseListener() {
            @Override
            public void onKeyboardOpen(int keyBoardHeight) {
            }

            @Override
            public void onKeyboardClose() {
                if (mEmojiKeyboard.isShowing()) mEmojiKeyboard.dismiss();
            }
        });

        mEmojiKeyboard.setOnEmojiconClickedListener(new EmojiconGridView.OnEmojiconClickedListener() {
            @Override
            public void onEmojiconClicked(Emojicon emojicon) {
                if (mEditMessage == null || emojicon == null) {
                    return;
                }

                int start = mEditMessage.getSelectionStart();
                int end = mEditMessage.getSelectionEnd();

                if (start < 0) {
                    mEditMessage.append(emojicon.getEmoji());
                } else {
                    mEditMessage.getText().replace(Math.min(start, end),
                            Math.max(start, end), emojicon.getEmoji(), 0,
                            emojicon.getEmoji().length());
                }
            }
        });

        mEmojiKeyboard.setOnEmojiconBackspaceClickedListener(new EmojiconsPopup.OnEmojiconBackspaceClickedListener() {
            @Override
            public void onEmojiconBackspaceClicked(View v) {
                KeyEvent event = new KeyEvent(
                        0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL);
                mEditMessage.dispatchKeyEvent(event);
            }
        });

        return mEmojiKeyboard;
    }

    public static int getSendButtonImageResource(SendButtonAction action) {
        switch (action) {
            case TEXT:
                return R.drawable.ic_send_text_offline;
            case TAKE_PHOTO:
                return R.drawable.ic_send_photo_offline;
            case RECORD_VOICE:
                return R.drawable.ic_send_voice_offline;
            case SEND_LOCATION:
                return R.drawable.ic_send_location_offline;
            case CANCEL:
                return R.drawable.ic_send_cancel_offline;
            case CHOOSE_PICTURE:
                return R.drawable.ic_send_picture_offline;
        }
        return R.drawable.ic_send_text_offline;
    }

}
