package net.atomarea.flowx.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import com.makeramen.roundedimageview.RoundedImageView;
import com.yalantis.contextmenu.lib.ContextMenuDialogFragment;
import com.yalantis.contextmenu.lib.MenuObject;
import com.yalantis.contextmenu.lib.MenuParams;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.crypto.axolotl.AxolotlService;
import net.atomarea.flowx.crypto.axolotl.XmppAxolotlSession;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Blockable;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.services.XmppConnectionService.OnAccountUpdate;
import net.atomarea.flowx.services.XmppConnectionService.OnConversationUpdate;
import net.atomarea.flowx.services.XmppConnectionService.OnRosterUpdate;
import net.atomarea.flowx.ui.adapter.ConversationAdapter;
import net.atomarea.flowx.utils.ExceptionHelper;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.OnUpdateBlocklist;
import net.atomarea.flowx.xmpp.chatstate.ChatState;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.java.otr4j.session.SessionStatus;

import org.openintents.openpgp.util.OpenPgpApi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.timroes.android.listview.EnhancedListView;
import github.ankushsachdeva.emojicon.EmojiconTextView;

public class ConversationActivity extends XmppActivity implements OnAccountUpdate, OnConversationUpdate, OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast, View.OnClickListener {

    public static final String ACTION_DOWNLOAD = "net.atomarea.flowx.action.DOWNLOAD";

    public static final String VIEW_CONVERSATION = "viewConversation";
    public static final String CONVERSATION = "conversationUuid";
    public static final String MESSAGE = "messageUuid";
    public static final String TEXT = "text";
    public static final String NICK = "nick";
    public static final String PRIVATE_MESSAGE = "pm";

    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
    public static final int ATTACHMENT_CHOICE_INVALID = 0x0306;
    private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
    private static final String STATE_PANEL_OPEN = "state_panel_open";
    private static final String STATE_PENDING_URI = "state_pending_uri";
    final private List<Uri> mPendingImageUris = new ArrayList<>();
    final private List<Uri> mPendingFileUris = new ArrayList<>();
    private String mOpenConverstaion = null;
    private boolean mPanelOpen = true;
    private Uri mPendingGeoUri = null;
    private boolean forbidProcessingPendings = false;
    private Message mPendingDownloadableMessage = null;

    private boolean conversationWasSelectedByKeyboard = false;

    private View mContentView;

    private List<Conversation> conversationList = new ArrayList<>();
    private Conversation swipedConversation = null;
    private Conversation mSelectedConversation = null;
    private EnhancedListView listView;
    private ConversationFragment mConversationFragment;

    private ArrayAdapter<Conversation> listAdapter;

    private boolean mActivityPaused = false;
    private AtomicBoolean mRedirected = new AtomicBoolean(false);
    private Pair<Integer, Intent> mPostponedActivityResult;
    private int lastAbState = -1;

