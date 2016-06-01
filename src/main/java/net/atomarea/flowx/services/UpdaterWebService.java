package net.atomarea.flowx.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.ui.UpdaterActivity.UpdateReceiver;

public class UpdaterWebService extends IntentService {
    public static final String REQUEST_STRING = "";
    public static final String RESPONSE_MESSAGE = "";

    private String URL = null;
    public static final int REGISTRATION_TIMEOUT = 3 * 1000;
    public static final int WAIT_TIMEOUT = 30 * 1000;

    public UpdaterWebService() {
        super("UpdaterWebService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        String requestString = intent.getStringExtra(REQUEST_STRING);
        Log.d(Config.LOGTAG, "AppUpdater: " + requestString);
        String responseMessage;
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        //get the app version Name for display
        final String versionName = pInfo.versionName;

        try {

            URL = requestString;
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();

            HttpConnectionParams.setConnectionTimeout(params, REGISTRATION_TIMEOUT);
            HttpConnectionParams.setSoTimeout(params, WAIT_TIMEOUT);
            ConnManagerParams.setTimeout(params, WAIT_TIMEOUT);

            HttpGet httpGet = new HttpGet(URL);
            httpGet.setHeader("User-Agent", getString(R.string.app_name) + " " + versionName);
            HttpResponse response = httpclient.execute(httpGet);

            StatusLine statusLine = response.getStatusLine();
            Log.d(Config.LOGTAG, "AppUpdater: HTTP Status Code: " + statusLine.getStatusCode());
            if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseMessage = out.toString();
            } else {
                Log.e(Config.LOGTAG, "AppUpdater: HTTP1:" + statusLine.getReasonPhrase());
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }

        } catch (ClientProtocolException e) {
            Log.e(Config.LOGTAG, "AppUpdater: HTTP2:" + e);
            responseMessage = "";
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "AppUpdater: HTTP3:" + e);
            responseMessage = "";
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "AppUpdater: HTTP4:" + e);
            responseMessage = "";
        }


        Intent broadcastIntent = new Intent();
        broadcastIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        broadcastIntent.setAction(UpdateReceiver.PROCESS_RESPONSE);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(RESPONSE_MESSAGE, responseMessage);
        sendBroadcast(broadcastIntent);

    }

}