/*
 * Copyright (C) 2018-2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.touchsampling;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import java.util.Set;

import org.lineageos.settings.R;

public class TouchSamplingSettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String HTSR_ENABLE_KEY = "htsr_enable";
    private static final String PER_APP_HTSR_KEY = "per_app_htsr";
    public static final String SHAREDHTSR = "SHAREDHTSR";
    public static final String HTSR_STATE = "htsr_state";

    private SwitchPreference mHTSRPreference;
    private Preference mPerAppHTSRPreference;
    private SharedPreferences mPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.htsr_settings);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        
        mHTSRPreference = (SwitchPreference) findPreference(HTSR_ENABLE_KEY);
        mPerAppHTSRPreference = findPreference(PER_APP_HTSR_KEY);
        mPrefs = getActivity().getSharedPreferences(SHAREDHTSR, Context.MODE_PRIVATE);

        // Initialize switch state
        boolean htsrEnabled = mPrefs.getBoolean(HTSR_STATE, false);
        mHTSRPreference.setChecked(htsrEnabled);
        mHTSRPreference.setOnPreferenceChangeListener(this);

        // Set up per-app HTSR preference
        mPerAppHTSRPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), TouchSamplingPerAppActivity.class);
            startActivity(intent);
            return true;
        });

        // Ensure service state matches preference
        controlTouchSamplingService(htsrEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (HTSR_ENABLE_KEY.equals(preference.getKey())) {
            boolean isEnabled = (Boolean) newValue;

            // Save state
            mPrefs.edit().putBoolean(HTSR_STATE, isEnabled).apply();

            // Control service and hardware
            controlTouchSamplingService(isEnabled);
            if (!isEnabled) {
                // Only write 0 to hardware if no per-app HTSR is enabled
                Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(getActivity());
                if (enabledApps.isEmpty()) {
                    TouchSamplingUtils.writeTouchSamplingState(0);
                }
            } else {
                TouchSamplingUtils.writeTouchSamplingState(1);
            }
        }
        return true;
    }

    private void controlTouchSamplingService(boolean enable) {
        Intent serviceIntent = new Intent(getActivity(), TouchSamplingService.class);
        Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(getActivity());
        
        if (enable || !enabledApps.isEmpty()) {
            getActivity().startService(serviceIntent);
        } else {
            getActivity().stopService(serviceIntent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }
}
