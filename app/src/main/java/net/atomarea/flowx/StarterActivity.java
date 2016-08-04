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

    public void onLoaded(Data data) {
        if (data != null) {
            Intent ChatListActivity = new Intent(this, ChatListActivity.class);
            ChatListActivity.putExtra(Data.EXTRA_TOKEN, data);
            startActivity(ChatListActivity);
            finish();
        }
    }

    class LoaderTask extends AsyncTask<Void, Void, Data> {
        @Override
        protected Data doInBackground(Void... params) {
            return new Data(getApplicationContext());
        }

        @Override
        protected void onPostExecute(Data data) {
            super.onPostExecute(data);
            onLoaded(data);
        }
    }
}
