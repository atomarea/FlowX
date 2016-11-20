package net.atomarea.flowx.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigPictureStyle;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import com.makeramen.roundedimageview.RoundedDrawable;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.ui.ConversationActivity;
import net.atomarea.flowx.ui.TimePreference;
import net.atomarea.flowx.utils.GeoHelper;
import net.atomarea.flowx.utils.UIHelper;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {

    public static final int NOTIFICATION_ID = 0x2342;
    public static final int FOREGROUND_NOTIFICATION_ID = 0x8899;
    public static final int ERROR_NOTIFICATION_ID = 0x5678;
    private static final String CONVERSATIONS_GROUP = "net.atomarea.flowx";
    private final XmppConnectionService mXmppConnectionService;
    private final LinkedHashMap<String, ArrayList<Message>> notifications = new LinkedHashMap<>();
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public NotificationService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private static Pattern generateNickHighlightPattern(final String nick) {
        return Pattern.compile("\\b" + Pattern.quote(nick) + "\\p{Punct}?\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public boolean notify(final Message message) {
        return (message.getStatus() == Message.STATUS_RECEIVED)
                && notificationsEnabled()
                && !message.getConversation().isMuted()
                && (message.getConversation().alwaysNotify() || wasHighlightedOrPrivate(message)
        );
    }

    public boolean notificationsEnabled() {
        return mXmppConnectionService.getPreferences().getBoolean("show_notification", true);
    }

    public boolean isQuietHours() {
        if (!mXmppConnectionService.getPreferences().getBoolean("enable_quiet_hours", false)) {
            return false;
        }
        final long startTime = mXmppConnectionService.getPreferences().getLong("quiet_hours_start", TimePreference.DEFAULT_VALUE) % Config.MILLISECONDS_IN_DAY;
        final long endTime = mXmppConnectionService.getPreferences().getLong("quiet_hours_end", TimePreference.DEFAULT_VALUE) % Config.MILLISECONDS_IN_DAY;
        final long nowTime = Calendar.getInstance().getTimeInMillis() % Config.MILLISECONDS_IN_DAY;

        if (endTime < startTime) {
            return nowTime > startTime || nowTime < endTime;
        } else {
            return nowTime > startTime && nowTime < endTime;
        }
    }

    public void pushFromBacklog(final Message message) {
        if (notify(message)) {
            synchronized (notifications) {
                pushToStack(message);
            }
        }
    }

    public void pushFromDirectReply(final Message message) {
        synchronized (notifications) {
            pushToStack(message);
            updateNotification(false);
        }
    }

    public void finishBacklog(boolean notify) {
        synchronized (notifications) {
            mXmppConnectionService.updateUnreadCountBadge();
            updateNotification(notify);
        }
    }

    private void pushToStack(final Message message) {
        final String conversationUuid = message.getConversationUuid();
        if (notifications.containsKey(conversationUuid)) {
            notifications.get(conversationUuid).add(message);
        } else {
            final ArrayList<Message> mList = new ArrayList<>();
            mList.add(message);
            notifications.put(conversationUuid, mList);
        }
    }

    public void push(final Message message) {
        mXmppConnectionService.updateUnreadCountBadge();
        if (!notify(message)) {
            Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().toBareJid() + ": suppressing notification because turned off");
            return;
        }
        final boolean isScreenOn = mXmppConnectionService.isInteractive();
        if (this.mIsInForeground && isScreenOn && this.mOpenConversation == message.getConversation()) {
            Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().toBareJid() + ": suppressing notification because conversation is open");
            mXmppConnectionService.vibrate();
            return;
        }
        if (this.mIsInForeground && isScreenOn) {
            mXmppConnectionService.vibrate();
            return;
        }
        synchronized (notifications) {
            pushToStack(message);
            final Account account = message.getConversation().getAccount();
            final boolean doNotify = (!(this.mIsInForeground && this.mOpenConversation == null) || !isScreenOn)
                    && !account.inGracePeriod()
                    && !this.inMiniGracePeriod(account);
            updateNotification(doNotify);
        }
    }

    public void clear() {
        synchronized (notifications) {
            for (ArrayList<Message> messages : notifications.values()) {
                markAsReadIfHasDirectReply(messages);
            }
            notifications.clear();
            updateNotification(false);
        }
    }

    public void clear(final Conversation conversation) {
        synchronized (notifications) {
            markAsReadIfHasDirectReply(conversation);
            notifications.remove(conversation.getUuid());
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            notificationManager.cancel(conversation.getUuid(), NOTIFICATION_ID);
            updateNotification(false);
        }
    }

    private void markAsReadIfHasDirectReply(final Conversation conversation) {
        markAsReadIfHasDirectReply(notifications.get(conversation.getUuid()));
    }

    private void markAsReadIfHasDirectReply(final ArrayList<Message> messages) {
        if (messages != null && messages.size() > 0) {
            Message last = messages.get(messages.size() - 1);
            if (last.getStatus() != Message.STATUS_RECEIVED) {
                mXmppConnectionService.markRead(last.getConversation(), false);
            }
        }
    }

    private void setNotificationColor(final Builder mBuilder) {
        mBuilder.setColor(mXmppConnectionService.getResources().getColor(R.color.primary));
    }

    public void updateNotification(final boolean notify) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        final SharedPreferences preferences = mXmppConnectionService.getPreferences();

        if (notifications.size() == 0) {
            notificationManager.cancel(NOTIFICATION_ID);
        } else {
            if (notify) {
                this.markLastNotification();
            }
            final Builder mBuilder;
            if (notifications.size() == 1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.d(Config.LOGTAG, "Notification: Received 1 single notification and using device < Android N");
                mBuilder = buildSingleConversations(notifications.values().iterator().next());
                modifyForSoundVibrationAndLight(mBuilder, notify, preferences);
                notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
            } else {
                Log.d(Config.LOGTAG, "Notification: Received multiple notification or using Android N");
                mBuilder = buildMultipleConversation();
                modifyForSoundVibrationAndLight(mBuilder, notify, preferences);
                notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
                for (Map.Entry<String, ArrayList<Message>> entry : notifications.entrySet()) {
                    Builder singleBuilder = buildSingleConversations(entry.getValue());
                    singleBuilder.setGroup(CONVERSATIONS_GROUP);
                    modifyForSoundVibrationAndLight(singleBuilder, notify, preferences);
                    notificationManager.notify(entry.getKey(), NOTIFICATION_ID, singleBuilder.build());
                }
            }
        }
    }

    private void modifyForSoundVibrationAndLight(Builder mBuilder, boolean notify, SharedPreferences preferences) {
        final String ringtone = preferences.getString("notification_ringtone", null);
        final boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
        final boolean led = preferences.getBoolean("led", true);
        if (notify && !isQuietHours()) {
            if (vibrate) {
                final int dat = 70;
                final long[] pattern = {0, 3 * dat, dat, dat};
                mBuilder.setVibrate(pattern);
            }
            if (ringtone != null) {
                mBuilder.setSound(Uri.parse(ringtone));
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setCategory(Notification.CATEGORY_MESSAGE);
        }
        setNotificationColor(mBuilder);
        mBuilder.setDefaults(0);
        if (led) {
            mBuilder.setLights(0x0087FF, 2000, 4000);
        }
    }

    private Builder buildMultipleConversation() {
        final Builder mBuilder = new NotificationCompat.Builder(
                mXmppConnectionService);
        final NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(notifications.size()
                + " "
                + mXmppConnectionService
                .getString(R.string.unread_conversations));
        final StringBuilder names = new StringBuilder();
        Conversation conversation = null;
        for (final ArrayList<Message> messages : notifications.values()) {
            if (messages.size() > 0) {
                conversation = messages.get(0).getConversation();
                final String name = conversation.getName();
                if (Config.HIDE_MESSAGE_TEXT_IN_NOTIFICATION) {
                    int count = messages.size();
                    style.addLine(Html.fromHtml("<b>" + name + "</b>: " + mXmppConnectionService.getResources().getQuantityString(R.plurals.x_messages, count, count)));
                } else {
                    style.addLine(Html.fromHtml("<b>" + name + "</b>: "
                            + UIHelper.getMessagePreview(mXmppConnectionService, messages.get(0)).first));
                }
                names.append(name);
                names.append(", ");
            }
        }
        if (names.length() >= 2) {
            names.delete(names.length() - 2, names.length());
        }
        mBuilder.setContentTitle(notifications.size()
                + " "
                + mXmppConnectionService
                .getString(R.string.unread_conversations));
        mBuilder.setContentText(names.toString());
        mBuilder.setStyle(style);
        if (conversation != null) {
            mBuilder.setContentIntent(createContentIntent(conversation));
        }
        mBuilder.setGroupSummary(true);
        mBuilder.setGroup(CONVERSATIONS_GROUP);
        mBuilder.setDeleteIntent(createDeleteIntent(null));
        mBuilder.setSmallIcon(R.drawable.ic_notification);
        return mBuilder;
    }

    private Builder buildSingleConversations(final ArrayList<Message> messages) {
        final Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        if (messages.size() >= 1) {
            final Conversation conversation = messages.get(0).getConversation();
            Bitmap temp = mXmppConnectionService.getAvatarService().get(conversation, getPixel(150));
            RoundedDrawable roundedTemp = new RoundedDrawable(temp);
            roundedTemp.setScaleType(ImageView.ScaleType.CENTER_CROP);
            roundedTemp.setCornerRadius((float) 350);
            mBuilder.setLargeIcon(roundedTemp.toBitmap());
            mBuilder.setContentTitle(conversation.getName());
            if (Config.HIDE_MESSAGE_TEXT_IN_NOTIFICATION) {
                int count = messages.size();
                mBuilder.setContentText(mXmppConnectionService.getResources().getQuantityString(R.plurals.x_messages, count, count));
            } else {
                Message message;
                if ((message = getImage(messages)) != null) {
                    modifyForImage(mBuilder, message, messages);
                } else {
                    modifyForTextOnly(mBuilder, messages);
                }
                RemoteInput remoteInput = new RemoteInput.Builder("text_reply").setLabel(UIHelper.getMessageHint(mXmppConnectionService, conversation)).build();
                NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(R.drawable.ic_send_text_offline, "Antworten", createReplyIntent(conversation, false)).addRemoteInput(remoteInput).build();
                NotificationCompat.Action wearReplyAction = new NotificationCompat.Action.Builder(R.drawable.ic_send_text_offline, "Antworten", createReplyIntent(conversation, true)).addRemoteInput(remoteInput).build();
                mBuilder.extend(new NotificationCompat.WearableExtender().addAction(wearReplyAction));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mBuilder.addAction(replyAction);
                }
                if ((message = getFirstDownloadableMessage(messages)) != null) {
                    mBuilder.addAction(
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                                    R.drawable.ic_file_download_white_24dp : R.drawable.ic_action_download,
                            mXmppConnectionService.getResources().getString(R.string.download_x_file,
                                    UIHelper.getFileDescriptionString(mXmppConnectionService, message)),
                            createDownloadIntent(message)
                    );
                }
                if ((message = getFirstLocationMessage(messages)) != null) {
                    mBuilder.addAction(R.drawable.ic_room_white_24dp,
                            mXmppConnectionService.getString(R.string.show_location),
                            createShowLocationIntent(message));
                }
            }
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                Contact contact = conversation.getContact();
                Uri systemAccount = contact.getSystemAccount();
                if (systemAccount != null) {
                    mBuilder.addPerson(systemAccount.toString());
                }
            }
            mBuilder.setWhen(conversation.getLatestMessage().getTimeSent());
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setDeleteIntent(createDeleteIntent(conversation));
            mBuilder.setContentIntent(createContentIntent(conversation));
        }
        return mBuilder;
    }

    private void modifyForImage(final Builder builder, final Message message,
                                final ArrayList<Message> messages) {
        try {
            final Bitmap bitmap = mXmppConnectionService.getFileBackend()
                    .getThumbnail(message, getPixel(288), false);
            final ArrayList<Message> tmp = new ArrayList<>();
            for (final Message msg : messages) {
                if (msg.getType() == Message.TYPE_TEXT
                        && msg.getTransferable() == null) {
                    tmp.add(msg);
                }
            }
            final BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();
            bigPictureStyle.bigPicture(bitmap);
            if (tmp.size() > 0) {
                CharSequence text = getMergedBodies(tmp);
                bigPictureStyle.setSummaryText(text);
                builder.setContentText(text);
            } else {
                builder.setContentText(mXmppConnectionService.getString(
                        R.string.received_x_file,
                        UIHelper.getFileDescriptionString(mXmppConnectionService, message)));
            }
            builder.setStyle(bigPictureStyle);
        } catch (final FileNotFoundException e) {
            modifyForTextOnly(builder, messages);
        }
    }

    private void modifyForTextOnly(final Builder builder, final ArrayList<Message> messages) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(mXmppConnectionService.getString(R.string.me));
            Conversation conversation = messages.get(0).getConversation();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                messagingStyle.setConversationTitle(conversation.getName());
            }
            for (Message message : messages) {
                String sender = message.getStatus() == Message.STATUS_RECEIVED ? UIHelper.getMessageDisplayName(message) : null;
                messagingStyle.addMessage(UIHelper.getMessagePreview(mXmppConnectionService, message).first, message.getTimeSent(), sender);
            }
            builder.setStyle(messagingStyle);
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(getMergedBodies(messages)));
            builder.setContentText(UIHelper.getMessagePreview(mXmppConnectionService, messages.get((messages.size()-1))).first);
        }
    }

    private Message getImage(final Iterable<Message> messages) {
        Message image = null;
        for (final Message message : messages) {
            if (message.getStatus() != Message.STATUS_RECEIVED) {
                return null;
            }
            if (message.getType() != Message.TYPE_TEXT
                    && message.getTransferable() == null
                    && message.getEncryption() != Message.ENCRYPTION_PGP
                    && message.getFileParams().height > 0) {
                image = message;
            }
        }
        return image;
    }

    private Message getFirstDownloadableMessage(final Iterable<Message> messages) {
        for (final Message message : messages) {
            if (message.getTransferable() != null
                    && (message.getType() == Message.TYPE_FILE
                    || message.getType() == Message.TYPE_IMAGE
                    || message.treatAsDownloadable() != Message.Decision.NEVER)) {
                return message;
            }
        }
        return null;
    }

    private Message getFirstLocationMessage(final Iterable<Message> messages) {
        for (final Message message : messages) {
            if (GeoHelper.isGeoUri(message.getBody())) {
                return message;
            }
        }
        return null;
    }

    private CharSequence getMergedBodies(final ArrayList<Message> messages) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < messages.size(); ++i) {
            text.append(UIHelper.getMessagePreview(mXmppConnectionService, messages.get(i)).first);
            if (i != messages.size() - 1) {
                text.append("\n");
            }
        }
        return text.toString();
    }

    private PendingIntent createShowLocationIntent(final Message message) {
        Iterable<Intent> intents = GeoHelper.createGeoIntentsFromMessage(message);
        for (Intent intent : intents) {
            if (intent.resolveActivity(mXmppConnectionService.getPackageManager()) != null) {
                return PendingIntent.getActivity(mXmppConnectionService, 18, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }
        return createOpenConversationsIntent();
    }

    private PendingIntent createContentIntent(final String conversationUuid, final String downloadMessageUuid) {
        final Intent viewConversationIntent = new Intent(mXmppConnectionService, ConversationActivity.class);
        viewConversationIntent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);
        viewConversationIntent.putExtra(ConversationActivity.CONVERSATION, conversationUuid);
        if (downloadMessageUuid != null) {
            viewConversationIntent.putExtra(ConversationActivity.EXTRA_DOWNLOAD_UUID, downloadMessageUuid);
            return PendingIntent.getActivity(mXmppConnectionService,
                    conversationUuid.hashCode() % 389782,
                    viewConversationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getActivity(mXmppConnectionService,
                    conversationUuid.hashCode() % 936236,
                    viewConversationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private PendingIntent createDownloadIntent(final Message message) {
        return createContentIntent(message.getConversationUuid(), message.getUuid());
    }

    private PendingIntent createContentIntent(final Conversation conversation) {
        return createContentIntent(conversation.getUuid(), null);
    }

    private PendingIntent createDeleteIntent(Conversation conversation) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_CLEAR_NOTIFICATION);
        if (conversation != null) {
            intent.putExtra("uuid", conversation.getUuid());
            return PendingIntent.getService(mXmppConnectionService, conversation.getUuid().hashCode() % 247527, intent, 0);
        }
        return PendingIntent.getService(mXmppConnectionService, 0, intent, 0);
    }

    private PendingIntent createReplyIntent(Conversation conversation, boolean dismissAfterReply) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_REPLY_TO_CONVERSATION);
        intent.putExtra("uuid", conversation.getUuid());
        intent.putExtra("dismiss_notification",dismissAfterReply);
        int id =  conversation.getUuid().hashCode() % (dismissAfterReply ? 402359 : 426583);
        return PendingIntent.getService(mXmppConnectionService, id, intent, 0);
    }

    private PendingIntent createTryAgainIntent() {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_TRY_AGAIN);
        return PendingIntent.getService(mXmppConnectionService, 45, intent, 0);
    }

    private boolean wasHighlightedOrPrivate(final Message message) {
        final String nick = message.getConversation().getMucOptions().getActualNick();
        final Pattern highlight = generateNickHighlightPattern(nick);
        if (message.getBody() == null || nick == null) {
            return false;
        }
        final Matcher m = highlight.matcher(message.getBody());
        return (m.find() || message.getType() == Message.TYPE_PRIVATE);
    }

    public void setOpenConversation(final Conversation conversation) {
        this.mOpenConversation = conversation;
    }

    public void setIsInForeground(final boolean foreground) {
        this.mIsInForeground = foreground;
    }

    private int getPixel(final int dp) {
        final DisplayMetrics metrics = mXmppConnectionService.getResources()
                .getDisplayMetrics();
        return ((int) (dp * metrics.density));
    }

    private void markLastNotification() {
        this.mLastNotification = SystemClock.elapsedRealtime();
    }

    private boolean inMiniGracePeriod(final Account account) {
        final int miniGrace = account.getStatus() == Account.State.ONLINE ? Config.MINI_GRACE_PERIOD
                : Config.MINI_GRACE_PERIOD * 2;
        return SystemClock.elapsedRealtime() < (this.mLastNotification + miniGrace);
    }

    private PendingIntent createOpenConversationsIntent() {
        return PendingIntent.getActivity(mXmppConnectionService, 0, new Intent(mXmppConnectionService, ConversationActivity.class), 0);
    }

    public void updateErrorNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        final List<Account> errors = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus()) {
                errors.add(account);
            }
        }
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        if (errors.size() == 0) {
            notificationManager.cancel(ERROR_NOTIFICATION_ID);
            return;
        } else if (errors.size() == 1) {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
            mBuilder.setContentText(errors.get(0).getJid().toBareJid().toString());
        } else {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_accounts));
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_fix));
        }
        mBuilder.addAction(R.drawable.ic_autorenew_white_24dp,
                mXmppConnectionService.getString(R.string.try_again),
                createTryAgainIntent());
        if (errors.size() == 1) {
        }
        mBuilder.setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_warning_white_24dp);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning);
        }
        final TaskStackBuilder stackBuilder = TaskStackBuilder.create(mXmppConnectionService);
        stackBuilder.addParentStack(ConversationActivity.class);

        final PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);
        notificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }
}