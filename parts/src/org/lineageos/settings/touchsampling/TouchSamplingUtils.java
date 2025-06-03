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

public final class TouchSamplingUtils {

    private static final String TAG = "TouchSamplingUtils";
    public static final String HTSR_FILE = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";

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
}
