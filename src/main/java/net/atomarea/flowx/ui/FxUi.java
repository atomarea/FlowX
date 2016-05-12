package net.atomarea.flowx.ui;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.makeramen.roundedimageview.RoundedImageView;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.chatstate.ChatState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import github.ankushsachdeva.emojicon.EmojiconEditText;
import github.ankushsachdeva.emojicon.EmojiconTextView;
import github.ankushsachdeva.emojicon.EmojiconsPopup;
import nl.changer.audiowife.AudioWife;

/**
 * Created by Tom on 10.05.2016.
 */
public class FxUi extends FxXmppActivity implements XmppConnectionService.OnConversationUpdate {

    private static final String TAG = "FlowX (UI Main)";

    /***
     * [[ ATTACHMENT FIELDS ]]
     ***/

    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;

    /***
     * [[ REQUEST CODES FOR ACTIVITY RESULT ]]
     ***/

    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;

    /*
     Prefixes:

     m -> Main
     d -> Data, used in some states
     */

    /***
     * [[ STATIC CONTEXT ]]
     ***/

    public static FxUi App;

    /***
     * [[ GUI ELEMENTS ]]
     ***/

    private Toolbar mToolbar;
    private Handler mHandler;
    private ScrollView mScroll;
    private LinearLayout mLayout;
    private RelativeLayout mParent;
    private LinearLayout mFooter;

    private ImageView mFxLogo;
    private RelativeLayout mFxLogoParent;

    /***
     * [[ STATE & BLOCKING VARS ]]
     ***/

    private SendButtonAction dSendButtonAction;

    private boolean backendConnected;

    private State mFxState;

    public Conversation dConversation;

    private boolean InStateRefresh;
    private boolean StateRefreshQueued;

    private EmojiconsPopup mEmojiKeyboard;

    /***
     * [[ PENDING QUEUES ]]
     ***/

    private List<Uri> mPendingImageUris = new ArrayList<>();
    private List<Uri> mPendingFileUris = new ArrayList<>();

    /***
     * [[ ENUM FOR THE UI STATE ]]
     ***/

    public enum State {
        STARTUP, RECENT_CONVERSATIONS, SINGLE_CONVERSATION, CONTACTS, GROUPS
    }

