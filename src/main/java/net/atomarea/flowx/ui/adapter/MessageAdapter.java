package net.atomarea.flowx.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.beardedhen.androidbootstrap.BootstrapProgressBar;
import com.beardedhen.androidbootstrap.api.defaults.DefaultBootstrapBrand;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.XmppAxolotlSession;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.DownloadableFile;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Message.FileParams;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.ui.ConversationActivity;
import net.atomarea.flowx.ui.ShowFullscreenMessageActivity;
import net.atomarea.flowx.ui.widget.ClickableMovementMethod;
import net.atomarea.flowx.ui.widget.ListSelectionManager;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.GeoHelper;
import net.atomarea.flowx.utils.UIHelper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import nl.changer.audiowife.AudioWife;

public class MessageAdapter extends ArrayAdapter<Message> {

    private static final int SENT = 0;
    private static final int RECEIVED = 1;
    private static final int STATUS = 2;
    private static final Pattern XMPP_PATTERN = Pattern
            .compile("xmpp\\:(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+");

    private ConversationActivity activity;

    private DisplayMetrics metrics;

    private OnLongClickListener openContextMenu = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            v.showContextMenu();
            return true;
        }
    };
    private boolean mIndicateReceived = false;
    private HashMap<Integer, AudioWife> audioPlayer;
    private final ListSelectionManager listSelectionManager = new ListSelectionManager();

    public MessageAdapter(ConversationActivity a, List<Message> messages) {
        super(a, 0, messages);
        this.activity = a;
        metrics = getContext().getResources().getDisplayMetrics();
        updatePreferences();
    }

    public static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || message != oldMessage) bitmapWorkerTask.cancel(true);
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

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    public int getItemViewType(Message message) {
        if (message.getType() == Message.TYPE_STATUS) return STATUS;
        else if (message.getStatus() <= Message.STATUS_RECEIVED) return RECEIVED;
        return SENT;
    }

    @Override
    public int getItemViewType(int position) {
        return getItemViewType(getItem(position));
    }

    private int getMessageTextColor(boolean onDark, boolean primary) {
        return (onDark ? ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54) : ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70));
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        String filesize = null;
        String info = null;
        boolean error = false;
        if (viewHolder.indicatorReceived != null) {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
            viewHolder.indicatorRead.setVisibility(View.GONE);
        }
        if (viewHolder.edit_indicator != null) {
            if (message.edited()) {
                viewHolder.edit_indicator.setVisibility(View.VISIBLE);
                viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_white_18dp);
                viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
            } else {
                viewHolder.edit_indicator.setVisibility(View.GONE);
            }
        }
        boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE || message.getTransferable() != null) {
            FileParams params = message.getFileParams();
            if (params.size > (1.5 * 1024 * 1024)) filesize = params.size / (1024 * 1024) + " MB";
            else if (params.size > 0) filesize = params.size / 1024 + " KB";
            if (message.getTransferable() != null && message.getTransferable().getStatus() == Transferable.STATUS_FAILED)
                error = true;
        }
        switch (message.getMergedStatus()) {
            case Message.STATUS_WAITING:
                info = getContext().getString(R.string.waiting);
                break;
            case Message.STATUS_UNSEND:
                Transferable d = message.getTransferable();
                if (d != null)
                    info = getContext().getString(R.string.sending_file, d.getProgress());
                else info = getContext().getString(R.string.sending);
                break;
            case Message.STATUS_OFFERED:
                info = getContext().getString(R.string.offering);
                break;
            case Message.STATUS_SEND_RECEIVED:
                if (mIndicateReceived && viewHolder.indicatorReceived != null)
                    viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                break;
            case Message.STATUS_SEND_DISPLAYED:
                if (mIndicateReceived) {
                    if (viewHolder.indicatorReceived != null)
                        viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                    if (viewHolder.indicatorRead != null)
                        viewHolder.indicatorRead.setVisibility(View.VISIBLE);
                }
                break;
            case Message.STATUS_SEND_FAILED:
                info = getContext().getString(R.string.send_failed);
                error = true;
                break;
            default:
                if (multiReceived) info = UIHelper.getMessageDisplayName(message);
                break;
        }
        if (error && type == SENT) viewHolder.time.setTextColor(activity.getWarningTextColor());
        else viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
        if (message.getEncryption() == Message.ENCRYPTION_NONE)
            viewHolder.indicator.setVisibility(View.GONE);
        else {
            viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_secure_indicator : R.drawable.ic_secure_indicator_white);
            viewHolder.indicator.setVisibility(View.VISIBLE);
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                XmppAxolotlSession.Trust trust = message.getConversation().getAccount().getAxolotlService().getFingerprintTrust(message.getFingerprint());
                if (trust == null || (!trust.trusted() && !trust.trustedInactive())) {
                    viewHolder.indicator.setColorFilter(activity.getWarningTextColor());
                    viewHolder.indicator.setAlpha(1.0f);
                } else {
                    viewHolder.indicator.clearColorFilter();
                    if (darkBackground) viewHolder.indicator.setAlpha(0.7f);
                    else viewHolder.indicator.setAlpha(0.57f);
                }
            } else {
                viewHolder.indicator.clearColorFilter();
                if (darkBackground) viewHolder.indicator.setAlpha(0.7f);
                else viewHolder.indicator.setAlpha(0.57f);
            }
        }

        String formatedTime = UIHelper.readableTimeDifferenceFull(getContext(), message.getMergedTimeSent());
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if ((filesize != null) && (info != null))
                viewHolder.time.setText(formatedTime + " \u00B7 " + filesize + " \u00B7 " + info);
            else if ((filesize == null) && (info != null))
                viewHolder.time.setText(formatedTime + " \u00B7 " + info);
            else if ((filesize != null))
                viewHolder.time.setText(formatedTime + " \u00B7 " + filesize);
            else viewHolder.time.setText(formatedTime);
        } else {
            if ((filesize != null) && (info != null))
                viewHolder.time.setText(filesize + " \u00B7 " + info);
            else if ((filesize == null) && (info != null))
                if (error) viewHolder.time.setText(info + " \u00B7 " + formatedTime);
                else viewHolder.time.setText(info);
            else if ((filesize != null))
                viewHolder.time.setText(filesize + " \u00B7 " + formatedTime);
            else viewHolder.time.setText(formatedTime);
        }
    }

    private void displayInfoMessage(ViewHolder viewHolder, String text, boolean darkBackground) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(text);
        viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
        viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayProgressBar(ViewHolder viewHolder, int progress) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        if (viewHolder.pbFile == null) {
            viewHolder.pbFile = new BootstrapProgressBar(getContext());
            viewHolder.pbFile.setBootstrapBrand(DefaultBootstrapBrand.PRIMARY);
            ((ViewGroup) viewHolder.messageBody.getParent()).addView(viewHolder.pbFile);
        }
        viewHolder.pbFile.setProgress(progress);
        viewHolder.pbFile.setVisibility(View.VISIBLE);
    }

    private void displayDecryptionFailed(ViewHolder viewHolder, boolean darkBackground) {
        viewHolder.aw_player.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(getContext().getString(R.string.decryption_failed));
        viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayHeartMessage(final ViewHolder viewHolder, final String body) {
        viewHolder.aw_player.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.messageBody.setIncludeFontPadding(false);
        Spannable span = new SpannableString(body);
        span.setSpan(new RelativeSizeSpan(4.0f), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(activity.getWarningTextColor()), 0, body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(span);
    }

    private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground) {
        viewHolder.aw_player.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setIncludeFontPadding(true);
        if (message.getBody() != null) {
            final String nick = UIHelper.getMessageDisplayName(message);
            String body;
            try {
                body = message.getMergedBody().replaceAll("^" + Message.ME_COMMAND, nick + " ");
            } catch (ArrayIndexOutOfBoundsException e) {
                body = message.getMergedBody();
            }
            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = body.substring(0, Config.MAX_DISPLAY_MESSAGE_CHARS) + "\u2026";
            }

            Spannable formattedBody = new SpannableString(body);
            int i = body.indexOf(Message.MERGE_SEPARATOR);
            while (i >= 0) {
                final int end = i + Message.MERGE_SEPARATOR.length();
                formattedBody.setSpan(new RelativeSizeSpan(0.3f), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = body.indexOf(Message.MERGE_SEPARATOR, end);
            }
            if (message.getType() != Message.TYPE_PRIVATE) {
                if (message.hasMeCommand()) {
                    formattedBody.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                String privateMarker;
                if (message.getStatus() <= Message.STATUS_RECEIVED)
                    privateMarker = activity
                            .getString(R.string.private_message);
                else {
                    final String to;
                    if (message.getCounterpart() != null)
                        to = message.getCounterpart().getResourcepart();
                    else
                        to = "";
                    privateMarker = activity.getString(R.string.private_message_to, to);
                }
                formattedBody = new SpannableStringBuilder().append(privateMarker).append(' ').append(formattedBody);
                formattedBody.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarker
                        .length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                formattedBody.setSpan(new StyleSpan(Typeface.BOLD), 0,
                        privateMarker.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (message.hasMeCommand())
                    formattedBody.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarker.length() + 1,
                            privateMarker.length() + 1 + nick.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            Linkify.addLinks(formattedBody, Linkify.WEB_URLS);
            Linkify.addLinks(formattedBody, XMPP_PATTERN, "xmpp");
            Linkify.addLinks(formattedBody, GeoHelper.GEO_URI, "geo");
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(formattedBody);
            viewHolder.messageBody.setTextIsSelectable(true);
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
            listSelectionManager.onUpdate(viewHolder.messageBody, message);
        } else {
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
        }
        viewHolder.messageBody.setTextColor(this.getMessageTextColor(darkBackground, true));
        viewHolder.messageBody.setLinkTextColor(this.getMessageTextColor(darkBackground, true));
        viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground ? R.color.grey800 : R.color.grey500));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody.setOnLongClickListener(openContextMenu);
    }

    private void displayDownloadableMessage(ViewHolder viewHolder,
                                            final Message message, String text) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(text);
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                activity.startDownloadable(message);
            }
        });
        viewHolder.download_button.setOnLongClickListener(openContextMenu);
    }

    private void displayAudioMessage(ViewHolder viewHolder, final Message message, int position) {
        if (audioPlayer == null) audioPlayer = new HashMap<>();
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.aw_player.setVisibility(View.VISIBLE);
        Uri audioFile = Uri.fromFile(activity.xmppConnectionService.getFileBackend().getFile(message));

        AudioWife audioWife = audioPlayer.get(position);
        if (audioWife == null) {
            audioWife = new AudioWife();
            audioWife.init(getContext(), audioFile);
            audioPlayer.put(position, audioWife);
            RelativeLayout vg = new RelativeLayout(activity);
            LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            audioWife.useDefaultUi(vg, layoutInflater);
            viewHolder.aw_player.addView(audioWife.getPlayerUi());
        } else {
            audioWife.cleanPlayerUi();
            viewHolder.aw_player.addView(audioWife.getPlayerUi());
        }
    }

    private void displayOpenableMessage(ViewHolder viewHolder, final Message message) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                openDownloadable(message);
            }
        });
        viewHolder.download_button.setOnLongClickListener(openContextMenu);
    }

    private void displayLocationMessage(ViewHolder viewHolder, final Message message) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(R.string.show_location);
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showLocation(message);
            }
        });
        viewHolder.download_button.setOnLongClickListener(openContextMenu);
    }

    private void displayImageMessage(ViewHolder viewHolder, final Message message) {
        viewHolder.aw_player.setVisibility(View.GONE);
        if (viewHolder.download_button != null) viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.VISIBLE);
        if (viewHolder.pbFile != null) viewHolder.pbFile.setVisibility(View.GONE);
        FileParams params = message.getFileParams();
        double target = metrics.density * 200;
        int scaledW;
        int scaledH;
        if (Math.max(params.height, params.width) * metrics.density <= target) {
            scaledW = (int) (params.width * metrics.density);
            scaledH = (int) (params.height * metrics.density);
        } else if (Math.max(params.height, params.width) <= target) {
            scaledW = params.width;
            scaledH = params.height;
        } else if (params.width <= params.height) {
            scaledW = (int) (params.width / ((double) params.height / target));
            scaledH = (int) target;
        } else {
            scaledW = (int) target;
            scaledH = (int) (params.height / ((double) params.width / target));
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
        layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
        viewHolder.image.setLayoutParams(layoutParams);
        activity.loadBitmap(message, viewHolder.image);
        viewHolder.image.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openDownloadable(message);
            }
        });
        viewHolder.image.setOnLongClickListener(openContextMenu);
    }


    @Override
    public View getView(int position, View unused, ViewGroup parent) {
        final Message message = getItem(position);
        final boolean isInValidSession = message.isValidInSession();
        final int type = getItemViewType(position);
        ViewHolder viewHolder;
        View view;
        viewHolder = new ViewHolder();
        switch (type) {
            case SENT:
                view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                viewHolder.message_box = (LinearLayout) view.findViewById(R.id.message_box);
                viewHolder.aw_player = (ViewGroup) view.findViewById(R.id.aw_player);
                viewHolder.download_button = (BootstrapButton) view.findViewById(R.id.download_button);
                viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
                viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
                viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
                viewHolder.messageBody = (TextView) view.findViewById(R.id.message_body);
                viewHolder.time = (TextView) view.findViewById(R.id.message_time);
                viewHolder.indicatorReceived = (ImageView) view.findViewById(R.id.indicator_received);
                viewHolder.indicatorRead = (ImageView) view.findViewById(R.id.indicator_read);
                viewHolder.encryption = (TextView) view.findViewById(R.id.message_encryption);
                break;
            case RECEIVED:
                view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                viewHolder.message_box = (LinearLayout) view.findViewById(R.id.message_box);
                viewHolder.aw_player = (ViewGroup) view.findViewById(R.id.aw_player);
                viewHolder.download_button = (BootstrapButton) view.findViewById(R.id.download_button);
                viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
                viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
                viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
                viewHolder.messageBody = (TextView) view.findViewById(R.id.message_body);
                viewHolder.time = (TextView) view.findViewById(R.id.message_time);
                viewHolder.indicatorReceived = (ImageView) view.findViewById(R.id.indicator_received);
                viewHolder.encryption = (TextView) view.findViewById(R.id.message_encryption);
                break;
            case STATUS:
                view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                viewHolder.status_message = (TextView) view.findViewById(R.id.status_message);
                viewHolder.load_more_messages = (Button) view.findViewById(R.id.load_more_messages);
                break;
            default:
                view = new View(getContext());
                viewHolder = null;
                break;
        }
        if (viewHolder.messageBody != null) listSelectionManager.onCreate(viewHolder.messageBody);
        view.setTag(viewHolder);

        if (viewHolder == null) return view;

        boolean darkBackground = (type == RECEIVED);

        if (type == STATUS) {
            if ("LOAD_MORE".equals(message.getBody())) {
                viewHolder.status_message.setVisibility(View.GONE);
                viewHolder.load_more_messages.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    }
                });
            } else {
                viewHolder.status_message.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setVisibility(View.GONE);
                viewHolder.status_message.setText(message.getBody());
            }
            return view;
        }

        final Transferable transferable = message.getTransferable();
        String mimeType = message.getMimeType();
        if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
            if (transferable.getStatus() == Transferable.STATUS_OFFER) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
            } else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
            } else {
                String msg = UIHelper.getMessagePreview(activity, message).first;
                if (msg.startsWith("prg")) {
                    int progress = Integer.parseInt(msg.split(":")[1]);
                    Log.i("TEST!", "" + progress);
                    displayProgressBar(viewHolder, progress);
                } else displayInfoMessage(viewHolder, msg, darkBackground);
            }
        } else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayImageMessage(viewHolder, message);
        } else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            if (message.getFileParams().width > 0) {
                displayImageMessage(viewHolder, message);
            } else {
                if (mimeType != null) {
                    if (message.getMimeType().startsWith("audio/")) {
                        displayAudioMessage(viewHolder, message, position);
                    } else displayOpenableMessage(viewHolder, message);
                } else displayOpenableMessage(viewHolder, message);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp())
                displayInfoMessage(viewHolder, activity.getString(R.string.encrypted_message), darkBackground);
            else {
                displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain), darkBackground);
                viewHolder.message_box.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.showInstallPgpDialog();
                    }
                });
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED)
            displayDecryptionFailed(viewHolder, darkBackground);
        else {
            if (GeoHelper.isGeoUri(message.getBody())) displayLocationMessage(viewHolder, message);
            else if (message.bodyIsHeart())
                displayHeartMessage(viewHolder, message.getBody().trim());
            else if (message.treatAsDownloadable() == Message.Decision.MUST || message.treatAsDownloadable() == Message.Decision.SHOULD)
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
            else displayTextMessage(viewHolder, message, darkBackground);
        }

        if (type == RECEIVED) {
            if (isInValidSession) {
                viewHolder.message_box.setBackgroundResource(R.drawable.msg_bbl_recv);
                viewHolder.encryption.setVisibility(View.GONE);
            } else {
                viewHolder.message_box.setBackgroundResource(R.drawable.msg_bbl_warn);
                viewHolder.encryption.setVisibility(View.VISIBLE);
                viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
            }
        }

        displayStatus(viewHolder, message, type, darkBackground);

        return view;
    }
    @Override
    public void notifyDataSetChanged() {
        listSelectionManager.onBeforeNotifyDataSetChanged();
        super.notifyDataSetChanged();
        listSelectionManager.onAfterNotifyDataSetChanged();
    }
    public void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = file.getMimeType();
        if (mime.startsWith("image/")) {
            Intent intent = new Intent(getContext(), ShowFullscreenMessageActivity.class);
            intent.putExtra("image", Uri.fromFile(file));
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        } else if (mime.startsWith("video/")) {
            Intent intent = new Intent(getContext(), ShowFullscreenMessageActivity.class);
            intent.putExtra("video", Uri.fromFile(file));
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        if (mime == null) {
            mime = "*/*";
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                uri = FileProvider.getUriForFile(activity, "net.atomarea.flowx.files", file);
            } catch (IllegalArgumentException e) {
                Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                return;
            }
            openIntent.setDataAndType(uri, mime);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        openIntent.setDataAndType(uri, mime);
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(Uri.fromFile(file), "*/*");
        }
        try {
            getContext().startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    public void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(activity, R.string.no_application_found_to_display_location, Toast.LENGTH_SHORT).show();
    }

    public void updatePreferences() {
        this.mIndicateReceived = activity.indicateReceived();
    }
    public TextView getMessageBody(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            return viewHolder.messageBody;
        }
        return null;
    }
    private static class ViewHolder {

        public Button load_more_messages;
        public ImageView edit_indicator;
        public BootstrapProgressBar pbFile;
        protected LinearLayout message_box;
        protected BootstrapButton download_button;
        protected ViewGroup aw_player;
        protected TextView encryption;
        protected ImageView image;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected ImageView indicatorRead;
        protected TextView messageBody;
        protected TextView status_message;
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

    class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            return activity.avatarService().get(params[0], activity.getPixel(48), isCancelled());
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
}
