package net.atomarea.flowx.async;

import android.os.AsyncTask;
import android.widget.ImageView;

import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.file.FileBackend;

import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;

import java.io.File;

/**
 * Created by Tom on 08.08.2016.
 */
public class AvatarImageUpdater extends AsyncTask<Void, Void, VCard> {

    private String xmppAddress;
    private ImageView imageView;
    private File outputFile;

    public AvatarImageUpdater(String xmppAddress, ImageView imageView) {
        this.xmppAddress = xmppAddress;
        this.imageView = imageView;
        outputFile = FileBackend.resolve("Cache/Avatar", "avatar_" + xmppAddress.replaceAll("@", "[at]") + ".cache");
    }

    @Override
    protected VCard doInBackground(Void... params) {
        if (outputFile.exists()) return null; // TODO: Logic for cache invalidation
        try {
            return VCardManager.getInstanceFor(Data.getConnection().getRawConnection()).loadVCard(xmppAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(VCard vCard) {
        super.onPostExecute(vCard);
        if (vCard != null)
            new FileBackend.SaveAndRun(new FileBackend.FileOperationFinishedCallback() {
                @Override
                public void onFileOperationFinished(boolean success) {
                    if (success) new ImageViewUpdater(imageView).execute(outputFile);
                }
            }, vCard.getAvatar()).execute(outputFile);
        else if (outputFile.exists())
            new ImageViewUpdater(imageView).execute(outputFile);
    }
}
