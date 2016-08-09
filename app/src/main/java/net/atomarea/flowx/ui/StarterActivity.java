package net.atomarea.flowx.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.database.DatabaseHelper;
import net.atomarea.flowx.services.XmppService;
import net.atomarea.flowx.ui.activities.ChatListActivity;
import net.atomarea.flowx.ui.activities.LoginActivity;
import net.atomarea.flowx.ui.dialog.Dialog;

public class StarterActivity extends AppCompatActivity {

    private XmppService.ServiceBinder xmppServiceBinder;
    private ServiceConnection xmppServiceConnection;

    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);

        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Dialog.newInstance(this, R.layout.dialog_warn_storage_permission, R.string.title_proceed, R.string.action_proceed, new Dialog.OnClickListener() {
                    @Override
                    public void onPositiveButtonClicked(View rootView) {
                        startActivity(new Intent(StarterActivity.this, StarterActivity.class));
                        finish();
                    }
                }).show();
            } else {
                startActivity(new Intent(StarterActivity.this, StarterActivity.class));
                finish();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppServiceBinder == null && xmppServiceConnection != null) {
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
        if (xmppServiceConnection != null) unbindService(xmppServiceConnection);
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
