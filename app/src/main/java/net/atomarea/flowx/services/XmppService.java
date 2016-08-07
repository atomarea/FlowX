package net.atomarea.flowx.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class XmppService extends Service {

    public XmppService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
