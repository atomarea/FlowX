package net.atomarea.flowx.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.services.UpdaterWebService;

public class UpdaterActivity extends Activity {

    String appURI = "";
    private UpdateReceiver receiver = null;
    private int versionCode = 0;
    private DownloadManager downloadManager;
    private long downloadReference;
    private String FileName = "update.apk";

    BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            //check if the broadcast message is for our Enqueued download
            long referenceId = intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID);
            if (downloadReference == referenceId) {
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FileName);
                //start the installation of the latest version
                Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                installIntent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(installIntent);
                UpdaterActivity.this.finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set activity
        setContentView(R.layout.activity_updater);
        TextView textView = (TextView) findViewById(R.id.updater);
        textView.setText(R.string.update_info);

        //Broadcast receiver for our Web Request
        IntentFilter filter = new IntentFilter(UpdateReceiver.PROCESS_RESPONSE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new UpdateReceiver();
        registerReceiver(receiver, filter);

        //Broadcast receiver for the download manager (download complete)
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        //check of internet is available before making a web service request
        if (isNetworkAvailable(this)) {
            Intent msgIntent = new Intent(this, UpdaterWebService.class);
            msgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            msgIntent.putExtra(UpdaterWebService.REQUEST_STRING, Config.UPDATE_URL);

            Toast.makeText(getApplicationContext(),
                    getText(R.string.checking_for_updates),
                    Toast.LENGTH_SHORT).show();
            startService(msgIntent);
        }
    }

    @Override
    public void onDestroy() {
        //unregister your receivers
        this.unregisterReceiver(receiver);
        this.unregisterReceiver(downloadReceiver);
        super.onDestroy();
        //enable touch events
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    //check for internet connection
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (int i = 0; i < info.length; i++) {
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    //show warning on back pressed
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.cancel_update)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        UpdaterActivity.this.finish();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //broadcast receiver to get notification when the web request finishes
    public class UpdateReceiver extends BroadcastReceiver {

        public static final String PROCESS_RESPONSE = "net.atomarea.flowx.intent.action.PROCESS_RESPONSE";

        @Override
        public void onReceive(Context context, Intent intent) {

            //disable touch events
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

            String responseMessage = intent.getStringExtra(UpdaterWebService.RESPONSE_MESSAGE);

            if (responseMessage == "" || responseMessage.isEmpty() || responseMessage == null) {
                Toast.makeText(getApplicationContext(),
                        getText(R.string.failed),
                        Toast.LENGTH_LONG).show();
                UpdaterActivity.this.finish();
            } else {
                //parse the JSON reponse
                JSONObject reponseObj;

                try {
                    //if the response was successful check further
                    reponseObj = new JSONObject(responseMessage);
                    boolean success = reponseObj.getBoolean("success");
                    if (success) {
                        //Overall information about the contents of a package
                        //This corresponds to all of the information collected from AndroidManifest.xml.
                        PackageInfo pInfo = null;
                        try {
                            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }
                        //get the app version Name for display
                        final String versionName = pInfo.versionName;
                        final int versionCode = pInfo.versionCode;
                        //get the latest version from the JSON string
                        int latestVersionCode = reponseObj.getInt("latestVersionCode");
                        String latestVersion = reponseObj.getString("latestVersion");
                        String filesize = reponseObj.getString("filesize");
                        String changelog = reponseObj.getString("changelog");
                        //get the lastest application URI from the JSON string
                        appURI = reponseObj.getString("appURI");
                        //check if we need to upgrade?
                        if (latestVersionCode > versionCode) {
                            //delete old downloaded version files
                            File dir = new File(getExternalFilesDir(null), Environment.DIRECTORY_DOWNLOADS);
                            if (dir.isDirectory()) {
                                String[] children = dir.list();
                                for (int i = 0; i < children.length; i++) {
                                    new File(dir, children[i]).delete();
                                }
                            }
                            //enable touch events
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                            //oh yeah we do need an upgrade, let the user know send an alert message
                            AlertDialog.Builder builder = new AlertDialog.Builder(UpdaterActivity.this);
                            builder.setCancelable(false);

                            String UpdateMessageInfo = getResources().getString(R.string.update_available);
                            builder.setMessage(String.format(UpdateMessageInfo, latestVersion, filesize, versionName, changelog))
                                    .setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
                                        //if the user agrees to upgrade
                                        public void onClick(DialogInterface dialog, int id) {
                                            //disable touch events
                                            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                                            //ask for permissions on devices >= SDK 23
                                            if (isStoragePermissionGranted()) {
                                                //start downloading the file using the download manager
                                                downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                                Uri Download_Uri = Uri.parse(appURI);
                                                DownloadManager.Request request = new DownloadManager.Request(Download_Uri);
                                                //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
                                                //request.setAllowedOverRoaming(false);
                                                request.setTitle("FlowX Update");
                                                request.setDestinationInExternalFilesDir(UpdaterActivity.this, Environment.DIRECTORY_DOWNLOADS, FileName);
                                                downloadReference = downloadManager.enqueue(request);
                                                Toast.makeText(getApplicationContext(),
                                                        getText(R.string.download_started),
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    })
                                    .setNegativeButton(R.string.remind_later, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            // User cancelled the dialog
                                            UpdaterActivity.this.finish();
                                        }
                                    });
                            //show the alert message
                            builder.create().show();
                        } else {
                            Toast.makeText(getApplicationContext(),
                                    getText(R.string.no_update_available),
                                    Toast.LENGTH_SHORT).show();
                            UpdaterActivity.this.finish();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                getText(R.string.failed),
                                Toast.LENGTH_LONG).show();
                        UpdaterActivity.this.finish();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
