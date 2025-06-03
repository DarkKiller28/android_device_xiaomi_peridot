/*
 * Copyright (C) 2024 The LineageOS Project
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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

public class TouchSamplingService extends Service {
    private static final String TAG = "TouchSamplingService";

    private BroadcastReceiver mScreenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TouchSamplingService started");

        // Register receiver for screen state changes
        mScreenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action) ||
                    Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.d(TAG, "Screen state changed, reapplying touch sampling rate");
                    applyTouchSamplingRate();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenStateReceiver, filter);

        // Apply initial touch sampling rate
        applyTouchSamplingRate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TouchSamplingService onStartCommand");
        applyTouchSamplingRate();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TouchSamplingService stopped");

        // Disable touch sampling when service stops
        TouchSamplingUtils.writeTouchSamplingState(0);

        // Unregister receiver
        if (mScreenStateReceiver != null) {
            try {
                unregisterReceiver(mScreenStateReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyTouchSamplingRate() {
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        boolean htsrEnabled = sharedPref.getBoolean(TouchSamplingSettingsFragment.HTSR_STATE, false);
        
        int desiredState = htsrEnabled ? 1 : 0;
        int currentState = TouchSamplingUtils.readTouchSamplingState();
        
        if (currentState != desiredState) {
            Log.d(TAG, "Applying touch sampling rate: " + desiredState);
            TouchSamplingUtils.writeTouchSamplingState(desiredState);
        }
    }
}
