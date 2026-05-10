/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.power;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Set;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;
import org.lineageos.settings.touchsampling.TouchSamplingSettingsFragment;

public class PowerProfileTileService extends TileService {
    private static final String TAG = "PowerProfileTileService";
    private static final String POWER_PROFILE_PATH = "/sys/class/thermal/thermal_message/sconfig";
    private static final String POWER_ENABLED_KEY = "power_enabled";
    private static final String POWER_PROFILE_PREF_KEY = "saved_power_profile";
    private static final String PREV_POWER_PROFILE_PREF_KEY = "prev_power_profile";
    private static final String SYS_PERF_PROP = "sys.perf_mode_active";
    private static final int NOTIFICATION_ID_PERFORMANCE = 1001;

    private enum PowerProfile {
        DEFAULT(0, R.string.powerprofile_default, R.drawable.ic_power_default, "1"),
        BATTERY(1, R.string.powerprofile_battery, R.drawable.ic_power_battery_saver, "0"),
        PERFORMANCE(6, R.string.powerprofile_performance, R.drawable.ic_power_performance, "2"),
        UNKNOWN(-1, R.string.powerprofile_unknown, R.drawable.ic_power_default, "1");

        private final int value;
        private final int nameResId;
        private final int iconResId;
        private final String sysPropValue;

        PowerProfile(int value, int nameResId, int iconResId, String sysPropValue) {
            this.value = value;
            this.nameResId = nameResId;
            this.iconResId = iconResId;
            this.sysPropValue = sysPropValue;
        }

        public int getValue() { return value; }
        public int getNameResId() { return nameResId; }
        public int getIconResId() { return iconResId; }
        public String getSysPropValue() { return sysPropValue; }

        public static PowerProfile fromValue(int value) {
            for (PowerProfile profile : values()) {
                if (profile.value == value) return profile;
            }
            return UNKNOWN;
        }

        public PowerProfile getNext() {
            switch (this) {
                case DEFAULT: return BATTERY;
                case BATTERY: return PERFORMANCE;
                case PERFORMANCE: 
                case UNKNOWN: 
                default: return DEFAULT;
            }
        }
    }