    @SuppressLint("NewApi")
    private static List<Uri> extractUriFromIntent(final Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) return uris;
        Uri uri = intent.getData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && uri == null) {
            ClipData clipData = intent.getClipData();
            for (int i = 0; i < clipData.getItemCount(); ++i)
                uris.add(clipData.getItemAt(i).getUri());
        } else uris.add(uri);
        return uris;
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

    public Conversation getSelectedConversation() {
        return mSelectedConversation;
    }

    public void setSelectedConversation(Conversation conversation) {
        mSelectedConversation = conversation;
    }

    public void showConversationsOverview() {
        if (mContentView instanceof SlidingPaneLayout) {
            SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
            mSlidingPaneLayout.openPane();
        }
    }

    @Override
    protected String getShareableUri() {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            return conversation.getAccount().getShareableUri();
        } else {
            return "";
        }
    }

    public void hideConversationsOverview() {
        if (mContentView instanceof SlidingPaneLayout) {
            SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
            mSlidingPaneLayout.closePane();
        }
    }

    public boolean isConversationsOverviewHideable() {
        return (mContentView instanceof SlidingPaneLayout);
    }

    public boolean isConversationsOverviewVisable() {
        if (mContentView instanceof SlidingPaneLayout) {
            SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
            return mSlidingPaneLayout.isOpen();
        } else {
            return true;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FileBackend.getConversationsFileDirectory();
        if (savedInstanceState != null) {
            mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION, null);
            mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, true);
            String pending = savedInstanceState.getString(STATE_PENDING_URI, null);
            if (pending != null) {
                mPendingImageUris.clear();
                mPendingImageUris.add(Uri.parse(pending));
            }
        }

        setContentView(R.layout.fragment_conversations_overview);

        this.mConversationFragment = new ConversationFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.selected_conversation, this.mConversationFragment, "conversation");
        transaction.commit();

        listView = (EnhancedListView) findViewById(R.id.list);
        this.listAdapter = new ConversationAdapter(this, conversationList);
        listView.setAdapter(this.listAdapter);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }

        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View clickedView,
                                    int position, long arg3) {
                if (getSelectedConversation() != conversationList.get(position)) {
                    setSelectedConversation(conversationList.get(position));
                    ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
                    conversationWasSelectedByKeyboard = false;
                }
                hideConversationsOverview();
                openConversation();
            }
        });

        listView.setDismissCallback(new EnhancedListView.OnDismissCallback() {

            @Override
            public EnhancedListView.Undoable onDismiss(final EnhancedListView enhancedListView, final int position) {

                final int index = listView.getFirstVisiblePosition();
                View v = listView.getChildAt(0);
                final int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());

                try {
                    swipedConversation = listAdapter.getItem(position);
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
                listAdapter.remove(swipedConversation);
                xmppConnectionService.markRead(swipedConversation);

                final boolean formerlySelected = (getSelectedConversation() == swipedConversation);
                if (position == 0 && listAdapter.getCount() == 0) {
                    endConversation(swipedConversation, false, true);
                    return null;
                } else if (formerlySelected) {
                    setSelectedConversation(listAdapter.getItem(0));
                    ConversationActivity.this.mConversationFragment
                            .reInit(getSelectedConversation());
                }

                return new EnhancedListView.Undoable() {

                    @Override
                    public void undo() {
                        listAdapter.insert(swipedConversation, position);
                        if (formerlySelected) {
                            setSelectedConversation(swipedConversation);
                            ConversationActivity.this.mConversationFragment
                                    .reInit(getSelectedConversation());
                        }
                        swipedConversation = null;
                        if (Build.VERSION.SDK_INT >= 21)
                            listView.setSelectionFromTop(index + (listView.getChildCount() < position ? 1 : 0), top);
                        else
                            Toast.makeText(getApplicationContext(), "TODO! Blame the devs :D", Toast.LENGTH_SHORT).show(); // TODO: !
                    }

                    @Override
                    public void discard() {
                        if (!swipedConversation.isRead()
                                && swipedConversation.getMode() == Conversation.MODE_SINGLE) {
                            swipedConversation = null;
                            return;
                        }
                        endConversation(swipedConversation, false, false);
                        swipedConversation = null;
                    }

                    @Override
                    public String getTitle() {
                        if (swipedConversation.getMode() == Conversation.MODE_MULTI) {
                            return getResources().getString(R.string.title_undo_swipe_out_muc);
                        } else {
                            return getResources().getString(R.string.title_undo_swipe_out_conversation);
                        }
                    }
                };
            }
        });
        listView.enableSwipeToDismiss();
        listView.setSwipingLayout(R.id.swipeable_item);
        listView.setUndoStyle(EnhancedListView.UndoStyle.SINGLE_POPUP);
        listView.setUndoHideDelay(5000);
        listView.setRequireTouchBeforeDismiss(false);

        mContentView = findViewById(R.id.content_view_spl);

        if (mContentView instanceof SlidingPaneLayout) {
            SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
            mSlidingPaneLayout.setParallaxDistance(150);
            // mSlidingPaneLayout.setShadowResource(R.drawable.es_slidingpane_shadow);
            mSlidingPaneLayout.setSliderFadeColor(0);
            mSlidingPaneLayout.setPanelSlideListener(new PanelSlideListener() {
                @Override
                public void onPanelOpened(View v) {
                    updateActionBarTitle();
                    invalidateOptionsMenu();
                    hideKeyboard();
                    if (xmppConnectionServiceBound) {
                        xmppConnectionService.getNotificationService()
                                .setOpenConversation(null);
                    }
                    closeContextMenu();
                }

                @Override
                public void onPanelClosed(View v) {
                    listView.discardUndo();
                    openConversation();
                }

                @Override
                public void onPanelSlide(View v, float f) { // unnötig...
                }
            });
        }

        if (Build.VERSION.SDK_INT >= 23) {
            int hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
            if (hasMicPerm != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1337);
            }
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        setSelectedConversation(conversation);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
                openConversation();
            }
        });
    }

    private void updateActionBarTitle() {
        updateActionBarTitle(isConversationsOverviewHideable() && !isConversationsOverviewVisable());
    }

    private void updateActionBarTitle(boolean titleShouldBeName) {
        final ActionBar ab = getActionBar();
        final Conversation conversation = getSelectedConversation();

        if (ab == null) return;

        ab.setDisplayShowCustomEnabled(true);
        ab.setDisplayShowTitleEnabled(false);
        LayoutInflater inflator = LayoutInflater.from(this);
        View v = ab.getCustomView();
        if (v == null) {
            v = inflator.inflate(R.layout.actionbar, null);
            ab.setCustomView(v);
        }
        final EmojiconTextView tv_title = (EmojiconTextView) v.findViewById(R.id.title);
        final EmojiconTextView tv_subtitle = (EmojiconTextView) v.findViewById(R.id.subtitle);
        final EmojiconTextView tv_flowx = (EmojiconTextView) v.findViewById(R.id.title2);
        final RoundedImageView iv_avatar = (RoundedImageView) v.findViewById(R.id.avatar_pic);
        iv_avatar.setVisibility(View.VISIBLE);
        if (titleShouldBeName && conversation != null) {
            if (lastAbState != 1) {
                lastAbState = 1;
                tv_flowx.setAlpha(1f);
                tv_title.setAlpha(0f);
                tv_subtitle.setAlpha(0f);
                iv_avatar.setAlpha(0f);
                tv_flowx.animate().setStartDelay(0).alpha(0f).setDuration(200).start();
                tv_title.animate().setStartDelay(500).alpha(1f).setDuration(200).start();
                tv_subtitle.animate().setStartDelay(1300).alpha(1f).setDuration(200).start(); // Daten laden, dann erst anzeigen... daher das Delay...
                iv_avatar.animate().setStartDelay(300).alpha(1f).setDuration(200).start();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadAvatar(conversation, iv_avatar);
                        ab.setDisplayHomeAsUpEnabled(true);
                    }
                }, 250);
            }
            if (conversation.getMode() == Conversation.MODE_SINGLE || useSubjectToIdentifyConference()) {
                tv_title.setText(conversation.getName());
                v.setOnClickListener(this);
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    ChatState state = conversation.getIncomingChatState();
                    if (state == ChatState.COMPOSING) {
                        tv_subtitle.setText(getString(R.string.contact_is_typing));
                        v.setOnClickListener(this);
                    } else if (state == ChatState.PAUSED) {
                        tv_subtitle.setText(getString(R.string.contact_has_stopped_typing));
                        v.setOnClickListener(this);
                    } else
                        tv_subtitle.setText(UIHelper.lastseen(getApplicationContext(), conversation.getContact().lastseen.time));
                } else if (useSubjectToIdentifyConference()) {
                    tv_subtitle.setText((conversation.getParticipants() == null ? "-" : conversation.getParticipants()));
                }
            } else ab.setTitle(conversation.getJid().toBareJid().toString());
        } else {
            tv_flowx.setText(R.string.app_name);
            if (lastAbState != 2) {
                lastAbState = 2;
                tv_flowx.setAlpha(0f);
                tv_title.setAlpha(1f);
                tv_subtitle.setAlpha(1f);
                iv_avatar.setAlpha(1f);
                tv_title.animate().setStartDelay(0).alpha(0f).setDuration(200).start();
                tv_subtitle.animate().setStartDelay(0).alpha(0f).setDuration(200).start();
                iv_avatar.animate().setStartDelay(0).alpha(0f).setDuration(200).start();
                tv_flowx.animate().setStartDelay(300).alpha(1f).setDuration(200).start();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ab.setDisplayHomeAsUpEnabled(false);
                    }
                }, 250);
            }
        }
    }

    private void openConversation() {
        updateActionBarTitle();
        invalidateOptionsMenu();
        if (xmppConnectionServiceBound) {
            final Conversation conversation = getSelectedConversation();
            xmppConnectionService.getNotificationService().setOpenConversation(conversation);
            sendReadMarkerIfNecessary(conversation);
        }
        listAdapter.notifyDataSetChanged();
    }

    public void sendReadMarkerIfNecessary(final Conversation conversation) {
        if (!mActivityPaused && conversation != null) {
            if (!conversation.isRead()) xmppConnectionService.sendReadMarker(conversation);
            else xmppConnectionService.markRead(conversation);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);
        final MenuItem menuSecure = menu.findItem(R.id.action_security);
        final MenuItem menuArchive = menu.findItem(R.id.action_archive);
        final MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
        final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
        final MenuItem menuAttach = menu.findItem(R.id.action_attach_file);
        final MenuItem menuClearHistory = menu.findItem(R.id.action_clear_history);
        final MenuItem menuAdd = menu.findItem(R.id.action_add);
        final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
        final MenuItem menuMute = menu.findItem(R.id.action_mute);
        final MenuItem menuUnmute = menu.findItem(R.id.action_unmute);

        if (isConversationsOverviewVisable() && isConversationsOverviewHideable()) {
            menuArchive.setVisible(false);
            menuMucDetails.setVisible(false);
            menuContactDetails.setVisible(false);
            menuSecure.setVisible(false);
            menuInviteContact.setVisible(false);
            menuAttach.setVisible(false);
            menuClearHistory.setVisible(false);
            menuMute.setVisible(false);
            menuUnmute.setVisible(false);
        } else {
            menuAdd.setVisible(!isConversationsOverviewHideable());
            if (this.getSelectedConversation() != null) {
                if (this.getSelectedConversation().getNextEncryption() != Message.ENCRYPTION_NONE) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        menuSecure.setIcon(R.drawable.ic_lock_white_24dp);
                    else menuSecure.setIcon(R.drawable.ic_action_secure);
                }
                if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    menuContactDetails.setVisible(false);
                    menuAttach.setVisible(getSelectedConversation().getAccount().httpUploadAvailable() && getSelectedConversation().getMucOptions().participating());
                    menuInviteContact.setVisible(getSelectedConversation().getMucOptions().canInvite());
                    menuSecure.setVisible(false); // !Config.HIDE_PGP_IN_UI && !Config.X509_VERIFICATION <- Sinnfreiii :D
                } else menuMucDetails.setVisible(false);
                if (this.getSelectedConversation().isMuted()) menuMute.setVisible(false);
                else menuUnmute.setVisible(false);
            }
        }
        return true;
    }

    protected void selectPresenceToAttachFile(final int attachmentChoice, final int encryption) {
        final Conversation conversation = getSelectedConversation();
        final Account account = conversation.getAccount();
        final OnPresenceSelected callback = new OnPresenceSelected() {
            @Override
            public void onPresenceSelected() {
                Intent intent = new Intent();
                boolean chooser = false;
                String fallbackPackageId = null;
                switch (attachmentChoice) {
                    case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        }
                        intent.setType("image/*");
                        chooser = true;
                        break;
                    case ATTACHMENT_CHOICE_TAKE_PHOTO:
                        Uri uri = xmppConnectionService.getFileBackend().getTakePhotoUri();
                        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                        mPendingImageUris.clear();
                        mPendingImageUris.add(uri);
                        break;
                    case ATTACHMENT_CHOICE_CHOOSE_FILE:
                        chooser = true;
                        intent.setType("*/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        break;
                    case ATTACHMENT_CHOICE_RECORD_VOICE:
                        if (Build.VERSION.SDK_INT >= 23) {
                            int hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
                            if (hasMicPerm == PackageManager.PERMISSION_GRANTED) {
                                intent.setAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                                intent.setPackage("net.atomarea.flowx");
                            } else
                                Toast.makeText(getApplicationContext(), "No perm 4 mic... -> change in settings of phone", Toast.LENGTH_SHORT).show(); // TODO: !Txt in strings.xml
                        } else {
                            intent.setAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                            intent.setPackage("net.atomarea.flowx");
                        }
                        break;
                    case ATTACHMENT_CHOICE_LOCATION:
                        intent.setAction("net.atomarea.flowx.location.request");
                        fallbackPackageId = "net.atomarea.flowx.sharelocation";
                        break;
                }
                if (intent.resolveActivity(getPackageManager()) != null) {
                    if (chooser) {
                        startActivityForResult(
                                Intent.createChooser(intent, getString(R.string.perform_action_with)),
                                attachmentChoice);
                    } else startActivityForResult(intent, attachmentChoice);
                } else if (fallbackPackageId != null)
                    startActivity(getInstallApkIntent(fallbackPackageId));
            }
        };
        if ((account.httpUploadAvailable() || attachmentChoice == ATTACHMENT_CHOICE_LOCATION) && encryption != Message.ENCRYPTION_OTR) {
            conversation.setNextCounterpart(null);
            callback.onPresenceSelected();
        } else selectPresence(conversation, callback);
    }

    private Intent getInstallApkIntent(final String packageId) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=" + packageId));
        if (intent.resolveActivity(getPackageManager()) != null) return intent;
        else {
            intent.setData(Uri.parse("http://play.google.com/store/apps/details?id=" + packageId));
            return intent;
        }
    }

    public void attachFile(final int attachmentChoice) {
        if (attachmentChoice != ATTACHMENT_CHOICE_LOCATION) {
            if (!hasStoragePermission(attachmentChoice)) return;
        }
        switch (attachmentChoice) {
            case ATTACHMENT_CHOICE_LOCATION:
                getPreferences().edit().putString("recently_used_quick_action", "location").apply();
                break;
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                getPreferences().edit().putString("recently_used_quick_action", "voice").apply();
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                getPreferences().edit().putString("recently_used_quick_action", "photo").apply();
                break;
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                getPreferences().edit().putString("recently_used_quick_action", "picture").apply();
                break;
        }
        final Conversation conversation = getSelectedConversation();
        final int encryption = conversation.getNextEncryption();
        final int mode = conversation.getMode();
        if (encryption == Message.ENCRYPTION_PGP) {
            if (hasPgp()) {
                if (mode == Conversation.MODE_SINGLE && conversation.getContact().getPgpKeyId() != 0) {
                    xmppConnectionService.getPgpEngine().hasKey(
                            conversation.getContact(),
                            new UiCallback<Contact>() {
                                @Override
                                public void userInputRequried(PendingIntent pi, Contact contact) {
                                    ConversationActivity.this.runIntent(pi, attachmentChoice);
                                }

                                @Override
                                public void success(Contact contact) {
                                    selectPresenceToAttachFile(attachmentChoice, encryption);
                                }

                                @Override
                                public void error(int error, Contact contact) {
                                    displayErrorDialog(error);
                                }
                            });
                } else if (mode == Conversation.MODE_MULTI && conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(this, R.string.missing_public_keys, Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    selectPresenceToAttachFile(attachmentChoice, encryption);
                } else {
                    final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
                            .findFragmentByTag("conversation");
                    if (fragment != null) {
                        fragment.showNoPGPKeyDialog(false,
                                new OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                        xmppConnectionService.databaseBackend.updateConversation(conversation);
                                        selectPresenceToAttachFile(attachmentChoice, Message.ENCRYPTION_NONE);
                                    }
                                });
                    }
                }
            } else {
                showInstallPgpDialog();
            }
        } else if (encryption != Message.ENCRYPTION_AXOLOTL || !trustKeysIfNeeded(REQUEST_TRUST_KEYS_MENU, attachmentChoice))
            selectPresenceToAttachFile(attachmentChoice, encryption);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1337) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED))
                Toast.makeText(this, "No perm 4 mic...", Toast.LENGTH_SHORT).show(); // TODO: ! Text in strings.xml
        } else {
            if (grantResults.length > 0)
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    if (requestCode == REQUEST_START_DOWNLOAD) {
                        if (this.mPendingDownloadableMessage != null)
                            startDownloadable(this.mPendingDownloadableMessage);
                    } else attachFile(requestCode);
                else
                    Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
        }
    }

    public void startDownloadable(Message message) {
        if (!hasStoragePermission(ConversationActivity.REQUEST_START_DOWNLOAD)) {
            this.mPendingDownloadableMessage = message;
            return;
        }
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            if (!transferable.start())
                Toast.makeText(this, R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
        } else if (message.treatAsDownloadable() != Message.Decision.NEVER)
            xmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message, true);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            showConversationsOverview();
            return true;
        } else if (item.getItemId() == R.id.action_add) {
            startActivity(new Intent(this, StartConversationActivity.class));
            return true;
        } else if (getSelectedConversation() != null) {
            switch (item.getItemId()) {
                case R.id.action_attach_file:
                    attachFileDialog();
                    break;
                case R.id.action_archive:
                    this.endConversation(getSelectedConversation());
                    break;
                case R.id.action_contact_details:
                    switchToContactDetails(getSelectedConversation().getContact());
                    break;
                case R.id.action_muc_details:
                    Intent intent = new Intent(this,
                            ConferenceDetailsActivity.class);
                    intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
                    intent.putExtra("uuid", getSelectedConversation().getUuid());
                    startActivity(intent);
                    break;
                case R.id.action_invite:
                    inviteToConversation(getSelectedConversation());
                    break;
                case R.id.action_security:
                    selectEncryptionDialog(getSelectedConversation());
                    break;
                case R.id.action_clear_history:
                    clearHistoryDialog(getSelectedConversation());
                    break;
                case R.id.action_mute:
                    muteConversationDialog(getSelectedConversation());
                    break;
                case R.id.action_unmute:
                    unmuteConversation(getSelectedConversation());
                    break;
                case R.id.action_block:
                    BlockContactDialog.show(this, xmppConnectionService, getSelectedConversation());
                    break;
                case R.id.action_unblock:
                    BlockContactDialog.show(this, xmppConnectionService, getSelectedConversation());
                    break;
                default:
                    break;
            }
            return super.onOptionsItemSelected(item);
        } else return super.onOptionsItemSelected(item);
    }

    public void endConversation(Conversation conversation) {
        endConversation(conversation, true, true);
    }

    public void endConversation(Conversation conversation, boolean showOverview, boolean reinit) {
        if (showOverview) showConversationsOverview();
        xmppConnectionService.archiveConversation(conversation);
        if (reinit) {
            if (conversationList.size() > 0) {
                setSelectedConversation(conversationList.get(0));
                this.mConversationFragment.reInit(getSelectedConversation());
            } else {
                setSelectedConversation(null);
                if (mRedirected.compareAndSet(false, true)) {
                    Intent intent = new Intent(this, StartConversationActivity.class);
                    intent.putExtra("init", true);
                    startActivity(intent);
                    finish();
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    protected void clearHistoryDialog(final Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.clear_conversation_history));
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
        final CheckBox endConversationCheckBox = (CheckBox) dialogView.findViewById(R.id.end_conversation_checkbox);
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.delete_messages),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ConversationActivity.this.xmppConnectionService.clearConversationHistory(conversation);
                        if (endConversationCheckBox.isChecked()) endConversation(conversation);
                        else {
                            updateConversationList();
                            ConversationActivity.this.mConversationFragment.updateMessages();
                        }
                    }
                });
        builder.create().show();
    }

    protected void attachFileDialog() {
        View menuAttachFile = findViewById(R.id.action_attach_file);
        if (menuAttachFile == null) return;
        List<MenuObject> lst = new ArrayList<>();
        lst.add(new MenuObject());
        lst.get(0).setResource(R.drawable.ic_send_cancel_offline);
        lst.add(new MenuObject(getResources().getString(R.string.attach_choose_picture)));
        lst.get(1).setResource(R.drawable.ic_send_picture_offline);
        lst.add(new MenuObject(getResources().getString(R.string.attach_take_picture)));
        lst.get(2).setResource(R.drawable.ic_send_photo_offline);
        lst.add(new MenuObject(getResources().getString(R.string.attach_file)));
        lst.get(3).setResource(R.drawable.ic_send_file_offline);
        lst.add(new MenuObject(getResources().getString(R.string.attach_record_voice)));
        lst.get(4).setResource(R.drawable.ic_send_voice_offline);
        if (!(new Intent("net.atomarea.flowx.location.request").resolveActivity(getPackageManager()) == null)) {
            lst.add(new MenuObject(getResources().getString(R.string.send_location)));
            lst.get(5).setResource(R.drawable.ic_send_location_online);
        }
        MenuParams mp = new MenuParams();
        if (getActionBar() != null) mp.setActionBarSize(getActionBar().getHeight());
        mp.setMenuObjects(lst);
        mp.setClosableOutside(true);
        ContextMenuDialogFragment cmdf = ContextMenuDialogFragment.newInstance(mp);
        cmdf.setItemClickListener(new com.yalantis.contextmenu.lib.interfaces.OnMenuItemClickListener() {
            @Override
            public void onMenuItemClick(View clickedView, int position) {
                switch (position) {
                    case 1:
                        attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                        break;
                    case 2:
                        attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
                        break;
                    case 3:
                        attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
                        break;
                    case 4:
                        attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
                        break;
                    case 5:
                        attachFile(ATTACHMENT_CHOICE_LOCATION);
                        break;
                }
            }
        });
        cmdf.show(getSupportFragmentManager(), "CMDF");
    }

    public void verifyOtrSessionDialog(final Conversation conversation, View view) {
        if (!conversation.hasValidOtrSession() || conversation.getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
            Toast.makeText(this, R.string.otr_session_not_started, Toast.LENGTH_LONG).show();
            return;
        }
        if (view == null) return;
        PopupMenu popup = new PopupMenu(this, view);
        popup.inflate(R.menu.verification_choices);
        popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent intent = new Intent(ConversationActivity.this, VerifyOTRActivity.class);
                intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
                intent.putExtra("contact", conversation.getContact().getJid().toBareJid().toString());
                intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().toBareJid().toString());
                switch (menuItem.getItemId()) {
                    case R.id.scan_fingerprint:
                        intent.putExtra("mode", VerifyOTRActivity.MODE_SCAN_FINGERPRINT);
                        break;
                    case R.id.ask_question:
                        intent.putExtra("mode", VerifyOTRActivity.MODE_ASK_QUESTION);
                        break;
                    case R.id.manual_verification:
                        intent.putExtra("mode", VerifyOTRActivity.MODE_MANUAL_VERIFICATION);
                        break;
                }
                startActivity(intent);
                return true;
            }
        });
        popup.show();
    }

    protected void selectEncryptionDialog(final Conversation conversation) {
        View menuItemView = findViewById(R.id.action_security);
        if (menuItemView == null) return;
        if (conversation.getNextEncryption() == Message.ENCRYPTION_NONE) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(conversation.getAccount()) + "Enabled axolotl for Contact " + conversation.getContact().getJid());
            conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
        } else if (conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(conversation.getAccount()) + "Disabled axolotl (enabled none) for Contact " + conversation.getContact().getJid());
            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
        }
        final ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
        xmppConnectionService.databaseBackend.updateConversation(conversation);
        fragment.updateChatMsgHint();
        invalidateOptionsMenu();
        refreshUi();
    }

    protected void muteConversationDialog(final Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.disable_notifications);
        final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
        builder.setItems(R.array.mute_options_descriptions,
                new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final long till;
                        if (durations[which] == -1) {
                            till = Long.MAX_VALUE;
                        } else {
                            till = System.currentTimeMillis() + (durations[which] * 1000);
                        }
                        conversation.setMutedTill(till);
                        ConversationActivity.this.xmppConnectionService.databaseBackend
                                .updateConversation(conversation);
                        updateConversationList();
                        ConversationActivity.this.mConversationFragment.updateMessages();
                        invalidateOptionsMenu();
                    }
                });
        builder.create().show();
    }

    public void unmuteConversation(final Conversation conversation) {
        conversation.setMutedTill(0);
        this.xmppConnectionService.databaseBackend.updateConversation(conversation);
        updateConversationList();
        ConversationActivity.this.mConversationFragment.updateMessages();
        invalidateOptionsMenu();
    }

    @Override
    public void onBackPressed() {
        if (!isConversationsOverviewVisable()) showConversationsOverview();
        else moveTaskToBack(true);
    }

    @Override
    public boolean onKeyUp(int key, KeyEvent event) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        final int upKey;
        final int downKey;
        switch (rotation) {
            case Surface.ROTATION_90:
                upKey = KeyEvent.KEYCODE_DPAD_LEFT;
                downKey = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Surface.ROTATION_180:
                upKey = KeyEvent.KEYCODE_DPAD_DOWN;
                downKey = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case Surface.ROTATION_270:
                upKey = KeyEvent.KEYCODE_DPAD_RIGHT;
                downKey = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            default:
                upKey = KeyEvent.KEYCODE_DPAD_UP;
                downKey = KeyEvent.KEYCODE_DPAD_DOWN;
        }
        final boolean modifier = event.isCtrlPressed() || event.isAltPressed();
        if (modifier && key == KeyEvent.KEYCODE_TAB && isConversationsOverviewHideable()) {
            toggleConversationsOverview();
            return true;
        } else if (modifier && key == downKey) {
            if (isConversationsOverviewHideable() && !isConversationsOverviewVisable())
                showConversationsOverview();
            return selectDownConversation();
        } else if (modifier && key == upKey) {
            if (isConversationsOverviewHideable() && !isConversationsOverviewVisable())
                showConversationsOverview();
            return selectUpConversation();
        } else if (modifier && key == KeyEvent.KEYCODE_1) return openConversationByIndex(0);
        else if (modifier && key == KeyEvent.KEYCODE_2) return openConversationByIndex(1);
        else if (modifier && key == KeyEvent.KEYCODE_3) return openConversationByIndex(2);
        else if (modifier && key == KeyEvent.KEYCODE_4) return openConversationByIndex(3);
        else if (modifier && key == KeyEvent.KEYCODE_5) return openConversationByIndex(4);
        else if (modifier && key == KeyEvent.KEYCODE_6) return openConversationByIndex(5);
        else if (modifier && key == KeyEvent.KEYCODE_7) return openConversationByIndex(6);
        else if (modifier && key == KeyEvent.KEYCODE_8) return openConversationByIndex(7);
        else if (modifier && key == KeyEvent.KEYCODE_9) return openConversationByIndex(8);
        else if (modifier && key == KeyEvent.KEYCODE_0) return openConversationByIndex(9);
        else return super.onKeyUp(key, event);
    }

    private void toggleConversationsOverview() {
        if (isConversationsOverviewVisable()) {
            hideConversationsOverview();
            if (mConversationFragment != null) mConversationFragment.setFocusOnInputField();
        } else showConversationsOverview();
    }

    private boolean selectUpConversation() {
        if (this.mSelectedConversation != null) {
            int index = this.conversationList.indexOf(this.mSelectedConversation);
            if (index > 0) return openConversationByIndex(index - 1);
        }
        return false;
    }

    private boolean selectDownConversation() {
        if (this.mSelectedConversation != null) {
            int index = this.conversationList.indexOf(this.mSelectedConversation);
            if (index != -1 && index < this.conversationList.size() - 1)
                return openConversationByIndex(index + 1);
        }
        return false;
    }

    private boolean openConversationByIndex(int index) {
        try {
            this.conversationWasSelectedByKeyboard = true;
            setSelectedConversation(this.conversationList.get(index));
            this.mConversationFragment.reInit(getSelectedConversation());
            if (index > listView.getLastVisiblePosition() - 1 || index < listView.getFirstVisiblePosition() + 1)
                this.listView.setSelection(index);
            openConversation();
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (xmppConnectionServiceBound) {
            if (intent != null && VIEW_CONVERSATION.equals(intent.getType())) {
                handleViewConversationIntent(intent);
                setIntent(new Intent());
            }
        } else setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.mRedirected.set(false);
        if (this.xmppConnectionServiceBound) this.onBackendConnected();
        if (conversationList.size() >= 1) this.onConversationUpdate();
    }

    @Override
    public void onPause() {
        listView.discardUndo();
        super.onPause();
        this.mActivityPaused = true;
        if (this.xmppConnectionServiceBound)
            this.xmppConnectionService.getNotificationService().setIsInForeground(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        final int theme = findTheme();
        final boolean usingEnterKey = usingEnterKey();
        if (this.mTheme != theme || usingEnterKey != mUsingEnterKey) recreate();
        this.mActivityPaused = false;
        if (this.xmppConnectionServiceBound)
            this.xmppConnectionService.getNotificationService().setIsInForeground(true);
        if (!isConversationsOverviewVisable() || !isConversationsOverviewHideable())
            sendReadMarkerIfNecessary(getSelectedConversation());
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null)
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        else savedInstanceState.remove(STATE_OPEN_CONVERSATION);
        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
        if (this.mPendingImageUris.size() >= 1)
            savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUris.get(0).toString());
        else savedInstanceState.remove(STATE_PENDING_URI);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void clearPending() {
        mPendingImageUris.clear();
        mPendingFileUris.clear();
        mPendingGeoUri = null;
        mPostponedActivityResult = null;
    }

    @Override
    void onBackendConnected() {
        this.xmppConnectionService.getNotificationService().setIsInForeground(true);
        updateConversationList();

        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }

        if (xmppConnectionService.getAccounts().size() == 0) {
            if (mRedirected.compareAndSet(false, true)) {
                if (Config.X509_VERIFICATION)
                    startActivity(new Intent(this, ManageAccountActivity.class));
                else startActivity(new Intent(this, EditAccountActivity.class));
                finish();
            }
        } else if (conversationList.size() <= 0) {
            if (mRedirected.compareAndSet(false, true)) {
                Intent intent = new Intent(this, StartConversationActivity.class);
                intent.putExtra("init", true);
                startActivity(intent);
                finish();
            }
        } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
            clearPending();
            handleViewConversationIntent(getIntent());
        } else if (selectConversationByUuid(mOpenConverstaion)) {
            if (mPanelOpen) showConversationsOverview();
            else {
                if (isConversationsOverviewHideable()) {
                    openConversation();
                    updateActionBarTitle(true);
                }
            }
            this.mConversationFragment.reInit(getSelectedConversation());
            mOpenConverstaion = null;
        } else if (getSelectedConversation() == null) {
            showConversationsOverview();
            clearPending();
            setSelectedConversation(conversationList.get(0));
            this.mConversationFragment.reInit(getSelectedConversation());
        } else {
            this.mConversationFragment.messageListAdapter.updatePreferences();
            this.mConversationFragment.messagesView.invalidateViews();
            this.mConversationFragment.setupIme();
        }
        if (this.mPostponedActivityResult != null)
            this.onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
        if (!forbidProcessingPendings) {
            for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
                Uri foo = i.next();
                attachImageToConversation(getSelectedConversation(), foo);
            }
            for (Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
                attachFileToConversation(getSelectedConversation(), i.next());
            }
            if (mPendingGeoUri != null) {
                attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
                mPendingGeoUri = null;
            }
        }
        forbidProcessingPendings = false;
        ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
        openBatteryOptimizationDialogIfNeeded();
        setIntent(new Intent());
    }

    private void handleViewConversationIntent(final Intent intent) {
        final String uuid = intent.getStringExtra(CONVERSATION);
        final String downloadUuid = intent.getStringExtra(MESSAGE);
        final String text = intent.getStringExtra(TEXT);
        final String nick = intent.getStringExtra(NICK);
        final boolean pm = intent.getBooleanExtra(PRIVATE_MESSAGE, false);
        if (selectConversationByUuid(uuid)) {
            this.mConversationFragment.reInit(getSelectedConversation());
            if (nick != null) {
                if (pm) {
                    Jid jid = getSelectedConversation().getJid();
                    try {
                        Jid next = Jid.fromParts(jid.getLocalpart(), jid.getDomainpart(), nick);
                        this.mConversationFragment.privateMessageWith(next);
                    } catch (final InvalidJidException ignored) {
                    }
                } else this.mConversationFragment.highlightInConference(nick);
            } else this.mConversationFragment.appendText(text);
            hideConversationsOverview();
            openConversation();
            if (mContentView instanceof SlidingPaneLayout)
                updateActionBarTitle(true); //fixes bug where slp isn't properly closed yet
            if (downloadUuid != null) {
                final Message message = mSelectedConversation.findMessageWithFileAndUuid(downloadUuid);
                if (message != null) startDownloadable(message);
            }
        }
    }

    private boolean selectConversationByUuid(String uuid) {
        if (uuid == null) return false;
        for (Conversation aConversationList : conversationList) {
            if (aConversationList.getUuid().equals(uuid)) {
                setSelectedConversation(aConversationList);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void unregisterListeners() {
        super.unregisterListeners();
        xmppConnectionService.getNotificationService().setOpenConversation(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP)
                mConversationFragment.onActivityResult(requestCode, resultCode, data);
            else if (requestCode == REQUEST_CHOOSE_PGP_ID) {
                if (xmppConnectionServiceBound) {
                    if (data.getExtras().containsKey(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                        mSelectedConversation.getAccount().setPgpSignId(data.getExtras().getLong(OpenPgpApi.EXTRA_SIGN_KEY_ID));
                        announcePgp(mSelectedConversation.getAccount(), null);
                    } else choosePgpSignId(mSelectedConversation.getAccount());
                    this.mPostponedActivityResult = null;
                } else this.mPostponedActivityResult = new Pair<>(requestCode, data);
            } else if (requestCode == REQUEST_ANNOUNCE_PGP) {
                if (xmppConnectionServiceBound) {
                    announcePgp(mSelectedConversation.getAccount(), mSelectedConversation);
                    this.mPostponedActivityResult = null;
                } else this.mPostponedActivityResult = new Pair<>(requestCode, data);
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                mPendingImageUris.clear();
                mPendingImageUris.addAll(extractUriFromIntent(data));
                if (xmppConnectionServiceBound)
                    for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove())
                        attachImageToConversation(getSelectedConversation(), i.next());
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_FILE || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE) {
                mPendingFileUris.clear();
                mPendingFileUris.addAll(extractUriFromIntent(data));
                if (xmppConnectionServiceBound)
                    for (Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove())
                        attachFileToConversation(getSelectedConversation(), i.next());
            } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                if (mPendingImageUris.size() == 1) {
                    Uri uri = mPendingImageUris.get(0);
                    if (xmppConnectionServiceBound) {
                        attachImageToConversation(getSelectedConversation(), uri);
                        mPendingImageUris.clear();
                    }
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(uri);
                    sendBroadcast(intent);
                } else mPendingImageUris.clear();
            } else if (requestCode == ATTACHMENT_CHOICE_LOCATION) {
                double latitude = data.getDoubleExtra("latitude", 0);
                double longitude = data.getDoubleExtra("longitude", 0);
                this.mPendingGeoUri = Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
                if (xmppConnectionServiceBound) {
                    attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
                    this.mPendingGeoUri = null;
                }
            } else if (requestCode == REQUEST_TRUST_KEYS_TEXT || requestCode == REQUEST_TRUST_KEYS_MENU) {
                this.forbidProcessingPendings = !xmppConnectionServiceBound;
                if (xmppConnectionServiceBound) {
                    mConversationFragment.onActivityResult(requestCode, resultCode, data);
                    this.mPostponedActivityResult = null;
                } else this.mPostponedActivityResult = new Pair<>(requestCode, data);
            }
        } else {
            mPendingImageUris.clear();
            mPendingFileUris.clear();
            if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP)
                mConversationFragment.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_BATTERY_OP) setNeverAskForBatteryOptimizationsAgain();
        }
    }

    private void setNeverAskForBatteryOptimizationsAgain() {
        getPreferences().edit().putBoolean("show_battery_optimization", false).commit();
    }

    private void openBatteryOptimizationDialogIfNeeded() {
        if (showBatteryOptimizationWarning() && getPreferences().getBoolean("show_battery_optimization", true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.battery_optimizations_enabled);
            builder.setMessage(R.string.battery_optimizations_enabled_dialog);
            builder.setPositiveButton(R.string.next, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (Build.VERSION.SDK_INT >= 23) { // Sonst Crash bei API <23
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        Uri uri = Uri.parse("package:" + getPackageName());
                        intent.setData(uri);
                        startActivityForResult(intent, REQUEST_BATTERY_OP);
                    }
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        setNeverAskForBatteryOptimizationsAgain();
                    }
                });
            }
            builder.create().show();
        }
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) return;
        xmppConnectionService.attachLocationToConversation(conversation, uri, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message object) {
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {
            }
        });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) return;
        final Toast prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachFileToConversation(conversation, uri, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                hidePrepareFileToast(prepareFileToast);
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message message) {
                hidePrepareFileToast(prepareFileToast);
                displayErrorDialog(errorCode);
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
            }
        });
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) return;
        final Toast prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {
                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        displayErrorDialog(error);
                    }
                });
    }

    private void hidePrepareFileToast(final Toast prepareFileToast) {
        if (prepareFileToast != null) runOnUiThread(new Runnable() {

            @Override
            public void run() {
                prepareFileToast.cancel();
            }
        });
    }

    public void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        if (swipedConversation != null) {
            if (swipedConversation.isRead()) conversationList.remove(swipedConversation);
            else listView.discardUndo();
        }
        listAdapter.notifyDataSetChanged();
    }

    public void runIntent(PendingIntent pi, int requestCode) {
        try {
            this.startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    public void encryptTextMessage(Message message) {
        xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {
                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        ConversationActivity.this.runIntent(pi, ConversationActivity.REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                    }
                });
    }

    public boolean useSendButtonToIndicateStatus() {
        return getPreferences().getBoolean("send_button_status", true);
    }

    public boolean indicateReceived() {
        return getPreferences().getBoolean("indicate_received", true);
    }

    protected boolean trustKeysIfNeeded(int requestCode) {
        return trustKeysIfNeeded(requestCode, ATTACHMENT_CHOICE_INVALID);
    }

    protected boolean trustKeysIfNeeded(int requestCode, int attachmentChoice) {
        AxolotlService axolotlService = mSelectedConversation.getAccount().getAxolotlService();
        Contact contact = mSelectedConversation.getContact();
        boolean hasUndecidedOwn = !axolotlService.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED).isEmpty();
        boolean hasUndecidedContact = !axolotlService.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED, contact).isEmpty();
        boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(mSelectedConversation).isEmpty();
        boolean hasNoTrustedKeys = axolotlService.getNumTrustedKeys(mSelectedConversation.getContact()) == 0;
        if (hasUndecidedOwn || hasUndecidedContact || hasPendingKeys || hasNoTrustedKeys) {
            axolotlService.createSessionsIfNeeded(mSelectedConversation);
            Intent intent = new Intent(getApplicationContext(), TrustKeysActivity.class);
            intent.putExtra("contact", mSelectedConversation.getContact().getJid().toBareJid().toString());
            intent.putExtra(EXTRA_ACCOUNT, mSelectedConversation.getAccount().getJid().toBareJid().toString());
            intent.putExtra("choice", attachmentChoice);
            intent.putExtra("has_no_trusted", hasNoTrustedKeys);
            startActivityForResult(intent, requestCode);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void refreshUiReal() {
        updateConversationList();
        if (conversationList.size() > 0) {
            ConversationActivity.this.mConversationFragment.updateMessages();
            updateActionBarTitle();
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate() {
        this.refreshUi();
    }

    @Override
    public void onRosterUpdate() {
        this.refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(Status status) {
        this.refreshUi();
    }

    public void unblockConversation(final Blockable conversation) {
        xmppConnectionService.sendUnblockRequest(conversation);
    }

    public boolean enterIsSend() {
        return getPreferences().getBoolean("enter_is_send", false);
    }

    @Override
    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ConversationActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public boolean highlightSelectedConversations() {
        return !isConversationsOverviewHideable() || this.conversationWasSelectedByKeyboard;
    }

    public void setMessagesLoaded() {
        if (mConversationFragment != null) mConversationFragment.setMessagesLoaded();
    }

    @Override
    public void onClick(View view) {
        final Conversation conversation = getSelectedConversation();
        Log.e("Con", "Clicked Title");
        if (conversation.getMode() == Conversation.MODE_SINGLE)
            switchToContactDetails(getSelectedConversation().getContact());
        else if (conversation.getMode() == Conversation.MODE_MULTI) {
            Intent intent = new Intent(this,
                    ConferenceDetailsActivity.class);
            intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
            intent.putExtra("uuid", getSelectedConversation().getUuid());
            startActivity(intent);
        }
    }

    public void loadAvatar(Conversation conversation, ImageView imageView) {
        if (cancelPotentialWork(conversation, imageView)) {
            final Bitmap bm = avatarService().get(conversation, getPixel(56), true);
            if (bm != null) {
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName()));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(conversation);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
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

    class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Conversation conversation = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Conversation... params) {
            return avatarService().get(params[0], getPixel(56));
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }
}