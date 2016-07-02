package net.atomarea.flowx.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import net.atomarea.flowx.R;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {

	private Callback mCallback;

	private static final String KEY_1 = "NESTED_KEY1";
	private static final String KEY_2 = "NESTED_KEY2";
	private static final String KEY_3 = "NESTED_KEY3";
	private static final String KEY_4 = "NESTED_KEY4";
	private static final String KEY_5 = "NESTED_KEY5";

	@Override
	public void onAttach(Activity activity) {

		super.onAttach(activity);

		if (activity instanceof Callback) {
			mCallback = (Callback) activity;
		} else {
			throw new IllegalStateException("Owner must implement Callback interface");
		}
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.pref);

		// add listeners for non-default actions
		Preference preference = findPreference(KEY_1);
		preference.setOnPreferenceClickListener(this);
		preference = findPreference(KEY_2);
		preference.setOnPreferenceClickListener(this);
		preference = findPreference(KEY_3);
		preference.setOnPreferenceClickListener(this);
		preference = findPreference(KEY_4);
		preference.setOnPreferenceClickListener(this);
		preference = findPreference(KEY_5);
		preference.setOnPreferenceClickListener(this);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		// here you should use the same keys as you used in the xml-file
		if (preference.getKey().equals(KEY_1)) {
			mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_1_KEY);
		}

		if (preference.getKey().equals(KEY_2)) {
			mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_2_KEY);
		}
		if (preference.getKey().equals(KEY_3)) {
			mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_3_KEY);
		}
		if (preference.getKey().equals(KEY_4)) {
			mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_4_KEY);
		}
		if (preference.getKey().equals(KEY_5)) {
			mCallback.onNestedPreferenceSelected(NestedPreferenceFragment.NESTED_SCREEN_5_KEY);
		}


		return false;
	}

	public interface Callback {
		public void onNestedPreferenceSelected(int key);
	}
}