package net.atomarea.flowx.ui;

import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xmpp.XmppConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class SettingsActivity extends XmppActivity implements
        OnSharedPreferenceChangeListener {
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentManager fm = getFragmentManager();
        mSettingsFragment = (SettingsFragment) fm.findFragmentById(android.R.id.content);
        if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
            mSettingsFragment = new SettingsFragment();
            fm.beginTransaction().replace(android.R.id.content, mSettingsFragment).commit();
        }
    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        ListPreference resources = (ListPreference) mSettingsFragment.findPreference("resource");
        if (resources != null) {
            ArrayList<CharSequence> entries = new ArrayList<>(Arrays.asList(resources.getEntries()));
            if (!entries.contains(Build.MODEL)) {
                entries.add(0, Build.MODEL);
                resources.setEntries(entries.toArray(new CharSequence[entries.size()]));
                resources.setEntryValues(entries.toArray(new CharSequence[entries.size()]));
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences,
                                          String name) {
        if (name.equals("resource")) {
            String resource = preferences.getString("resource", "mobile")
                    .toLowerCase(Locale.US);
            if (xmppConnectionServiceBound) {
                for (Account account : xmppConnectionService.getAccounts()) {
                    if (account.setResource(resource)) {
                        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                            XmppConnection connection = account.getXmppConnection();
                            if (connection != null) {
                                connection.resetStreamId();
                            }
                            xmppConnectionService.reconnectAccountInBackground(account);
                        }
                    }
                }
            }
        } else if (name.equals("keep_foreground_service")) {
            xmppConnectionService.toggleForegroundService();
        } else if (name.equals("confirm_messages")
                || name.equals("xa_on_silent_mode")
                || name.equals("away_when_screen_off")) {
            if (xmppConnectionServiceBound) {
                if (name.equals("away_when_screen_off")) {
                    xmppConnectionService.toggleScreenEventReceiver();
                }
                xmppConnectionService.refreshAllPresences();
            }
        } else if (name.equals("dont_trust_system_cas")) {
            xmppConnectionService.updateMemorizingTrustmanager();
            reconnectAccounts();
        }

    }

    private void displayToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reconnectAccounts() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        //nothing to do. This Activity doesn't implement any listeners
    }

}
