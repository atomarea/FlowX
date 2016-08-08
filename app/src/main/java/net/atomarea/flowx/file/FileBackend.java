package net.atomarea.flowx.file;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by Tom on 08.08.2016.
 */
public class FileBackend {

    public static class SaveAndRun extends AsyncTask<File, Void, Boolean> {

        private FileOperationFinishedCallback callback;
        private byte[] bytes;

        public SaveAndRun(FileOperationFinishedCallback callback, byte[] bytes) {
            this.callback = callback;
            this.bytes = bytes;
        }

        @Override
        protected Boolean doInBackground(File... params) {
            if (params.length == 0) return false;
            if (bytes == null) return false;
            File file = params[0];
            if (!file.exists())
                try {
                    file.createNewFile();
                } catch (Exception e) {
                    file.delete();
                    e.printStackTrace();
                    return false;
                }
            try {
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                out.write(bytes, 0, bytes.length);
                out.close();
                return true;
            } catch (Exception e) {
                file.delete();
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (callback != null) callback.onFileOperationFinished(aBoolean);
        }
    }

    public static File resolve(String dir, String filename) {
        File directory = new File(Environment.getExternalStorageDirectory(), "FlowX/" + dir);
        directory.mkdirs();
        return new File(directory, filename);
    }

    public interface FileOperationFinishedCallback {
        void onFileOperationFinished(boolean success);
    }

}