    // Core components
    private SharedPreferences mSharedPrefs;
    private NotificationManager mNotificationManager;
    private PowerManager mPowerManager;
    private ContentObserver mBatterySaverObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeComponents();
        setupNotificationChannel();
        registerBatterySaverObserver();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!isPowerEnabled()) {
            updateTileState(PowerProfile.UNKNOWN, false);
            return;
        }

        // Check if this is first boot
        if (isFirstBoot()) {
            Log.i(TAG, "First boot detected, setting to DEFAULT profile");
            applyProfile(PowerProfile.DEFAULT);
        } else {
            PowerProfile current = getCurrentProfile();
            PowerProfile saved = getSavedProfile();
            
            if (current != saved && saved != PowerProfile.UNKNOWN) {
                Log.d(TAG, "Reboot detected, restoring saved profile: " + saved);
                applyProfile(saved);
            } else if (current == PowerProfile.UNKNOWN) {
                Log.w(TAG, "Unknown profile detected, falling back to DEFAULT");
                applyProfile(PowerProfile.DEFAULT);
            } else {
                updateTileState(current, true);
            }
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!isPowerEnabled()) {
            Log.w(TAG, "Power profiles are disabled");
            return;
        }

        PowerProfile current = getCurrentProfile();
        PowerProfile next = current.getNext();
        applyProfile(next);
        Log.d(TAG, "Profile changed: " + current + " → " + next);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    // Core Methods
    private void initializeComponents() {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mNotificationManager = getSystemService(NotificationManager.class);
        mPowerManager = getSystemService(PowerManager.class);
    }

    private void cleanup() {
        if (mBatterySaverObserver != null) {
            getContentResolver().unregisterContentObserver(mBatterySaverObserver);
            mBatterySaverObserver = null;
        }
        mNotificationManager = null;
        mPowerManager = null;
        mSharedPrefs = null;
    }

    private boolean isPowerEnabled() {
        return mSharedPrefs.getBoolean(POWER_ENABLED_KEY, true);
    }

    private PowerProfile getCurrentProfile() {
        String value = FileUtils.readOneLine(POWER_PROFILE_PATH);
        if (value == null) {
            Log.e(TAG, "Failed to read power profile");
            return PowerProfile.UNKNOWN;
        }

        try {
            return PowerProfile.fromValue(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid power profile value: " + value, e);
            return PowerProfile.UNKNOWN;
        }
    }

    private boolean isCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) return false;

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void applyProfile(PowerProfile profile) {
        if (!writeProfileToSysfs(profile)) return;
        
        setSystemProperty(profile);
        handleProfileActions(profile);
        saveProfile(profile);
        updateTileState(profile, true);
        
        Log.d(TAG, "Applied power profile: " + getString(profile.getNameResId()));
    }

    private boolean writeProfileToSysfs(PowerProfile profile) {
        boolean success = FileUtils.writeLine(POWER_PROFILE_PATH, String.valueOf(profile.getValue()));
        if (!success) {
            Log.e(TAG, "Failed to write power profile: " + profile);
        }
        return success;
    }

    private void setSystemProperty(PowerProfile profile) {
        try {
            SystemProperties.set(SYS_PERF_PROP, profile.getSysPropValue());
            Log.d(TAG, "Set " + SYS_PERF_PROP + " = " + profile.getSysPropValue());
        } catch (Exception e) {
            Log.w(TAG, "Failed to set system property for " + profile + ": " + e.getMessage());
        }
    }

    private void handleProfileActions(PowerProfile profile) {
        if (profile == PowerProfile.BATTERY) {
            savePreviousProfile(getCurrentProfile());
        }

        boolean isCharging = isCharging();
        
        // Handle HTSR for PERFORMANCE profile
        boolean htsrEnabled = profile == PowerProfile.PERFORMANCE;
        updateTouchSamplingState(htsrEnabled);
        
        switch (profile) {
            case BATTERY:
                if (!isCharging) {
                    setBatterySaver(true);
                } else {
                    setBatterySaver(false); // Disable battery saver if charging
                }
                cancelPerformanceNotification();
                break;
            case PERFORMANCE:
                setBatterySaver(false); // Always disable battery saver for PERFORMANCE
                showPerformanceNotification();
                break;
            case DEFAULT:
                setBatterySaver(false);
                cancelPerformanceNotification();
                break;
            default:
                setBatterySaver(false);
                cancelPerformanceNotification();
                break;
        }
    }

    private void setBatterySaver(boolean enable) {
        if (mPowerManager == null) return;

        try {
            boolean currentState = mPowerManager.isPowerSaveMode();
            if (currentState != enable) {
                Settings.Global.putInt(getContentResolver(), 
                    Settings.Global.LOW_POWER_MODE, enable ? 1 : 0);
                Log.d(TAG, "Battery saver " + (enable ? "enabled" : "disabled"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle battery saver", e);
        }
    }

    private void updateTileState(PowerProfile profile, boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.powerprofile_title));
        tile.setIcon(Icon.createWithResource(this, profile.getIconResId()));
        
        if (enabled && profile != PowerProfile.UNKNOWN) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(profile.getNameResId()));
        } else {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.power_tile_disabled_subtitle));
        }
        
        tile.updateTile();
    }

    // Notification Management
    private void setupNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            TAG, 
            getString(R.string.perf_mode_title),
            NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setBlockable(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void showPerformanceNotification() {
        if (mNotificationManager == null) return;

        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, TAG)
            .setContentTitle(getString(R.string.perf_mode_title))
            .setContentText(getString(R.string.perf_mode_notification))
            .setSmallIcon(R.drawable.ic_power_performance)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();

        mNotificationManager.notify(NOTIFICATION_ID_PERFORMANCE, notification);
    }

    private void cancelPerformanceNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID_PERFORMANCE);
        }
    }

    // Battery Saver Observer
    private void registerBatterySaverObserver() {
        mBatterySaverObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                boolean isBatterySaverOn = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.LOW_POWER_MODE, 0) == 1;
                
                PowerProfile current = getCurrentProfile();
                if (isBatterySaverOn && !isCharging() && current != PowerProfile.PERFORMANCE) {
                    if (current != PowerProfile.BATTERY) {
                        Log.d(TAG, "Battery saver enabled, switching to battery profile");
                        applyProfile(PowerProfile.BATTERY);
                    }
                } else if (!isBatterySaverOn && current == PowerProfile.BATTERY) {
                    PowerProfile prevProfile = getPreviousProfile();
                    if (prevProfile != PowerProfile.BATTERY && prevProfile != PowerProfile.UNKNOWN) {
                        Log.d(TAG, "Battery saver disabled, restoring previous profile: " + prevProfile);
                        applyProfile(prevProfile);
                    } else {
                        Log.d(TAG, "Battery saver disabled, switching to DEFAULT");
                        applyProfile(PowerProfile.DEFAULT);
                    }
                }
            }
        };

        getContentResolver().registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.LOW_POWER_MODE),
            false, mBatterySaverObserver
        );
    }

    // Preference Management
    private void saveProfile(PowerProfile profile) {
        if (mSharedPrefs != null) {
            mSharedPrefs.edit()
                .putInt(POWER_PROFILE_PREF_KEY, profile.getValue())
                .apply();
        }
    }

    private PowerProfile getSavedProfile() {
        if (mSharedPrefs == null) return PowerProfile.DEFAULT;
        
        int savedValue = mSharedPrefs.getInt(POWER_PROFILE_PREF_KEY, PowerProfile.DEFAULT.getValue());
        return PowerProfile.fromValue(savedValue);
    }

    private void savePreviousProfile(PowerProfile profile) {
        if (mSharedPrefs != null && profile != PowerProfile.BATTERY) {
            mSharedPrefs.edit()
                .putInt(PREV_POWER_PROFILE_PREF_KEY, profile.getValue())
                .apply();
        }
    }

    private PowerProfile getPreviousProfile() {
        if (mSharedPrefs == null) return PowerProfile.DEFAULT;
        
        int prevValue = mSharedPrefs.getInt(PREV_POWER_PROFILE_PREF_KEY, PowerProfile.DEFAULT.getValue());
        return PowerProfile.fromValue(prevValue);
    }

    // Boot Detection & Profile Management
    private boolean isFirstBoot() {
        return !mSharedPrefs.contains(POWER_PROFILE_PREF_KEY);
    }

    // Touch Sampling Integration
    private void updateTouchSamplingState(boolean enable) {
        // Update hardware state
        TouchSamplingUtils.writeTouchSamplingState(enable ? 1 : 0);

        // Update SharedPreferences for TouchSamplingSettingsFragment
        SharedPreferences htsrPrefs = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        htsrPrefs.edit().putBoolean(TouchSamplingSettingsFragment.HTSR_STATE, enable).apply();

        // Control TouchSamplingService
        Intent serviceIntent = new Intent(this, org.lineageos.settings.touchsampling.TouchSamplingService.class);
        if (enable) {
            startService(serviceIntent);
        } else {
            // Only stop the service if no per-app HTSR is enabled
            Set<String> enabledApps = TouchSamplingUtils.getPerAppHtsrEnabledApps(this);
            if (enabledApps.isEmpty()) {
                stopService(serviceIntent);
            }
        }

        // Update Quick Settings tile
        Intent tileUpdateIntent = new Intent("org.lineageos.settings.touchsampling.ACTION_UPDATE_TILE");
        sendBroadcast(tileUpdateIntent);

        Log.d(TAG, "Touch sampling " + (enable ? "enabled" : "disabled") + " for performance profile");
    }
}
