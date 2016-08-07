package net.atomarea.flowx.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.activities.ChatListActivity;
import net.atomarea.flowx.xmpp.ServerConnection;

import java.util.Calendar;

public class StarterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);

        Data.initMain();

        new LoaderTask().execute();
    }

    public void onLoaded() {
        startActivity(new Intent(this, ChatListActivity.class));
        finish();
    }

    class LoaderTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Data.init(StarterActivity.this);
            ServerConnection serverConnection = new ServerConnection();
            try {
                //do login
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            onLoaded();
        }
    }
}