    /***
     * [[ ON CREATE METHOD, CALLED BY ANDROID ]]
     ***/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fx_base_layout); // load layout from xml (base layout)

        if (Build.VERSION.SDK_INT >= 23) {
            Log.i(TAG, "=== [ FLOWX PERMISSION CHECKER ] ===");
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 1337);
            }
        }

        Log.i(TAG, "=== [ FLOWX MAIN UI ] ===");

        InStateRefresh = false;
        StateRefreshQueued = false;

        App = this; // static context <3

        mFxState = State.STARTUP; // startup...

        backendConnected = false; // backend isn't connected at startup

        mToolbar = (Toolbar) findViewById(R.id.fx_toolbar); // find toolbar and
        mToolbar.setTitleTextColor(Color.WHITE); // set toolbar options and
        setSupportActionBar(mToolbar); // reset toolbar & attach to activity

        mHandler = new Handler(); // initialize handler for delaying requests

        mScroll = (ScrollView) findViewById(R.id.fx_main_scroll); // find other necessary view's
        mLayout = (LinearLayout) findViewById(R.id.fx_main_layout);

        mParent = (RelativeLayout) findViewById(R.id.fx_parent);

        mFooter = (LinearLayout) findViewById(R.id.fx_main_footer);

        mFxLogo = (ImageView) findViewById(R.id.fx_logo);
        mFxLogoParent = (RelativeLayout) findViewById(R.id.fx_logo_parent);

        mParent.setAlpha(0f); // set alpha value on main layout

        mFxLogo.setScaleX(0); // scale logo to 0, not visible
        mFxLogo.setScaleY(0);

        mFxLogo.animate().scaleX(1).scaleY(1).setStartDelay(100).setDuration(200).setInterpolator(new DecelerateInterpolator()).start(); // start logo animation -> 1x1 in scale, visible

        // [[ wait for backend... @ onBackendConnected() ]]
    }

    /***
     * [[ RESULT OF THE PERMISSION DIALOG, CALLED BY ANDROID ]]
     ***/

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1337) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, "We need the permission. Please enable it manually in settings.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /***
     * [[ REQUEST FOR UI REFRESH, CALLED BY BACKEND ]]
     ***/

    @Override
    protected void refreshUiReal() {
        Log.i(TAG, "BACKEND UI REQUEST [ refreshUiReal ]");
        if (!backendConnected)
            return; // if the backend isn't connected yet, this function can't run
        // refreshFxUi(); // should happen later only if needed, not needed yet
        // [[ TODO: !! DETECT CHANGES AND APPLY ONLY IF NEEDED ]]
        refreshFxUi(mFxState, false);
    }

    /***
     * [[ NOTIFY THAT BACKEND HAS CONNECTED, CALLED BY BACKEND ]]
     ***/

    @Override
    void onBackendConnected() {
        Log.i(TAG, "BACKEND CONNECTED [ onBackendConnected ]");
        backendConnected = true; // backend is now connected

        if (mFxState == State.STARTUP) // after startup
            refreshFxUi(State.RECENT_CONVERSATIONS, false); // first screen: recent message, populate before animation

        mHandler.postDelayed(new Runnable() { // delay request for animation
            @Override
            public void run() {
                mFxLogo.animate().scaleX(0).scaleY(0).setDuration(200).setInterpolator(new AccelerateInterpolator()).start();
                mParent.animate().alpha(1f).setDuration(200).start(); // properly animate the logo and the main layout to show nicely
            }
        }, 400);
        mHandler.postDelayed(new Runnable() { // delay until above animations have finished
            @Override
            public void run() {
                mFxLogoParent.setVisibility(View.GONE); // remove the "logo" layout from the shown view tree to allow inputs
            }
        }, 600);
    }

    /***
     * [[ INFLATE AND CREATE THE MENU IN ACTION BAR, CALLED BY ANDROID ]]
     ***/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    /***
     * [[ LOCAL UI REFRESH ]]
     ***/

    public void refreshFxUi(State toState, final boolean animate) {
        if (InStateRefresh) {
            StateRefreshQueued = true;
            return;
        }
        InStateRefresh = true;

        Log.i(TAG, "UI STATE REFRESH [ refreshFxUi ]");

        //State fromState = mFxState;

        final boolean change = toState != mFxState; // changed?

        if (change && animate) {
            Log.i(TAG, "ANIMATION TO ALPHA:0 [ refreshFxUi ]");
            mParent.animate().alpha(0).setDuration(200).start();
        }

        // [[ TODO: "SOFTER" WAY TO REFRESH ONLY ]]

        if (change) mFxState = toState;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long workStart = System.currentTimeMillis();

                mLayout.removeAllViews(); // bye views, won't need you anymore
                if (change) mFooter.removeAllViews(); // only remove footer when state changes

                if (State.RECENT_CONVERSATIONS == mFxState) { // cause we're showing a loading screen (or something like this), we can load everything into the ram... or at least generate everything and let android manage it properly
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.app_name); // real title: FlowX
                        getSupportActionBar().setSubtitle(null);
                        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                        getSupportActionBar().setLogo(null);
                    }

                    ArrayList<Conversation> tConversationList = new ArrayList<>();
                    xmppConnectionService.populateWithOrderedConversations(tConversationList); // load all recent conversations

                    for (Conversation tmpConversation : tConversationList) { // yay, let's fill up the ram =D
                        final Conversation tConversation = tmpConversation; // #finalize

                        View tRow = getLayoutInflater().inflate(R.layout.fx_row_recent_conversations, mLayout, false); // create the layout

                        EmojiconTextView tTvName = (EmojiconTextView) tRow.findViewById(R.id.fx_row_recent_conversations_name); // find places to fill
                        EmojiconTextView tTvLastMessage = (EmojiconTextView) tRow.findViewById(R.id.fx_row_recent_conversations_last_message);
                        TextView tTvTimestamp = (TextView) tRow.findViewById(R.id.fx_row_recent_conversations_timestamp);
                        RoundedImageView tIvPicture = (RoundedImageView) tRow.findViewById(R.id.fx_row_recent_conversations_picture);

                        if (!tConversation.isRead())
                            tTvLastMessage.setTypeface(null, Typeface.BOLD); // to see whether a conversation is read or not

                        if (Conversation.MODE_SINGLE == tConversation.getMode() || useSubjectToIdentifyConference())
                            tTvName.setText(tConversation.getName()); // set conversation title or
                        else
                            tTvName.setText(tConversation.getJid().toBareJid().toString()); // name of user

                        if (ChatState.COMPOSING.equals(tConversation.getIncomingChatState())) {
                            tTvLastMessage.setText(R.string.contact_is_typing); // contact is typing or
                            tTvLastMessage.setTypeface(null, Typeface.BOLD); // nice bold and
                            tTvLastMessage.setTextColor(ContextCompat.getColor(App, R.color.green500)); // green text
                        } else
                            tTvLastMessage.setText(tConversation.getLatestMessage().getBody()); // last message

                        FxUiHelper.loadAvatar(tConversation, tIvPicture, 66); // load the avatar from backend, 66dp width

                        tTvTimestamp.setText(UIHelper.readableTimeDifference(App, tConversation.getLatestMessage().getTimeSent())); // create and set timestamp

                        tRow.findViewById(R.id.fx_row_recent_conversations_container).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // [[ TODO: OPEN CHAT PANE WITH ANIMATION ]]
                                dConversation = tConversation; // set "current" conversation
                                refreshFxUi(State.SINGLE_CONVERSATION, true);
                            }
                        });

                        mLayout.addView(tRow); // add the row to the view tree
                    }
                } else if (State.SINGLE_CONVERSATION == mFxState) { // show a conversation, yay this will become complicated :/
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(dConversation.getName()); // real title: FlowX
                        if (dConversation.getMode() == Conversation.MODE_SINGLE) {
                            if (dConversation.getIncomingChatState() == ChatState.COMPOSING)
                                getSupportActionBar().setSubtitle(R.string.contact_is_typing);
                            else if (dConversation.getIncomingChatState() == ChatState.PAUSED)
                                getSupportActionBar().setSubtitle(R.string.contact_has_stopped_typing);
                            else
                                getSupportActionBar().setSubtitle(UIHelper.lastseen(App, dConversation.getContact().lastseen.time));
                        } else if (useSubjectToIdentifyConference())
                            getSupportActionBar().setSubtitle((dConversation.getParticipants() == null ? "-" : dConversation.getParticipants()));
                        getSupportActionBar().setLogo(FxUiHelper.loadAvatarForToolbar(dConversation));
                        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }

                    ArrayList<Message> tMessages = new ArrayList<>();
                    dConversation.populateWithMessages(tMessages);

                    xmppConnectionService.sendReadMarker(dConversation); // mark as read and force refresh to server

                    for (int i = 0; i < tMessages.size(); i++) {
                        if (tMessages.size() - 30 > i)
                            continue; // show the last 30 messages... more coming soon

                        final Message tMessage = tMessages.get(i); // #finalie

                        boolean GroupConversation = tMessage.getConversation().getMode() == Conversation.MODE_MULTI && tMessage.getMergedStatus() <= Message.STATUS_RECEIVED; // is it a group conversation

                        boolean _Error = false;

                        String _FileSize = null;
                        String _Info = null;

                        boolean _Received = false;
                        boolean _Read = false;

                        if (tMessage.getType() == Message.TYPE_IMAGE || tMessage.getType() == Message.TYPE_FILE || tMessage.getTransferable() != null) { // we have a image or a file, so do something with this
                            if (tMessage.getFileParams().size > (1024 * 1024))
                                _FileSize = tMessage.getFileParams().size / (1024 * 1024) + " MB";
                            else if (tMessage.getFileParams().size > 0)
                                _FileSize = tMessage.getFileParams().size / 1024 + " KB";
                            if (tMessage.getTransferable() != null && tMessage.getTransferable().getStatus() == Transferable.STATUS_FAILED)
                                _Error = true; // something wen't wrong in the backend, let's tell the user
                        }

                        if (tMessage.getMergedStatus() == Message.STATUS_WAITING)
                            _Info = getResources().getString(R.string.waiting);
                        if (tMessage.getMergedStatus() == Message.STATUS_UNSEND) {
                            if (tMessage.getTransferable() != null)
                                _Info = getResources().getString(R.string.sending_file, tMessage.getTransferable().getProgress());
                            else _Info = getResources().getString(R.string.sending);
                        }
                        if (tMessage.getMergedStatus() == Message.STATUS_OFFERED)
                            _Info = getResources().getString(R.string.offering);
                        if (tMessage.getMergedStatus() == Message.STATUS_SEND_RECEIVED) {
                            _Received = true;
                        }
                        if (tMessage.getMergedStatus() == Message.STATUS_SEND_DISPLAYED) {
                            _Received = true;
                            _Read = true;
                        }
                        if (tMessage.getMergedStatus() == Message.STATUS_SEND_FAILED) {
                            _Info = getResources().getString(R.string.send_failed);
                            _Error = true;
                        }
                        if (_Info == null) _Info = UIHelper.getMessageDisplayName(tMessage);

                        if (_Error) {
                            Log.e(TAG, "ERROR IN [ refreshFxUi ] STATE [ SINGLE_CONVERSATION ]");
                            continue; // error, do not show
                        }

                        View tRow = null;

                        switch (tMessage.getType()) {
                            case Message.TYPE_TEXT:
                                if (FxUiHelper.isMessageReceived(tMessage))
                                    tRow = getLayoutInflater().inflate(R.layout.fx_msg_recv_text, mLayout, false);
                                else
                                    tRow = getLayoutInflater().inflate(R.layout.fx_msg_sent_text, mLayout, false);
                                ((EmojiconTextView) tRow.findViewById(R.id.message_text)).setText(tMessage.getBody());
                                break;
                            case Message.TYPE_IMAGE:
                                tRow = getLayoutInflater().inflate(R.layout.fx_msg_sent_image, mLayout, false);
                                loadBitmap(tMessage, (ImageView) tRow.findViewById(R.id.message_image));
                                break;
                            case Message.TYPE_FILE:
                                if (tMessage.getFileParams().width > 0) { // is it an image?
                                    tRow = getLayoutInflater().inflate(R.layout.fx_msg_recv_image, mLayout, false); // then... show it
                                    loadBitmap(tMessage, (ImageView) tRow.findViewById(R.id.message_image));
                                } else if (tMessage.getMimeType() != null && tMessage.getMimeType().startsWith("audio/")) {
                                    if (FxUiHelper.isMessageReceived(tMessage))
                                        tRow = getLayoutInflater().inflate(R.layout.fx_msg_recv_audio, mLayout, false);
                                    else
                                        tRow = getLayoutInflater().inflate(R.layout.fx_msg_sent_audio, mLayout, false);
                                    AudioWife _AudioPlayer = new AudioWife();
                                    RelativeLayout _AudioPlayerViewGroup = new RelativeLayout(App);
                                    _AudioPlayer.init(App, Uri.fromFile(xmppConnectionService.getFileBackend().getFile(tMessage)));
                                    _AudioPlayer.useDefaultUi(_AudioPlayerViewGroup, getLayoutInflater());
                                    ((LinearLayout) tRow.findViewById(R.id.message_audio)).addView(_AudioPlayer.getPlayerUi());
                                }
                                break;
                        }

                        if (tRow == null)
                            continue; //hm nothing was inflated, why we should go on...

                        String _Time = UIHelper.readableTimeDifference(App, tMessage.getMergedTimeSent());

                        EmojiconTextView tMessageInfo = (EmojiconTextView) tRow.findViewById(R.id.message_information);

                        if (tMessage.getMergedStatus() <= Message.STATUS_RECEIVED) {
                            if (_FileSize != null && _Info != null && GroupConversation)
                                tMessageInfo.setText(_Time + " \u00B7 " + _FileSize + " \u00B7 " + _Info);
                            else if (_FileSize == null && _Info != null && GroupConversation)
                                tMessageInfo.setText(_Time + " \u00B7 " + _Info);
                            else if (_FileSize != null)
                                tMessageInfo.setText(_Time + " \u00B7 " + _FileSize);
                            else tMessageInfo.setText(_Time);
                        } else {
                            if (_FileSize != null && _Info != null && GroupConversation)
                                tMessageInfo.setText(_FileSize + " \u00B7 " + _Info);
                            else if (_FileSize == null && _Info != null && GroupConversation)
                                tMessageInfo.setText(_Info + " \u00B7 " + _Time);
                            else if (_FileSize != null)
                                tMessageInfo.setText(_FileSize + " \u00B7 " + _Time);
                            else tMessageInfo.setText(_Time);
                        }

                        if (tRow.findViewById(R.id.message_received) != null) if (!_Received)
                            tRow.findViewById(R.id.message_received).setVisibility(View.GONE);
                        if (tRow.findViewById(R.id.message_read) != null) if (!_Read)
                            tRow.findViewById(R.id.message_read).setVisibility(View.GONE);

                        if (tMessage.getEncryption() == Message.ENCRYPTION_NONE)
                            tRow.findViewById(R.id.message_security).setVisibility(View.GONE);

                        mLayout.addView(tRow);
                    }

                    mScroll.post(new Runnable() {
                        @Override
                        public void run() {
                            mScroll.fullScroll(ScrollView.FOCUS_DOWN); // Scroll down =3
                        }
                    });

                    if (change) {
                        mFooter.addView(getLayoutInflater().inflate(R.layout.fx_msg_input, mFooter, false));
                        EditMessage fxMessageInput = (EditMessage) findViewById(R.id.message_input);
                        mEmojiKeyboard = FxUiHelper.initEmojiKeyboard(findViewById(R.id.fx_root), App, fxMessageInput);
                        updateSendButton();
                        fxMessageInput.setKeyboardListener(new EditMessage.KeyboardListener() {
                            @Override
                            public boolean onEnterPressed() {
                                return false;
                            }

                            @Override
                            public void onTypingStarted() {
                                if (dConversation.getAccount().getStatus() == Account.State.ONLINE && dConversation.setOutgoingChatState(ChatState.COMPOSING))
                                    xmppConnectionService.sendChatState(dConversation);
                                updateSendButton();
                            }

                            @Override
                            public void onTypingStopped() {
                                if (dConversation.getAccount().getStatus() == Account.State.ONLINE && dConversation.setOutgoingChatState(ChatState.PAUSED))
                                    xmppConnectionService.sendChatState(dConversation);
                            }

                            @Override
                            public void onTextDeleted() {
                                if (dConversation.getAccount().getStatus() == Account.State.ONLINE && dConversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE))
                                    xmppConnectionService.sendChatState(dConversation);
                                updateSendButton();
                            }

                            @Override
                            public void onTextChanged() {
                                updateSendButton();
                            }

                            @Override
                            public boolean onTabPressed(boolean repeated) {
                                return false;
                            }
                        });
                    }
                }

                Log.i(TAG, "WORK DONE ( " + (System.currentTimeMillis() - workStart) + "ms ) [ refreshFxUi ]");

                if (change && animate) {
                    Log.i(TAG, "ANIMATION TO ALPHA:1 [ refreshFxUi ]");
                    mParent.animate().alpha(1).setDuration(200).start();
                }

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InStateRefresh = false;
                        if (StateRefreshQueued) {
                            StateRefreshQueued = false;
                            refreshFxUi(mFxState, false);
                        }
                    }
                }, (change && animate ? 250 : 0));
            }
        }, (change && animate ? 250 : 0));
    }

    /***
     * [[ THE BACK NAV BUTTON, CALLED BY ANDROID ]]
     ***/

    @Override
    public void onBackPressed() {
        if (mFxState == State.RECENT_CONVERSATIONS) super.onBackPressed();
        else {
            refreshFxUi(State.RECENT_CONVERSATIONS, true);
        }
    }

    /***
     * [[ AN ITEM IN MENU WAS SELECTED OR BACK ARROW WAS PRESSED, CALLED BY ANDROID ]]
     ***/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /***
     * [[ THE STATE OF THE APPLICATION WAS UPDATED, CALLED BY BACKEND ]]
     ***/

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    /***
     * [[ EMOJI BUTTON WAS CLICKED, CALLED BY UI ]]
     ***/

    public void fxClickEmojiButton(View v) {
        if (!mEmojiKeyboard.isShowing()) {
            if (mEmojiKeyboard.isKeyBoardOpen())
                mEmojiKeyboard.showAtBottom();
            else {
                findViewById(R.id.message_input).requestFocus();
                mEmojiKeyboard.showAtBottomPending();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(findViewById(R.id.message_input), InputMethodManager.SHOW_IMPLICIT);
            }
        } else mEmojiKeyboard.dismiss();
    }

    /***
     * [[ SEND BUTTON WAS CLICKED, CALLED BY UI ]]
     ***/

    public void fxClickSendButton(View v) {
        Log.i(TAG, "SEND MESSAGE [ fxClickSendButton ]");
        if (dSendButtonAction == null) dSendButtonAction = SendButtonAction.TEXT;
        if (dSendButtonAction == SendButtonAction.TAKE_PHOTO)
            attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
        if (dSendButtonAction == SendButtonAction.CHOOSE_PICTURE)
            attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
        if (dSendButtonAction == SendButtonAction.SEND_LOCATION)
            attachFile(ATTACHMENT_CHOICE_LOCATION);
        if (dSendButtonAction == SendButtonAction.RECORD_VOICE)
            attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
        if (dSendButtonAction == SendButtonAction.TEXT)
            FxUiHelper.sendMessage((EmojiconEditText) findViewById(R.id.message_input), dConversation, this); // let's send the text (!) message
    }

    /***
     * [[ SEND BUTTON UPDATE, DRAWABLE RESOURCE ]]
     ***/

    public void updateSendButton() {
        EditMessage fxMessageInput = (EditMessage) findViewById(R.id.message_input);
        if (fxMessageInput.getText().toString().trim().equals("")) { // nothing
            String setting = getPreferences().getString("quick_action", "voice");
            if (!setting.equals("none") && UIHelper.receivedLocationQuestion(dConversation.getLatestMessage())) {
                setting = "location";
            } else if (setting.equals("recent")) {
                setting = getPreferences().getString("recently_used_quick_action", "text");
            }
            switch (setting) {
                case "photo":
                    dSendButtonAction = SendButtonAction.TAKE_PHOTO;
                    break;
                case "location":
                    dSendButtonAction = SendButtonAction.SEND_LOCATION;
                    break;
                case "voice":
                    dSendButtonAction = SendButtonAction.RECORD_VOICE;
                    break;
                case "picture":
                    dSendButtonAction = SendButtonAction.CHOOSE_PICTURE;
                    break;
                default:
                    dSendButtonAction = SendButtonAction.TEXT;
                    break;
            }
        } else
            dSendButtonAction = SendButtonAction.TEXT;
        ((ImageButton) findViewById(R.id.message_send)).setImageResource(FxUiHelper.getSendButtonImageResource(dSendButtonAction));
    }

    /***
     * [[ CALLED WHEN LOCATION IS ATTACHED ]]
     ***/

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

    /***
     * [[ CALLED WHEN FILE IS ATTACHED ]]
     ***/

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) return;
        final Toast prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachFileToConversation(conversation, uri, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                FxUiHelper.hidePrepareFileToast(prepareFileToast);
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message message) {
                FxUiHelper.hidePrepareFileToast(prepareFileToast);
                displayErrorDialog(errorCode);
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
            }
        });
    }

    /***
     * [[ CALLED WHEN IMAGE IS ATTACHED ]]
     ***/

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) return;
        final Toast prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {
                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        FxUiHelper.hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        FxUiHelper.hidePrepareFileToast(prepareFileToast);
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        FxUiHelper.hidePrepareFileToast(prepareFileToast);
                        displayErrorDialog(error);
                    }
                });
    }

    /***
     * [[ ATTACH A FILE, NO MATTER WHAT ]]
     ***/

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
        final Conversation conversation = dConversation;
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
                                    App.runIntent(pi, attachmentChoice);
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
                                new DialogInterface.OnClickListener() {

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
        } else if (encryption != Message.ENCRYPTION_AXOLOTL || !FxUiHelper.axolotlTrustKeys(REQUEST_TRUST_KEYS_MENU, App))
            selectPresenceToAttachFile(attachmentChoice, encryption);
    }

    /***
     * [[ SOMETHING WILL BE ATTACHED ]]
     ***/

    protected void selectPresenceToAttachFile(final int attachmentChoice, final int encryption) {
        final Conversation conversation = dConversation;
        final Account account = conversation.getAccount();
        final FxXmppActivity.OnPresenceSelected callback = new FxXmppActivity.OnPresenceSelected() {
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
                        intent.setPackage("net.atomarea.flowx");
                        break;
                }
                if (intent.resolveActivity(getPackageManager()) != null) {
                    if (chooser) {
                        startActivityForResult(
                                Intent.createChooser(intent, getString(R.string.perform_action_with)),
                                attachmentChoice);
                    } else startActivityForResult(intent, attachmentChoice);
                } else if (fallbackPackageId != null)
                    startActivity(FxUiHelper.getInstallApkIntent(fallbackPackageId));
            }
        };
        if ((account.httpUploadAvailable() || attachmentChoice == ATTACHMENT_CHOICE_LOCATION) && encryption != Message.ENCRYPTION_OTR) {
            conversation.setNextCounterpart(null);
            callback.onPresenceSelected();
        } else selectPresence(conversation, callback);
    }

    /***
     * [[ HELPER FUNCTION ]]
     ***/

    public void runIntent(PendingIntent pi, int requestCode) {
        try {
            startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final IntentSender.SendIntentException ignored) {
        }
    }

    /***
     * [[ ACTIVITY RESULT, CALLED BY ANDROID ]]
     ***/

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_TRUST_KEYS_TEXT) {
                final String body = ((EditMessage) findViewById(R.id.message_input)).getText().toString();
                Message message = new Message(dConversation, body, dConversation.getNextEncryption());
                FxUiHelper.sendMessage(message, App);
            } else if (requestCode == REQUEST_TRUST_KEYS_MENU) {
                int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID);
                selectPresenceToAttachFile(choice, dConversation.getNextEncryption());
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                mPendingImageUris.clear();
                mPendingImageUris.addAll(FxUiHelper.extractUriFromIntent(data));
                if (xmppConnectionServiceBound)
                    for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove())
                        attachImageToConversation(dConversation, i.next());
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_FILE || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE) {
                final List<Uri> uris = FxUiHelper.extractUriFromIntent(data);
                final Conversation c = dConversation;
                final long max = c.getAccount()
                        .getXmppConnection()
                        .getFeatures()
                        .getMaxHttpUploadSize();
                final FxXmppActivity.OnPresenceSelected callback = new FxXmppActivity.OnPresenceSelected() {
                    @Override
                    public void onPresenceSelected() {
                        mPendingFileUris.clear();
                        mPendingFileUris.addAll(uris);
                        if (xmppConnectionServiceBound) {
                            for (Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
                                attachFileToConversation(c, i.next());
                            }
                        }
                    }
                };
                if (c.getMode() == Conversation.MODE_MULTI
                        || FileBackend.allFilesUnderSize(this, uris, max)
                        || c.getNextEncryption() == Message.ENCRYPTION_OTR) {
                    callback.onPresenceSelected();
                } else {
                    selectPresence(c, callback);
                }
            } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                if (mPendingImageUris.size() == 1) {
                    Uri uri = mPendingImageUris.get(0);
                    if (xmppConnectionServiceBound) {
                        attachImageToConversation(dConversation, uri);
                        mPendingImageUris.clear();
                    }
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(uri);
                    sendBroadcast(intent);
                } else mPendingImageUris.clear();
            } else if (requestCode == ATTACHMENT_CHOICE_LOCATION) {
                double latitude = data.getDoubleExtra("latitude", 0);
                double longitude = data.getDoubleExtra("longitude", 0);
                Uri mPendingGeoUri = Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
                if (xmppConnectionServiceBound)
                    attachLocationToConversation(dConversation, mPendingGeoUri);
            }
        } else {
            mPendingImageUris.clear();
            mPendingFileUris.clear();
        }
    }
}
