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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TouchSamplingPerAppFragment extends PreferenceFragment {

    private static final String TAG = "TouchSamplingPerAppFragment";
    
    private PackageManager mPackageManager;
    private PreferenceScreen mPreferenceScreen;
    private boolean mShowSystemApps = false;
    private LoadAppsTask mLoadAppsTask;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.per_app_htsr_settings);
        
        mPackageManager = getActivity().getPackageManager();
        mPreferenceScreen = getPreferenceScreen();
        
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        getActivity().setTitle(R.string.per_app_htsr_title);
        
        loadInstalledApps();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoadAppsTask != null && !mLoadAppsTask.isCancelled()) {
            mLoadAppsTask.cancel(true);
        }
    }

    public void toggleSystemApps() {
        mShowSystemApps = !mShowSystemApps;
        loadInstalledApps();
    }

    public boolean isShowingSystemApps() {
        return mShowSystemApps;
    }

    private void loadInstalledApps() {
        if (mLoadAppsTask != null && !mLoadAppsTask.isCancelled()) {
            mLoadAppsTask.cancel(true);
        }
        mLoadAppsTask = new LoadAppsTask();
        mLoadAppsTask.execute();
    }

    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {

        @Override
        protected void onPreExecute() {
            mPreferenceScreen.removeAll();
            // Show loading indicator
            Preference loadingPref = new Preference(getActivity());
            loadingPref.setTitle(R.string.loading_apps);
            loadingPref.setEnabled(false);
            mPreferenceScreen.addPreference(loadingPref);
        }

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            List<ApplicationInfo> installedApps = mPackageManager.getInstalledApplications(0);
            List<AppInfo> appInfoList = new ArrayList<>();

            for (ApplicationInfo appInfo : installedApps) {
                if (isCancelled()) {
                    return null;
                }

                boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                
                // Skip system apps if not showing them
                if (!mShowSystemApps && isSystemApp) {
                    continue;
                }

                try {
                    String appName = mPackageManager.getApplicationLabel(appInfo).toString();
                    Drawable appIcon = mPackageManager.getApplicationIcon(appInfo);
                    appInfoList.add(new AppInfo(appInfo.packageName, appName, isSystemApp, appIcon));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get app name or icon for: " + appInfo.packageName);
                }
            }

            // Sort apps alphabetically
            Collections.sort(appInfoList, (a, b) -> 
                Collator.getInstance().compare(a.name, b.name));

            return appInfoList;
        }

        @Override
        protected void onPostExecute(List<AppInfo> appInfoList) {
            mPreferenceScreen.removeAll();
            
            if (appInfoList == null || appInfoList.isEmpty()) {
                Preference emptyPref = new Preference(getActivity());
                emptyPref.setTitle(R.string.no_apps_found);
                emptyPref.setEnabled(false);
                mPreferenceScreen.addPreference(emptyPref);
                return;
            }

            Context context = getActivity();
            for (AppInfo appInfo : appInfoList) {
                SwitchPreference appPref = new SwitchPreference(context);
                appPref.setKey(appInfo.packageName);
                appPref.setTitle(appInfo.name);
                appPref.setSummary(appInfo.packageName);
                appPref.setIcon(appInfo.icon);
                
                // Set current state
                boolean isEnabled = TouchSamplingUtils.isPerAppHtsrEnabled(context, appInfo.packageName);
                appPref.setChecked(isEnabled);
                
                // Set change listener
                appPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    TouchSamplingUtils.setPerAppHtsrEnabled(context, appInfo.packageName, enabled);
                    Log.d(TAG, "Per-app HTSR for " + appInfo.packageName + ": " + enabled);
                    
                    // Start service if any app has per-app HTSR enabled
                    Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(context);
                    if (!enabledApps.isEmpty()) {
                        Intent serviceIntent = new Intent(context, TouchSamplingService.class);
                        context.startService(serviceIntent);
                    }
                    return true;
                });
                
                mPreferenceScreen.addPreference(appPref);
            }
        }
    }

    private static class AppInfo {
        final String packageName;
        final String name;
        final boolean isSystemApp;
        final Drawable icon;

        AppInfo(String packageName, String name, boolean isSystemApp, Drawable icon) {
            this.packageName = packageName;
            this.name = name;
            this.isSystemApp = isSystemApp;
            this.icon = icon;
        }
    }
}
