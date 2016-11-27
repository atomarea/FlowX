package net.atomarea.flowx.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.github.rtoshiro.view.video.FullscreenVideoLayout;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ShowFullscreenMessageActivity extends Activity {

    Integer oldOrientation;
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
        oldOrientation = getRequestedOrientation();

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
            if (intent.hasExtra("abc_profile")) {
                mImage.setImageDrawable(new BitmapDrawable(getResources(), ContactDetailsActivity.profilePicture));
                mImage.setVisibility(View.VISIBLE);
                mFAB.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/FlowX/Profile");
                            dir.mkdirs();
                            File file = new File(dir, System.currentTimeMillis() + ".png");
                            FileOutputStream out = new FileOutputStream(file);
                            ContactDetailsActivity.profilePicture.compress(Bitmap.CompressFormat.PNG, 80, out);
                            out.flush();
                            out.close();
                            Toast.makeText(ShowFullscreenMessageActivity.this, R.string.save, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(ShowFullscreenMessageActivity.this, R.string.error, Toast.LENGTH_LONG).show();
                            Log.e("ERR", "ERR", e);
                        }
                    }
                });
            } else if (intent.hasExtra("image")) {
                mFileUri = intent.getParcelableExtra("image");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        DisplayImage(mFile);
                    } catch (Exception e) {
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        Toast.makeText(ShowFullscreenMessageActivity.this, getString(R.string.error_file_corrupt), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Toast.makeText(ShowFullscreenMessageActivity.this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                }
            } else if (intent.hasExtra("video")) {
                mFileUri = intent.getParcelableExtra("video");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        DisplayVideo(mFileUri);
                    } catch (Exception e) {
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        Toast.makeText(ShowFullscreenMessageActivity.this, getString(R.string.error_file_corrupt), Toast.LENGTH_SHORT).show();
                        finish();
                    }
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
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
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
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mVideo.setShouldAutoplay(true);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mVideo.reset();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onPause();
    }

    @Override
    public void onStop() {
        mVideo.reset();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onStop();
    }
}