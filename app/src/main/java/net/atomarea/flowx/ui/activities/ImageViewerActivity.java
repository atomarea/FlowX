package net.atomarea.flowx.ui.activities;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.data.ChatMessage;
import net.atomarea.flowx.data.Data;

public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ChatMessage chatMessage = (ChatMessage) getIntent().getSerializableExtra(Data.EXTRA_TOKEN_CHAT_MESSAGE);

        if (chatMessage.getType() != ChatMessage.Type.Image) finish();

        Data.loadBitmap(this, new Data.BitmapLoadedCallback() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap) {
                ((ImageView) findViewById(R.id.image)).setImageDrawable(new BitmapDrawable(getResources(), bitmap));
            }
        }, chatMessage);
    }

    @Override
    public void onBackPressed() {
        supportFinishAfterTransition();
    }
}
