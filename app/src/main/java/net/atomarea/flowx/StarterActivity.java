package net.atomarea.flowx;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import net.atomarea.flowx.activities.ChatListActivity;
import net.atomarea.flowx.data.Data;

public class StarterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);

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
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            onLoaded();
        }
    }
}
