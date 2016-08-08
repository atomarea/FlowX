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
import net.atomarea.flowx.database.DatabaseHelper;
import net.atomarea.flowx.services.XmppService;
import net.atomarea.flowx.ui.activities.ChatListActivity;
import net.atomarea.flowx.ui.activities.LoginActivity;

public class StarterActivity extends AppCompatActivity {

    private XmppService.ServiceBinder xmppServiceBinder;
    private ServiceConnection xmppServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);

        DatabaseHelper.setApplicationContext(getApplicationContext());

        Data.initMain();

        xmppServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                xmppServiceBinder = (XmppService.ServiceBinder) service;
                if (!xmppServiceBinder.getService().isLoginDataSet()) {
                    startActivity(new Intent(StarterActivity.this, LoginActivity.class));
                    finish();
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

    public void onLoaded(boolean loggedIn) {
        if (loggedIn) startActivity(new Intent(this, ChatListActivity.class));
        else {
            startActivity(new Intent(StarterActivity.this, LoginActivity.class));
            if (xmppServiceBinder != null) xmppServiceBinder.getService().reset();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(xmppServiceConnection);
    }

    class LoaderTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            Data.init(StarterActivity.this);
            while (xmppServiceBinder == null || !xmppServiceBinder.getService().isLoggedIn()) {
                try {
                    Thread.sleep(1000); // wait for login
                } catch (Exception e) {
                }
            }
            if (xmppServiceBinder == null || xmppServiceBinder.getService().isLoginFailed())
                return false;
            return true;
        }

        @Override
        protected void onPostExecute(Boolean loggedIn) {
            super.onPostExecute(loggedIn);
            onLoaded(loggedIn);
        }
    }
}
