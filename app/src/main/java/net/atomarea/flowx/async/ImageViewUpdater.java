package net.atomarea.flowx.async;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import net.atomarea.flowx.data.Data;

import java.io.File;

/**
 * Created by Tom on 08.08.2016.
 */
public class ImageViewUpdater extends AsyncTask<File, Void, Bitmap> {

    private ImageView imageView;

    public ImageViewUpdater(ImageView imageView) {
        this.imageView = imageView;
    }

    @Override
    protected Bitmap doInBackground(File... params) {
        if (params.length == 0) return null;
        return BitmapFactory.decodeFile(params[0].getPath());
    }

    @Override
    protected void onPostExecute(final Bitmap bitmap) {
        super.onPostExecute(bitmap);
        imageView.setImageDrawable(new BitmapDrawable(Data.getApplicationContext().getResources(), bitmap));
        /*imageView.animate().scaleX(0).scaleY(0).setDuration(200).setInterpolator(new AccelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                imageView.setImageDrawable(new BitmapDrawable(Data.getApplicationContext().getResources(), bitmap));
                imageView.setScaleX(0);
                imageView.setScaleY(0);
                imageView.animate().setStartDelay(200).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setListener(null).start();
            }
        }).start();*/
    }
}
