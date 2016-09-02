package net.atomarea.flowx.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.github.rtoshiro.view.video.FullscreenVideoLayout;

import java.io.File;
import java.io.IOException;

import net.atomarea.flowx.R;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ShowFullscreenMessageActivity extends Activity {

    Integer oldOrientation = getRequestedOrientation();
    PhotoView mImage;
    FullscreenVideoLayout mVideo;
    ImageView mFullscreenbutton;
    Uri mFileUri;
    File mFile;
    ImageButton mFAB;
    int height = 0;
    int width = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 1;
        getWindow().setAttributes(layout);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getActionBar().hide();
        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
        setContentView(R.layout.activity_fullscreen_message);
        mImage = (PhotoView) findViewById(R.id.message_image_view);
        mVideo = (FullscreenVideoLayout) findViewById(R.id.message_video_view);
        mFullscreenbutton = (ImageView) findViewById(R.id.vcv_img_fullscreen);
        mFAB = (ImageButton) findViewById(R.id.imageButton);
        mFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mVideo.reset();
                shareWith(mFileUri);
            }
        });
    }

    private void shareWith(Uri mFileUri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(getMimeType(mFileUri.toString()));
        share.putExtra(Intent.EXTRA_STREAM, Uri.parse(mFileUri.toString()));
        startActivity(Intent.createChooser(share, getString(R.string.share_with)));
    }

    public static String getMimeType(String path) {
        try {
            String type = null;
            String extension = path.substring(path.lastIndexOf(".") + 1, path.length());
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onStart() {
        super.onStart();
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

    private void DisplayImage(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(file.getPath()).getAbsolutePath(), options);
        height = options.outHeight;
        width = options.outWidth;
        if (width > height) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else if (width < height) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
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
        MediaMetadataRetriever retriever = new  MediaMetadataRetriever();
        retriever.setDataSource(uri.getPath());
        height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        if (width > height) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else if (width < height) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
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

    @Override
    protected void onResume() {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 1;
        mVideo.setShouldAutoplay(true);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mVideo.reset();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = -1;
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onPause();
    }

    @Override
    public void onStop () {
        mVideo.reset();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = -1;
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onStop();
    }
}