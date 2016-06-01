package net.atomarea.flowx.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.github.rtoshiro.view.video.FullscreenVideoLayout;

import net.atomarea.flowx.R;

import java.io.File;
import java.io.IOException;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ShowFullscreenMessageActivity extends Activity {

    PhotoView mImage;
    FullscreenVideoLayout mVideo;
    ImageView mFullscreenbutton;
    Uri mFileUri;
    File mFile;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 1;
        getWindow().setAttributes(layout);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getWindow().addFlags(layout.FLAG_KEEP_SCREEN_ON);
        getActionBar().hide();
        setContentView(R.layout.activity_fullscreen_message);
        mImage = (PhotoView) findViewById(R.id.message_image_view);
        mVideo = (FullscreenVideoLayout) findViewById(R.id.message_video_view);
        mFullscreenbutton = (ImageView) findViewById(R.id.vcv_img_fullscreen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();

        if (intent != null) {
            if (intent.hasExtra("image")) {
                mFileUri = intent.getParcelableExtra("image");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null) {
                    DisplayImage(mFile);
                } else {
                    Toast.makeText(ShowFullscreenMessageActivity.this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                }
            } else if (intent.hasExtra("video")) {
                mFileUri = intent.getParcelableExtra("video");
                if (mFileUri != null) {
                    DisplayVideo(mFileUri);
                } else {
                    Toast.makeText(ShowFullscreenMessageActivity.this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void DisplayImage(File file) {
        final PhotoViewAttacher mAttacher = new PhotoViewAttacher(mImage);
        mImage.setVisibility(View.VISIBLE);
        try {
            Glide.with(this)
                    .load(file)
                    .asBitmap()
                    .into(new BitmapImageViewTarget(mImage) {
                        @Override
                        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                            super.onResourceReady(resource, glideAnimation);
                            mAttacher.update();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void DisplayVideo(Uri uri) {
        try {
            mVideo.setVisibility(View.VISIBLE);
            mVideo.setVideoURI(uri);
            mFullscreenbutton.setVisibility(View.INVISIBLE);
            mVideo.setShouldAutoplay(true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void onStop () {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = -1;
        getWindow().setAttributes(layout);
        getWindow().clearFlags(layout.FLAG_KEEP_SCREEN_ON);
        super.onStop();
    }
}