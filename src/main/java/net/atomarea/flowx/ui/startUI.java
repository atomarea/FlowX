package net.atomarea.flowx.ui;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.List;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class startUI extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {

    private static final int NeededPermissions = 1000;

    String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_ui);
        requestNeededPermissions();
    }

    @AfterPermissionGranted(NeededPermissions)
    private void requestNeededPermissions() {
        String PREFS_NAME = "FirstStart";
        SharedPreferences FirstStart = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long FirstStartTime = FirstStart.getLong("FirstStart", 0);
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, start ConversationsActivity
            Log.d(Config.LOGTAG, "All permissions granted, starting "+getString(R.string.app_name) + "(" +FirstStartTime + ")");
            Intent intent = new Intent (this, ConversationActivity.class);
            intent.putExtra("FirstStart", FirstStartTime);
            startActivity(intent);
            finish();
        } else {
            // set first start to 0 if there are permissions to request
            Log.d(Config.LOGTAG, "Requesting required permissions");
            SharedPreferences.Editor editor = FirstStart.edit();
            editor.putLong("FirstStart", 0);
            editor.commit();
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.request_permissions_message),
                    NeededPermissions, perms);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        Log.d(Config.LOGTAG, "Permissions granted:" + requestCode);
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        Log.d(Config.LOGTAG, "Permissions denied:" + requestCode);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.request_permissions_message_again))
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
