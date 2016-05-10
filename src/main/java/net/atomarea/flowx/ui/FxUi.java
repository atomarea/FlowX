package net.atomarea.flowx.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.chatstate.ChatState;

import java.util.ArrayList;

import github.ankushsachdeva.emojicon.EmojiconTextView;

/**
 * Created by Tom on 10.05.2016.
 */
public class FxUi extends FxXmppActivity {

    private static final String TAG = "FlowX (UI Main)";

    public static FxUi App;

    private Toolbar mToolbar;
    private Handler mHandler;
    private ScrollView mScroll;
    private LinearLayout mLayout;
    private LinearLayout mParent;

    private ImageView mFxLogo;
    private RelativeLayout mFxLogoParent;

    private boolean backendConnected;

    private State mFxState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fx_base_layout); // load layout from xml (base layout)

        App = this; // static context <3

        mFxState = State.STARTUP; // startup...

        backendConnected = false; // backend isn't connected at startup

        mToolbar = (Toolbar) findViewById(R.id.fx_toolbar); // find toolbar and
        mToolbar.setTitleTextColor(Color.WHITE); // set toolbar options and
        setSupportActionBar(mToolbar); // reset toolbar & attach to activity

        mHandler = new Handler(); // initialize handler for delaying requests

        mScroll = (ScrollView) findViewById(R.id.fx_main_scroll); // find other necessary view's
        mLayout = (LinearLayout) findViewById(R.id.fx_main_layout);

        mParent = (LinearLayout) findViewById(R.id.fx_parent);

        mFxLogo = (ImageView) findViewById(R.id.fx_logo);
        mFxLogoParent = (RelativeLayout) findViewById(R.id.fx_logo_parent);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(false); // prevent "back" arrow from showing

        mParent.setAlpha(0f); // set alpha value on main layout

        mFxLogo.setScaleX(0); // scale logo to 0, not visible
        mFxLogo.setScaleY(0);

        mFxLogo.animate().scaleX(1).scaleY(1).setStartDelay(100).setDuration(200).setInterpolator(new DecelerateInterpolator()).start(); // start logo animation -> 1x1 in scale, visible

        // [[ wait for backend... @ onBackendConnected() ]]
    }

    @Override
    protected void refreshUiReal() {
        Log.i(TAG, "backend requested ui refresh");
        if (!backendConnected)
            return; // if the backend isn't connected yet, this function can't run
        // refreshFxUi(); // should happen later only if needed, not needed yet
    }

    @Override
    void onBackendConnected() {
        Log.i(TAG, "backend connected");
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

    public void refreshFxUi(State toState, boolean animate) {
        Log.i(TAG, "refresh fxui state");

        State fromState = mFxState;

        boolean change = toState != mFxState;

        if (change && animate) {
            // [[ TODO: ANIMATION CODE HERE ]]
        }

        // [[ TODO: "SOFTER" WAY TO REFRESH ONLY ]]

        if (change) mFxState = toState;

        mLayout.removeAllViews(); // bye views, won't need you anymore

        if (State.RECENT_CONVERSATIONS == mFxState) { // cause we're showing a loading screen (or something like this), we can load everything into the ram... or at least generate everything and let android manage it properly
            ArrayList<Conversation> tConversationList = new ArrayList<>();
            xmppConnectionService.populateWithOrderedConversations(tConversationList); // load all recent conversations

            for (Conversation tConversation : tConversationList) { // yay, let's fill up the ram =D
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

                FxUiHelper.loadAvatar(tConversation, tIvPicture);

                tTvTimestamp.setText(UIHelper.readableTimeDifference(this, tConversation.getLatestMessage().getTimeSent()));

                mLayout.addView(tRow); // add the row to the view tree
            }
        }

        if (change && animate) {
            // [[ TODO: ANIMATION CODE HERE ]]
        }
    }

    public enum State {
        STARTUP, RECENT_CONVERSATIONS, SINGLE_CONVERSATION, CONTACTS, GROUPS
    }
}
