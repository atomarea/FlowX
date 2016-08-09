package net.atomarea.flowx.async;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import net.atomarea.flowx.Memory;
import net.atomarea.flowx.data.Data;

import java.io.File;

/**
 * Created by Tom on 08.08.2016.
 */
public class ImageViewUpdater extends AsyncTask<File, Void, Bitmap> {

    private String xmppAddress;
    private ImageView imageView;
    private boolean fullSource;

    public ImageViewUpdater(String xmppAddress, ImageView imageView, boolean fullSource) {
        this.xmppAddress = xmppAddress;
        this.imageView = imageView;
        this.fullSource = fullSource;
    }

    @Override
    protected Bitmap doInBackground(File... params) {
        if (params.length == 0) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(params[0].getPath());
        if (bitmap == null) return null;
        if (fullSource) return bitmap;
        return Bitmap.createScaledBitmap(bitmap, 120, 120, false);
    }

    @Override
    protected void onPostExecute(final Bitmap bitmap) {
        super.onPostExecute(bitmap);
        if (bitmap != null) {
            BitmapDrawable bd = new BitmapDrawable(Data.getApplicationContext().getResources(), bitmap);
            if (!fullSource) Memory.getAvatarDrawable().put(xmppAddress, bd);
            imageView.setImageDrawable(bd);
        }
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
