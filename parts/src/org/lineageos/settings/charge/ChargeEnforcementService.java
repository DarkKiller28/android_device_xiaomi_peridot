package org.lineageos.settings.charge;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.utils.FileUtils;

public class ChargeEnforcementService extends Service {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int REWRITE_INTERVAL = 10000; // 10 seconds
    private static final String LIMIT_PATH = "/sys/class/power_supply/battery/charge_control_limit";
    private static final String CURRENT_PATH = "/sys/class/power_supply/battery/constant_charge_current";

    private final Runnable mRewriteRunnable = new Runnable() {
        @Override
        public void run() {
            int index = PreferenceManager.getDefaultSharedPreferences(ChargeEnforcementService.this)
                        .getInt("saved_charge_mode", 0);
            
            // Only write to kernel if a mode is active AND the device is charging
            if (index > 0 && isCharging()) {
                String[] currents = {"6000000", "10000000"}; // COOL, NUKE
                FileUtils.writeLine(LIMIT_PATH, "0");
                FileUtils.writeLine(CURRENT_PATH, currents[index - 1]);
            }
            
            mHandler.postDelayed(this, REWRITE_INTERVAL);
        }
    };

    private boolean isCharging() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || 
               status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.post(mRewriteRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mRewriteRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
