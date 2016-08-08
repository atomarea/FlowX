package net.atomarea.flowx.services;

import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import net.atomarea.flowx.xmpp.ServerConnection;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;

/**
 * Created by Tom on 08.08.2016.
 */
public class XmppServiceThread extends Thread {

    private XmppService xmppService;
    private boolean threadRunning;
    private ServerConnection xmppConnection;

    private String Username;
    private String Password;

    public XmppServiceThread(XmppService xmppService) {
        this.xmppService = xmppService;
        threadRunning = true;
        Username = PreferenceManager.getDefaultSharedPreferences(xmppService).getString("fxUsername", null);
        Password = PreferenceManager.getDefaultSharedPreferences(xmppService).getString("fxPassword", null); // TODO: Encrypt :)
    }

    @Override
    public void run() {

        if (Username == null || Password == null) {
            Log.i("FX", "Could not login due to no username and / or password given. Waiting 10 seconds!");
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                Log.d("FX", "Interrupted, resetting.");
            }
            Log.i("FX", "Resetting to retry login.");
            xmppService.reset();
            return;
        }

        Looper.prepare();
        xmppConnection = new ServerConnection();
        try {
            Log.i("FX", "Attempting login as " + Username);
            xmppConnection.login(Username, Password);
        } catch (SmackException | IOException | XMPPException e) {

        }

        while (threadRunning) {
            Looper.loop();
        }

        Log.i("FX", "XmppServiceThread terminating.");
    }

    public void disconnectAndStop() {
        if (xmppConnection != null) {
            xmppConnection.disconnect();
            xmppConnection.getPostHandler().post(new Runnable() {
                @Override
                public void run() {
                    threadRunning = false;
                    Log.i("FX", "Disconnected!");
                }
            });
        } else threadRunning = false;
    }

}
