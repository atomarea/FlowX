package net.atomarea.flowx.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class XmppService extends Service {

    private ServiceBinder serviceBinder = new ServiceBinder();

    private XmppServiceThread xmppServiceThread;

    private Handler serviceHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("FX", "Backend starting");
        serviceHandler = new Handler();
        xmppServiceThread = new XmppServiceThread(this);
        xmppServiceThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        xmppServiceThread.disconnectAndStop();
    }

    public boolean isLoginDataSet() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("fxUsername", null) != null;
    }

    public boolean isLoggedIn() {
        if (xmppServiceThread != null) return xmppServiceThread.isLoggedIn();
        return false;
    }

    public boolean isLoginFailed() {
        if (xmppServiceThread != null) return xmppServiceThread.isLoginFailed();
        return true;
    }

    public void reset() {
        serviceHandler.post(new Runnable() {
            @Override
            public void run() {
                if (xmppServiceThread != null) xmppServiceThread.disconnectAndStop();
                xmppServiceThread = new XmppServiceThread(XmppService.this);
                xmppServiceThread.start();
            }
        });
    }

    public class ServiceBinder extends Binder {
        public XmppService getService() {
            return XmppService.this;
        }
    }

}
