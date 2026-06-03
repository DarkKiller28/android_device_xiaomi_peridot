package org.lineageos.settings.charge;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.utils.FileUtils;

public class ChargeTileService extends TileService {
    private static final String LIMIT_PATH = "/sys/class/power_supply/battery/charge_control_limit";
    private static final String CURRENT_PATH = "/sys/class/power_supply/battery/constant_charge_current";
    private static final String PREF_KEY = "saved_charge_mode";
    
    // Define your modes here (Label, Value)
    private static final String[][] MODES = {
        {"OK", "3500000"},
        {"COOL", "6000000"},
        {"NUKE", "10000000"}
    };

    // 1. Change interval to 5 seconds
    private static final int REWRITE_INTERVAL = 5000; 

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mRewriteRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCharging()) {
                int currentIndex = getCurrentIndex();
                // 2. Add the specific check for NUKE mode (index 2 in your array)
                // If you want it for COOL (index 1) and NUKE (index 2), use: index > 0
                if (currentIndex == 2) { 
                    applyCurrentMode();
                }
            }
            mHandler.postDelayed(this, REWRITE_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler.postDelayed(mRewriteRunnable, REWRITE_INTERVAL);
    }

    @Override
    public void onClick() {
        int currentIndex = getCurrentIndex();
        int nextIndex = (currentIndex + 1) % MODES.length;
        
        saveIndex(nextIndex);
        applyCurrentMode();
        updateTile();
    }

    private void applyCurrentMode() {
        final int index = getCurrentIndex();

        // LOG THE THREAD NAME
        android.util.Log.d("ChargeLog", "Current Thread: " + Thread.currentThread().getName());

        new Thread(() -> {
            // LOG THE THREAD NAME AGAIN
            android.util.Log.d("ChargeLog", "Writing Thread: " + Thread.currentThread().getName());
         
            FileUtils.writeLine(LIMIT_PATH, "0");
            FileUtils.writeLine(CURRENT_PATH, MODES[index][1]);
        }).start();
    }

    private int getCurrentIndex() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(PREF_KEY, 0);
    }

    private void saveIndex(int index) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(PREF_KEY, index).apply();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        tile.setLabel(MODES[getCurrentIndex()][0]);
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private boolean isCharging() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRewriteRunnable);
    }
}
