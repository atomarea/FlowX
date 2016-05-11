package net.atomarea.flowx.ui;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.makeramen.roundedimageview.RoundedImageView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.chatstate.ChatState;

import java.util.ArrayList;

import github.ankushsachdeva.emojicon.EmojiconEditText;
import github.ankushsachdeva.emojicon.EmojiconTextView;
import nl.changer.audiowife.AudioWife;

/**
 * Created by Tom on 10.05.2016.
 */
public class FxUi extends FxXmppActivity implements XmppConnectionService.OnConversationUpdate {

    private static final String TAG = "FlowX (UI Main)";

    /*
     Prefixes:

     m -> Main
     d -> Data, used in some states
     */

    public static FxUi App;

    private Toolbar mToolbar;
    private Handler mHandler;
    private ScrollView mScroll;
    private LinearLayout mLayout;
    private RelativeLayout mParent;
    private LinearLayout mFooter;

    private ImageView mFxLogo;
    private RelativeLayout mFxLogoParent;

    private boolean backendConnected;

    private State mFxState;

    public Conversation dConversation;

    private boolean InStateRefresh;
    private boolean StateRefreshQueued;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fx_base_layout); // load layout from xml (base layout)

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

    @Override
    protected void refreshUiReal() {
        Log.i(TAG, "BACKEND UI REQUEST [ refreshUiReal ]");
        if (!backendConnected)
            return; // if the backend isn't connected yet, this function can't run
        // refreshFxUi(); // should happen later only if needed, not needed yet
        // [[ TODO: !! DETECT CHANGES AND APPLY ONLY IF NEEDED ]]
        refreshFxUi(mFxState, false);
    }

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

                        if (Conversation.MODE_SINGLE == tConversation.getMode() || useSubjectToIdentifyConference())
                            tTvName.setText(tConversation.getName()); // set conversation title or
                        else
                            tTvName.setText(tConversation.getJid().toBareJid().toString()); // name of user

                        if (ChatState.COMPOSING.equals(tConversation.getIncomingChatState()))
                            tTvLastMessage.setText(R.string.contact_is_typing); // contact is typing or
                        else
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

                        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }

                    ArrayList<Message> tMessages = new ArrayList<>();
                    dConversation.populateWithMessages(tMessages);

                    xmppConnectionService.sendReadMarker(dConversation); // mark as read and force refresh to server

                    for (int i = 0; i < tMessages.size(); i++) {
                        if (tMessages.size() - 30 > i)
                            continue; // show the last 30 messages... more coming soon

                        final Message tMessage = tMessages.get(i); // #finalie

                        boolean GroupConversation = tMessage.getConversation().getMode() == Conversation.MODE_MULTI && tMessage.getMergedStatus() <= Message.STATUS_RECEIVED;

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

                    if (change)
                        mFooter.addView(getLayoutInflater().inflate(R.layout.fx_msg_input, mFooter, false));
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

    @Override
    public void onBackPressed() {
        if (mFxState == State.RECENT_CONVERSATIONS) super.onBackPressed();
        else {
            refreshFxUi(State.RECENT_CONVERSATIONS, true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    public enum State {
        STARTUP, RECENT_CONVERSATIONS, SINGLE_CONVERSATION, CONTACTS, GROUPS
    }

    public void fxClickEmojiButton(View v) {

    }

    public void fxClickSendButton(View v) {
        Log.i(TAG, "SEND MESSAGE [ fxClickSendButton ]");
        FxUiHelper.sendMessage((EmojiconEditText) findViewById(R.id.message_input), dConversation, this); // let's send the text (!) message
    }
}
