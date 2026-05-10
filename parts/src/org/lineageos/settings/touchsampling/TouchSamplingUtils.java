/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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
import android.content.SharedPreferences;
import android.util.Log;

import org.lineageos.settings.utils.FileUtils;

import java.util.HashSet;
import java.util.Set;

public final class TouchSamplingUtils {

    private static final String TAG = "TouchSamplingUtils";
    public static final String HTSR_FILE = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";

    // Per-app HTSR preferences
    public static final String PER_APP_HTSR_ENABLED_APPS = "per_app_htsr_enabled_apps";

    public static void restoreSamplingValue(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        boolean htsrState = sharedPref.getBoolean(TouchSamplingSettingsFragment.HTSR_STATE, false);
        writeTouchSamplingState(htsrState ? 1 : 0);
    }

    public static boolean writeTouchSamplingState(int state) {
        boolean success = FileUtils.writeOneLine(HTSR_FILE, String.valueOf(state));
        if (!success) {
            Log.e(TAG, "Failed to write touch sampling state: " + state);
        }
        return success;
    }

    public static int readTouchSamplingState() {
        String currentState = FileUtils.readOneLine(HTSR_FILE);
        if (currentState != null) {
            try {
                return Integer.parseInt(currentState.trim());
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid touch sampling state format: " + currentState);
            }
        }
        return 0; // Default to disabled
    }

    // Per-app HTSR methods
    public static boolean isPerAppHtsrEnabled(Context context, String packageName) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        Set<String> enabledApps = sharedPref.getStringSet(PER_APP_HTSR_ENABLED_APPS, new HashSet<>());
        return enabledApps.contains(packageName);
    }

    public static void setPerAppHtsrEnabled(Context context, String packageName, boolean enabled) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        Set<String> enabledApps = new HashSet<>(sharedPref.getStringSet(PER_APP_HTSR_ENABLED_APPS, new HashSet<>()));
        
        if (enabled) {
            enabledApps.add(packageName);
        } else {
            enabledApps.remove(packageName);
        }
        
        sharedPref.edit().putStringSet(PER_APP_HTSR_ENABLED_APPS, enabledApps).apply();
    }

    public static Set<String> getPerAppHtsrEnabledApps(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        return new HashSet<>(sharedPref.getStringSet(PER_APP_HTSR_ENABLED_APPS, new HashSet<>()));
    }
}
