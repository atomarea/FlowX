package net.atomarea.flowx;

import android.graphics.drawable.BitmapDrawable;

import java.util.HashMap;

/**
 * Created by Tom on 08.08.2016.
 */
public class Memory {

    private static HashMap<String, BitmapDrawable> avatarDrawable;

    static {
        if (avatarDrawable == null) avatarDrawable = new HashMap<>();
        else destroy();
    }

    public static HashMap<String, BitmapDrawable> getAvatarDrawable() {
        return avatarDrawable;
    }

    public static void destroy() {
        for (BitmapDrawable bd : avatarDrawable.values()) bd.getBitmap().recycle();
        avatarDrawable.clear();
    }
}
