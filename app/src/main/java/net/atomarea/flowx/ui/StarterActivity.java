package net.atomarea.flowx.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.services.XmppService;
import net.atomarea.flowx.ui.activities.ChatListActivity;

public class StarterActivity extends AppCompatActivity {

    private XmppService.ServiceBinder xmppServiceBinder;
    private ServiceConnection xmppServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);

        Data.initMain();

        xmppServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                xmppServiceBinder = (XmppService.ServiceBinder) service;
                if (!xmppServiceBinder.getService().isLoginDataSet()) {

                } else new LoaderTask().execute();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                xmppServiceBinder = null;
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppServiceBinder == null) {
            Intent xmppIntent = new Intent(this, XmppService.class);
            bindService(xmppIntent, xmppServiceConnection, Context.BIND_AUTO_CREATE);
            startService(xmppIntent);
        }
    }

    public void onLoaded() {
        startActivity(new Intent(this, ChatListActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(xmppServiceConnection);
    }

    class LoaderTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Data.init(StarterActivity.this);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            onLoaded();
        }
    }
}
