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

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.Set;

public class TouchSamplingService extends Service {
    private static final String TAG = "TouchSamplingService";
    private static final int CHECK_INTERVAL = 1000; // Check every 1 second

    private BroadcastReceiver mScreenStateReceiver;
    private Handler mHandler;
    private Runnable mAppCheckRunnable;
    private ActivityManager mActivityManager;
    private String mCurrentApp = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TouchSamplingService started");

        mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());

        // Register receiver for screen state changes
        mScreenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action) ||
                    Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.d(TAG, "Screen state changed, reapplying touch sampling rate");
                    startAppMonitoring();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "Screen off, stopping app monitoring");
                    stopAppMonitoring();
                    TouchSamplingUtils.writeTouchSamplingState(0);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter);

        // Start monitoring
        startAppMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TouchSamplingService onStartCommand");
        startAppMonitoring();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TouchSamplingService stopped");

        // Stop monitoring
        stopAppMonitoring();

        // Only disable touch sampling if neither global nor per-app HTSR is enabled
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        boolean globalHtsrEnabled = sharedPref.getBoolean(
                TouchSamplingSettingsFragment.HTSR_STATE, false);
        Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(this);
        if (!globalHtsrEnabled && enabledApps.isEmpty()) {
            TouchSamplingUtils.writeTouchSamplingState(0);
        }

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

    private void startAppMonitoring() {
        stopAppMonitoring(); // Stop any existing monitoring
        
        mAppCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkCurrentApp();
                mHandler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        
        mHandler.post(mAppCheckRunnable);
    }

    private void stopAppMonitoring() {
        if (mHandler != null && mAppCheckRunnable != null) {
            mHandler.removeCallbacks(mAppCheckRunnable);
        }
    }

    private void checkCurrentApp() {
        try {
            List<ActivityManager.RunningTaskInfo> tasks = 
                    mActivityManager.getRunningTasks(1);
            
            if (tasks != null && !tasks.isEmpty()) {
                String currentApp = tasks.get(0).topActivity.getPackageName();
                
                if (!currentApp.equals(mCurrentApp)) {
                    mCurrentApp = currentApp;
                    applyTouchSamplingForApp(currentApp);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking current app", e);
        }
    }

    private void applyTouchSamplingForApp(String packageName) {
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        
        boolean globalHtsrEnabled = sharedPref.getBoolean(
                TouchSamplingSettingsFragment.HTSR_STATE, false);
        
        int desiredState = 0;
        
        if (globalHtsrEnabled) {
            // Global HTSR is enabled
            desiredState = 1;
        } else {
            // Check per-app HTSR
            Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(this);
            if (enabledApps.contains(packageName)) {
                desiredState = 1;
            }
        }
        
        int currentState = TouchSamplingUtils.readTouchSamplingState();
        
        if (currentState != desiredState) {
            Log.d(TAG, "Applying touch sampling for " + packageName + ": " + desiredState);
            TouchSamplingUtils.writeTouchSamplingState(desiredState);
        }
    }
}
