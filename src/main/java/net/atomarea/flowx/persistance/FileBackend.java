package net.atomarea.flowx.persistance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.webkit.MimeTypeMap;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.DownloadableFile;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.ExifHelper;
import net.atomarea.flowx.utils.FileUtils;
import net.atomarea.flowx.xmpp.pep.Avatar;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileBackend {

    private static int IMAGE_SIZE = 1920;

    private final SimpleDateFormat imageDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);

    private XmppConnectionService mXmppConnectionService;

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public DownloadableFile getFile(Message message) {
        return getFile(message, true);
    }

    public DownloadableFile getFile(Message message, boolean decrypted) {
        final boolean encrypted = !decrypted
                && (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED);
        final DownloadableFile file;
        String path = message.getRelativeFilePath();
        if (path == null) {
            path = message.getUuid();
        }
        if (path.startsWith("/")) {
            file = new DownloadableFile(path);
        } else {
            String mime = message.getMimeType();
            if (mime != null && mime.startsWith("image")) {
                file = new DownloadableFile(getConversationsImageDirectory() + path);
            } else {
                file = new DownloadableFile(getConversationsFileDirectory() + path);
            }
        }
        if (encrypted) {
            return new DownloadableFile(getConversationsFileDirectory() + file.getName() + ".pgp");
        } else {
            return file;
        }
    }

    public static String getConversationsFileDirectory() {
        return  Environment.getExternalStorageDirectory().getAbsolutePath()+"/FlowX/";
    }

    public static String getConversationsImageDirectory() {
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath()
                + "/FlowX/";
    }

    public Bitmap resize(Bitmap originalBitmap, int size) {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (Math.max(w, h) > size) {
            int scalledW;
            int scalledH;
            if (w <= h) {
                scalledW = (int) (w / ((double) h / size));
                scalledH = size;
            } else {
                scalledW = size;
                scalledH = (int) (h / ((double) w / size));
            }
            Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
            if (originalBitmap != null && !originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    public Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix mtx = new Matrix();
        mtx.postRotate(degree);
        Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    public boolean useImageAsIs(Uri uri) {
        String path = getOriginalPath(uri);
        if (path == null) {
            return false;
        }
        File file = new File(path);
        long size = file.length();
        if (size == 0 || size >= 524288 ) {
            return false;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(uri), null, options);
            if (options == null || options.outMimeType == null || options.outHeight <= 0 || options.outWidth <= 0) {
                return false;
            }
            return (options.outWidth <= Config.IMAGE_SIZE && options.outHeight <= Config.IMAGE_SIZE && options.outMimeType.contains(Config.IMAGE_FORMAT.name().toLowerCase()));
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public String getOriginalPath(Uri uri) {
        Log.d(Config.LOGTAG,"get original path for uri: "+uri.toString());
        return FileUtils.getPath(mXmppConnectionService,uri);
    }

    public DownloadableFile copyFileToPrivateStorage(Message message, Uri uri) throws FileCopyException {
        Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage");
        String mime = mXmppConnectionService.getContentResolver().getType(uri);
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        message.setRelativeFilePath(message.getUuid() + "." + extension);
        DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
        file.getParentFile().mkdirs();
        OutputStream os = null;
        InputStream is = null;
        try {
            file.createNewFile();
            os = new FileOutputStream(file);
            is = mXmppConnectionService.getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
        } catch(FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileCopyException(R.string.error_io_exception);
        } finally {
            close(os);
            close(is);
        }
        Log.d(Config.LOGTAG, "output file name " + mXmppConnectionService.getFileBackend().getFile(message));
        return file;
    }

    public DownloadableFile copyImageToPrivateStorage(Message message, Uri image)
            throws FileCopyException {
        return this.copyImageToPrivateStorage(message, image, 0);
    }

    private DownloadableFile copyImageToPrivateStorage(Message message,Uri image, int sampleSize) throws FileCopyException {
        switch(Config.IMAGE_FORMAT) {
            case JPEG:
                message.setRelativeFilePath(message.getUuid()+".jpg");
                break;
            case PNG:
                message.setRelativeFilePath(message.getUuid()+".png");
                break;
            case WEBP:
                message.setRelativeFilePath(message.getUuid()+".webp");
                break;
        }
        DownloadableFile file = getFile(message);
        file.getParentFile().mkdirs();
        InputStream is = null;
        OutputStream os = null;
        try {
            file.createNewFile();
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            os = new FileOutputStream(file);

            Bitmap originalBitmap;
            BitmapFactory.Options options = new BitmapFactory.Options();
            int inSampleSize = (int) Math.pow(2, sampleSize);
            Log.d(Config.LOGTAG, "reading bitmap with sample size " + inSampleSize);
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new FileCopyException(R.string.error_not_an_image_file);
            }
            Bitmap scaledBitmap = resize(originalBitmap, Config.IMAGE_SIZE);
            int rotation = getRotation(image);
            if (rotation > 0) {
                scaledBitmap = rotate(scaledBitmap, rotation);
            }
            boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, Config.IMAGE_QUALITY, os);
            if (!success) {
                throw new FileCopyException(R.string.error_compressing_image);
            }
            os.flush();
            long size = file.getSize();
            int width = scaledBitmap.getWidth();
            int height = scaledBitmap.getHeight();
            message.setBody(Long.toString(size) + '|' + width + '|' + height);
            return file;
        } catch (FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileCopyException(R.string.error_io_exception);
        } catch (SecurityException e) {
            throw new FileCopyException(R.string.error_security_exception_during_image_copy);
        } catch (OutOfMemoryError e) {
            ++sampleSize;
            if (sampleSize <= 3) {
                return copyImageToPrivateStorage(message, image, sampleSize);
            } else {
                throw new FileCopyException(R.string.error_out_of_memory);
            }
        } catch (NullPointerException e) {
            throw new FileCopyException(R.string.error_io_exception);
        } finally {
            close(os);
            close(is);
        }
    }

    private int getRotation(File file) {
        return getRotation(Uri.parse("file://"+file.getAbsolutePath()));
    }

    private int getRotation(Uri image) {
        InputStream is = null;
        try {
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            return ExifHelper.getOrientation(is);
        } catch (FileNotFoundException e) {
            return 0;
        } finally {
            close(is);
        }
    }

    public Bitmap getThumbnail(Message message, int size, boolean cacheOnly)
            throws FileNotFoundException {
        Bitmap thumbnail = mXmppConnectionService.getBitmapCache().get(message.getUuid());
        if ((thumbnail == null) && (!cacheOnly)) {
            File file = getFile(message);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(file, size);
            Bitmap fullsize = BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            if (fullsize == null) {
                throw new FileNotFoundException();
            }
            thumbnail = resize(fullsize, size);
            int rotation = getRotation(file);
            if (rotation > 0) {
                thumbnail = rotate(thumbnail, rotation);
            }
            this.mXmppConnectionService.getBitmapCache().put(message.getUuid(),thumbnail);
        }
        return thumbnail;
    }

    public Uri getTakePhotoUri() {
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
        pathBuilder.append('/');
        pathBuilder.append("Camera");
        pathBuilder.append('/');
        pathBuilder.append("IMG_" + this.imageDateFormat.format(new Date()) + ".jpg");
        Uri uri = Uri.parse("file://" + pathBuilder.toString());
        File file = new File(uri.toString());
        file.getParentFile().mkdirs();
        return uri;
    }

    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
        try {
            Avatar avatar = new Avatar();
            Bitmap bm = cropCenterSquare(image, size);
            if (bm == null) {
                return null;
            }
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputSttream = new Base64OutputStream(
                    mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(
                    mBase64OutputSttream, digest);
            if (!bm.compress(format, 75, mDigestOutputStream)) {
                return null;
            }
            mDigestOutputStream.flush();
            mDigestOutputStream.close();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = new String(mByteArrayOutputStream.toByteArray());
            return avatar;
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public boolean isAvatarCached(Avatar avatar) {
        File file = new File(getAvatarPath(avatar.getFilename()));
        return file.exists();
    }

    public boolean save(Avatar avatar) {
        File file;
        if (isAvatarCached(avatar)) {
            file = new File(getAvatarPath(avatar.getFilename()));
        } else {
            String filename = getAvatarPath(avatar.getFilename());
            file = new File(filename + ".tmp");
            file.getParentFile().mkdirs();
            OutputStream os = null;
            try {
                file.createNewFile();
                os = new FileOutputStream(file);
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.reset();
                DigestOutputStream mDigestOutputStream = new DigestOutputStream(os, digest);
                mDigestOutputStream.write(avatar.getImageAsBytes());
                mDigestOutputStream.flush();
                mDigestOutputStream.close();
                String sha1sum = CryptoHelper.bytesToHex(digest.digest());
                if (sha1sum.equals(avatar.sha1sum)) {
                    file.renameTo(new File(filename));
                } else {
                    Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
                    file.delete();
                    return false;
                }
            } catch (IllegalArgumentException | IOException | NoSuchAlgorithmException e) {
                return false;
            } finally {
                close(os);
            }
        }
        avatar.size = file.length();
        return true;
    }

    public String getAvatarPath(String avatar) {
        return mXmppConnectionService.getFilesDir().getAbsolutePath()+ "/avatars/" + avatar;
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    public Bitmap cropCenterSquare(Uri image, int size) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                int rotation = getRotation(image);
                if (rotation > 0) {
                    input = rotate(input, rotation);
                }
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image,Math.max(newHeight, newWidth));
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap source = BitmapFactory.decodeStream(is, null, options);
            if (source == null) {
                return null;
            }
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float xScale = (float) newWidth / sourceWidth;
            float yScale = (float) newHeight / sourceHeight;
            float scale = Math.max(xScale, yScale);
            float scaledWidth = scale * sourceWidth;
            float scaledHeight = scale * sourceHeight;
            float left = (newWidth - scaledWidth) / 2;
            float top = (newHeight - scaledHeight) / 2;

            RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, null);
            if (source != null && !source.isRecycled()) {
                source.recycle();
            }
            return dest;
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenterSquare(Bitmap input, int size) {
        int w = input.getWidth();
        int h = input.getHeight();

        float scale = Math.max((float) size / h, (float) size / w);

        float outWidth = scale * w;
        float outHeight = scale * h;
        float left = (size - outWidth) / 2;
        float top = (size - outHeight) / 2;
        RectF target = new RectF(left, top, left + outWidth, top + outHeight);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, null);
        if (input != null && !input.isRecycled()) {
            input.recycle();
        }
        return output;
    }

    private int calcSampleSize(Uri image, int size) throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
        return calcSampleSize(options, size);
    }

    private static int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }

    public static int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size
                    && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public Uri getJingleFileUri(Message message) {
        File file = getFile(message);
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    public void updateFileParams(Message message) {
        updateFileParams(message,null);
    }

    public void updateFileParams(Message message, URL url) {
        DownloadableFile file = getFile(message);
        if (message.getType() == Message.TYPE_IMAGE || file.getMimeType().startsWith("image/")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int rotation = getRotation(file);
            boolean rotated = rotation == 90 || rotation == 270;
            int imageHeight = rotated ? options.outWidth : options.outHeight;
            int imageWidth = rotated ? options.outHeight : options.outWidth;
            if (url == null) {
                message.setBody(Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
            } else {
                message.setBody(url.toString()+"|"+Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
            }
        } else {
            if (url != null) {
                message.setBody(url.toString()+"|"+Long.toString(file.getSize()));
            } else {
                message.setBody(Long.toString(file.getSize()));
            }
        }

    }

    public class FileCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int resId;

        public FileCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }

    public Bitmap getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }
        Bitmap bm = cropCenter(getAvatarUri(avatar), size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    public boolean isFileAvailable(Message message) {
        return getFile(message).exists();
    }

    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
            }
        }
    }

    public static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }
}
