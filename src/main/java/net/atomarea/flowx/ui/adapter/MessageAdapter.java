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
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.FingerprintStatus;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.DownloadableFile;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Message.FileParams;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.ui.ConversationActivity;
import net.atomarea.flowx.ui.ShowFullscreenMessageActivity;
import net.atomarea.flowx.ui.text.DividerSpan;
import net.atomarea.flowx.ui.text.QuoteSpan;
import net.atomarea.flowx.ui.widget.ClickableMovementMethod;
import net.atomarea.flowx.ui.widget.CopyTextView;
import net.atomarea.flowx.ui.widget.ListSelectionManager;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.GeoHelper;
import net.atomarea.flowx.utils.UIHelper;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import nl.changer.audiowife.AudioWife;

public class MessageAdapter extends ArrayAdapter<Message> implements CopyTextView.CopyHandler {
    private AudioWife audioWife;

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

    private boolean mIndicateReceived = false;
    private HashMap<Integer, AudioWife> audioPlayer;
    private OnQuoteListener onQuoteListener;
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

    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
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

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground, boolean inValidSession) {

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
        boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE || message.getTransferable() != null) {
            FileParams params = message.getFileParams();
            if (params.size > (1 * 1024 * 1024)) {
                filesize = params.size / (1024 * 1024) + " MiB";
            } else if (params.size > (1 * 1024)) {
                filesize = params.size / 1024 + " KiB";
            } else if (params.size > 0) {
                filesize = "1 KiB";
            }
            if (message.getTransferable() != null && message.getTransferable().getStatus() == Transferable.STATUS_FAILED) {
                error = true;
            }
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
        if (error && type == SENT) {
            viewHolder.time.setTextColor(activity.getWarningTextColor());
        } else {
            viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
        }
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicator.setVisibility(View.GONE);
        } else {
            viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_secure_indicator : R.drawable.ic_secure_indicator_white);
            viewHolder.indicator.setVisibility(View.VISIBLE);
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                FingerprintStatus status = message.getConversation()
                        .getAccount().getAxolotlService().getFingerprintTrust(
                                message.getFingerprint());

                if (status == null || (!status.isVerified() && inValidSession)) {
                    viewHolder.indicator.setAlpha(0.57f);
                } else {
                    viewHolder.indicator.clearColorFilter();
                    if (darkBackground) {
                        viewHolder.indicator.setAlpha(0.7f);
                    } else {
                        viewHolder.indicator.setAlpha(0.57f);
                    }
                }
            } else {
                viewHolder.indicator.clearColorFilter();
                if (darkBackground) {
                    viewHolder.indicator.setAlpha(0.7f);
                } else {
                    viewHolder.indicator.setAlpha(0.57f);
                }
            }
        }

        String formatedTime = UIHelper.readableTimeDifferenceFull(getContext(),
                message.getMergedTimeSent());
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(formatedTime + " \u00B7 " + filesize + " \u00B7 " + info);
            } else if ((filesize == null) && (info != null)) {
                viewHolder.time.setText(formatedTime + " \u00B7 " + info);
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(formatedTime + " \u00B7 " + filesize);
            } else {
                viewHolder.time.setText(formatedTime);
            }
        } else {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + info);
            } else if ((filesize == null) && (info != null)) {
                if (error) {
                    viewHolder.time.setText(info + " \u00B7 " + formatedTime);
                } else {
                    viewHolder.time.setText(info);
                }
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + formatedTime);
            } else {
                viewHolder.time.setText(formatedTime);
            }
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

    private int applyQuoteSpan(SpannableStringBuilder body, int start, int end, boolean darkBackground) {
        if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            end++;
        }
        if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int color = darkBackground ? this.getMessageTextColor(darkBackground, false)
                : getContext().getResources().getColor(R.color.bubble);
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        body.setSpan(new QuoteSpan(color, metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return 0;
    }

    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters.
     * Appends likebreaks and applies DividerSpan to them to show a padding between quote and text.
     */
    private boolean handleTextQuotes(SpannableStringBuilder body, boolean darkBackground) {
        boolean startsWithQuote = false;
        char previous = '\n';
        int lineStart = -1;
        int lineTextStart = -1;
        int quoteStart = -1;
        for (int i = 0; i <= body.length(); i++) {
            char current = body.length() > i ? body.charAt(i) : '\n';
            if (lineStart == -1) {
                if (previous == '\n') {
                    if (current == '>' || current == '\u00bb') {
                        // Line start with quote
                        lineStart = i;
                        if (quoteStart == -1) quoteStart = i;
                        if (i == 0) startsWithQuote = true;
                    } else if (quoteStart >= 0) {
                        // Line start without quote, apply spans there
                        applyQuoteSpan(body, quoteStart, i - 1, darkBackground);
                        quoteStart = -1;
                    }
                }
            } else {
                // Remove extra spaces between > and first character in the line
                // > character will be removed too
                if (current != ' ' && lineTextStart == -1) {
                    lineTextStart = i;
                }
                if (current == '\n') {
                    body.delete(lineStart, lineTextStart);
                    i -= lineTextStart - lineStart;
                    if (i == lineStart) {
                        // Avoid empty lines because span over empty line can be hidden
                        body.insert(i++, " ");
                    }
                    lineStart = -1;
                    lineTextStart = -1;
                }
            }
            previous = current;
        }
        if (quoteStart >= 0) {
            // Apply spans to finishing open quote
            applyQuoteSpan(body, quoteStart, body.length(), darkBackground);
        }
        return startsWithQuote;
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
        span.setSpan(new ForegroundColorSpan(activity.getWarningTextColor()), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(span);
    }

    private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground) {
        if (viewHolder.download_button != null) {
            viewHolder.download_button.setVisibility(View.GONE);
        }
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setIncludeFontPadding(true);
        if (message.getBody() != null) {
            final String nick = UIHelper.getMessageDisplayName(message);
            SpannableStringBuilder body = message.getMergedBody();
            boolean hasMeCommand = message.hasMeCommand();
            if (hasMeCommand) {
                body = body.replace(0, Message.ME_COMMAND.length(), nick + " ");
            }
            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
                body.append("\u2026");
            }
            Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
            for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
                int start = body.getSpanStart(mergeSeparator);
                int end = body.getSpanEnd(mergeSeparator);
                body.setSpan(new RelativeSizeSpan(0.3f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            boolean startsWithQuote = handleTextQuotes(body, darkBackground);
            if (message.getType() != Message.TYPE_PRIVATE) {
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                String privateMarker;
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    privateMarker = activity
                            .getString(R.string.private_message);
                } else {
                    final String to;
                    if (message.getCounterpart() != null) {
                        to = message.getCounterpart().getResourcepart();
                    } else {
                        to = "";
                    }
                    privateMarker = activity.getString(R.string.private_message_to, to);
                }
                body.insert(0, privateMarker);
                int privateMarkerIndex = privateMarker.length();
                if (startsWithQuote) {
                    body.insert(privateMarkerIndex, "\n\n");
                    body.setSpan(new DividerSpan(false), privateMarkerIndex, privateMarkerIndex + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    body.insert(privateMarkerIndex, " ");
                }
                body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarkerIndex + 1, privateMarkerIndex + 1 + nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            Linkify.addLinks(body, Linkify.WEB_URLS);
            Linkify.addLinks(body, XMPP_PATTERN, "xmpp");
            Linkify.addLinks(body, GeoHelper.GEO_URI, "geo");
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(body);
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
    }

    private void displayAudioMessage(ViewHolder viewHolder, final Message message, int position) {
        if (audioPlayer == null) audioPlayer = new HashMap<>();
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        if (viewHolder.download_button != null) {
            viewHolder.download_button.setVisibility(View.GONE);
        }
        viewHolder.aw_player.setVisibility(View.VISIBLE);
        Uri audioFile = Uri.fromFile(activity.xmppConnectionService.getFileBackend().getFile(message));

        audioWife = audioPlayer.get(position);
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
        String mimeType = message.getMimeType();
        String fullName = "";
        if (mimeType != null) {
        } else if (message.getMimeType().contains("vcard")) {
            File file = new File(activity.xmppConnectionService.getFileBackend().getFile(message).toString());
            VCard vcard = null;
            String name = null;
            String version = null;
            try {
                vcard = Ezvcard.parse(file).first();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (vcard != null) {
                version = vcard.getVersion().toString();
                Log.d(Config.LOGTAG, "VCard version: " + version);
                name = vcard.getFormattedName().getValue();
                fullName = " (" + name + ")";
            }
        }
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message) + fullName));
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                openDownloadable(message);
            }
        });
    }

    private void displayXmppMessage(final ViewHolder viewHolder, final String body) {
        String contact = body.toLowerCase();
        contact = contact.split(":")[1];
        contact = contact.split("\\?")[0];
        contact = contact.split("@")[0];
        String add_contact = activity.getString(R.string.add_to_contact_list) + " (" + contact + ")";
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(add_contact);
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(body));
                activity.startActivity(intent);
            }
        });
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);

    }

    private void displayLocationMessage(ViewHolder viewHolder, final Message message) {
        viewHolder.aw_player.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        String url = GeoHelper.MapPreviewUri(message);
        Log.d(Config.LOGTAG, "Map preview = " + url);
        viewHolder.image.setVisibility(View.VISIBLE);
        viewHolder.image.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showLocation(message);
            }
        });
        Glide
                .with(activity)
                .load(Uri.parse(url))
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_map_marker_grey600_48dp)
                .error(R.drawable.ic_map_marker_grey600_48dp)
                .into(viewHolder.image);
        viewHolder.image.getLayoutParams().width = 400;
        viewHolder.image.getLayoutParams().height = 400;
        viewHolder.image.setAdjustViewBounds(true);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.download_button.setText(R.string.show_location);
        viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_map_marker_grey600_48dp, 0, 0, 0);
        viewHolder.download_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showLocation(message);
            }
        });

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
    }

    private void loadMoreMessages(Conversation conversation) {
        conversation.setLastClearHistory(0);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        long timestamp = conversation.getLastMessageTransmitted();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        activity.setMessagesLoaded();
        activity.xmppConnectionService.getMessageArchiveService().query(conversation, 0, timestamp);
        Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
    }

    @Override
    public View getView(int position, View unused, ViewGroup parent) {
        final Message message = getItem(position);
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
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
                viewHolder.messageBody = (CopyTextView) view.findViewById(R.id.message_body);
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
                viewHolder.messageBody = (CopyTextView) view.findViewById(R.id.message_body);
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
        if (viewHolder.messageBody != null) {
            listSelectionManager.onCreate(viewHolder.messageBody,
                    new MessageBodyActionModeCallback(viewHolder.messageBody));
            viewHolder.messageBody.setCopyHandler(this);
        }
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
                        loadMoreMessages(message.getConversation());
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
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayDecryptionFailed(viewHolder, darkBackground);
        } else {
            if (GeoHelper.isGeoUri(message.getBody())) {
                displayLocationMessage(viewHolder, message);
            } else if (message.bodyIsHeart()) {
                displayHeartMessage(viewHolder, message.getBody().trim());
            } else if (message.bodyIsXmpp()) {
                displayXmppMessage(viewHolder, message.getBody().trim());
            } else if (message.treatAsDownloadable() == Message.Decision.MUST ||
                    message.treatAsDownloadable() == Message.Decision.SHOULD) {
                try {
                    URL url = new URL(message.getBody());
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message),
                                    url.getHost()));
                } catch (Exception e) {
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message)));
                }
            } else {
                displayTextMessage(viewHolder, message, darkBackground);
            }
        }

        if (type == RECEIVED) {
            if (isInValidSession) {
                viewHolder.message_box.setBackgroundResource(R.drawable.msg_bbl_recv);
                viewHolder.encryption.setVisibility(View.GONE);
            } else {
                viewHolder.message_box.setBackgroundResource(R.drawable.msg_bbl_warn);
                viewHolder.encryption.setVisibility(View.VISIBLE);
                if (omemoEncryption && !message.isTrusted()) {
                    viewHolder.encryption.setText(R.string.not_trusted);
                } else {
                    viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
                }
            }
        }

        displayStatus(viewHolder, message, type, darkBackground, isInValidSession);

        return view;
    }

    @Override
    public void notifyDataSetChanged() {
        listSelectionManager.onBeforeNotifyDataSetChanged();
        super.notifyDataSetChanged();
        listSelectionManager.onAfterNotifyDataSetChanged();
    }

    private String transformText(CharSequence text, int start, int end, boolean forCopy) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object copySpan = new Object();
        builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
        for (DividerSpan dividerSpan : dividerSpans) {
            builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
                    dividerSpan.isLarge() ? "\n\n" : "\n");
        }
        start = builder.getSpanStart(copySpan);
        end = builder.getSpanEnd(copySpan);
        if (start == -1 || end == -1) return "";
        builder = new SpannableStringBuilder(builder, start, end);
        if (forCopy) {
            QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
            for (QuoteSpan quoteSpan : quoteSpans) {
                builder.insert(builder.getSpanStart(quoteSpan), "> ");
            }
        }
        return builder.toString();
    }

    @Override
    public String transformTextForCopy(CharSequence text, int start, int end) {
        if (text instanceof Spanned) {
            return transformText(text, start, end, true);
        } else {
            return text.toString().substring(start, end);
        }
    }

    public interface OnQuoteListener {
        public void onQuote(String text);
    }

    private class MessageBodyActionModeCallback implements ActionMode.Callback {

        private final TextView textView;

        public MessageBodyActionModeCallback(TextView textView) {
            this.textView = textView;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (onQuoteListener != null) {
                int quoteResId = activity.getThemeResource(R.attr.icon_quote, R.drawable.ic_action_reply);
                // 3rd item is placed after "copy" item
                menu.add(0, android.R.id.button1, 3, R.string.quote).setIcon(quoteResId)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == android.R.id.button1) {
                int start = textView.getSelectionStart();
                int end = textView.getSelectionEnd();
                if (end > start) {
                    String text = transformText(textView.getText(), start, end, false);
                    if (onQuoteListener != null) {
                        onQuoteListener.onQuote(text);
                    }
                    mode.finish();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                uri = FileProvider.getUriForFile(activity, FileBackend.CONVERSATIONS_FILE_PROVIDER, file);
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
        protected CopyTextView messageBody;
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
