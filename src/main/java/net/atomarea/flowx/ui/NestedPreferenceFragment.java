package net.atomarea.flowx.ui;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import net.atomarea.flowx.R;

public class NestedPreferenceFragment extends PreferenceFragment {

    public static final int NESTED_SCREEN_1_KEY = 1;
    public static final int NESTED_SCREEN_2_KEY = 2;
    public static final int NESTED_SCREEN_3_KEY = 3;
    public static final int NESTED_SCREEN_4_KEY = 4;
    public static final int NESTED_SCREEN_5_KEY = 5;



    private static final String TAG_KEY = "NESTED_KEY";

    public static NestedPreferenceFragment newInstance(int key) {
        NestedPreferenceFragment fragment = new NestedPreferenceFragment();
        // supply arguments to bundle.
        Bundle args = new Bundle();
        args.putInt(TAG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        checkPreferenceResource();
    }

    private void checkPreferenceResource() {
        int key = getArguments().getInt(TAG_KEY);
        // Load the preferences from an XML resource
        switch (key) {
            case NESTED_SCREEN_1_KEY:
                addPreferencesFromResource(R.xml.preference_notify);
                break;

            case NESTED_SCREEN_2_KEY:
                addPreferencesFromResource(R.xml.preference_input);
                break;
            case NESTED_SCREEN_3_KEY:
                addPreferencesFromResource(R.xml.preference_privacy);
                break;
            case NESTED_SCREEN_4_KEY:
                addPreferencesFromResource(R.xml.preference_advanced);
                break;
            case NESTED_SCREEN_5_KEY:
                addPreferencesFromResource(R.xml.preference_about);
                break;
            default:
                break;
        }
    }

}