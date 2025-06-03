/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.power;

import android.os.SystemProperties;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class PowerProfileTileService extends TileService {

    private static final String POWER_PROFILE_PATH = "/sys/class/thermal/thermal_message/sconfig";
    
    private static final int POWER_PROFILE_DEFAULT = 0;
    private static final int POWER_PROFILE_MBATTERY = 1;
    private static final int POWER_PROFILE_MPERFORMANCE = 6;

    private static final String SYS_PROP = "sys.perf_mode_active";

    private void updateUI(int profile) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.powerprofile_title));
            String subtitle;
            switch (profile) {
                case POWER_PROFILE_DEFAULT:
                    subtitle = getString(R.string.powerprofile_default);
                    break;
                case POWER_PROFILE_MBATTERY:
                    subtitle = getString(R.string.powerprofile_battery);
                    break;
                case POWER_PROFILE_MPERFORMANCE:
                    subtitle = getString(R.string.powerprofile_performance);
                    break;
                default:
                    subtitle = getString(R.string.powerprofile_unknown);
            }
            tile.setSubtitle(subtitle);
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        int profile = FileUtils.readLineInt(POWER_PROFILE_PATH);
        updateUI(profile);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        int currentProfile = FileUtils.readLineInt(POWER_PROFILE_PATH);

        // Cycle through profiles: DEFAULT → BATTERY → PERFORMANCE → DEFAULT ...
        int newProfile;
        switch (currentProfile) {
            case POWER_PROFILE_DEFAULT:
                newProfile = POWER_PROFILE_MBATTERY;
                break;
            case POWER_PROFILE_MBATTERY:
                newProfile = POWER_PROFILE_MPERFORMANCE;
                break;
            case POWER_PROFILE_MPERFORMANCE:
            default:
                newProfile = POWER_PROFILE_DEFAULT;
                break;
        }

        FileUtils.writeLine(POWER_PROFILE_PATH, newProfile);

        if (newProfile == POWER_PROFILE_MPERFORMANCE) {
            SystemProperties.set(SYS_PROP, "1");  // Disable LPM
        } else {
            SystemProperties.set(SYS_PROP, "0");  // Enable LPM
        }

        updateUI(newProfile);
    }
}
