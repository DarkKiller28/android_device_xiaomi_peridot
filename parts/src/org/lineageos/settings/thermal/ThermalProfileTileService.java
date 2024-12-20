/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class ThermalProfileTileService extends TileService {

    private static final String THERMAL_PROFILE_PATH = "/sys/class/thermal/thermal_message/sconfig";
    
    private static final int THERMAL_PROFILE_DEFAULT = 0;
    private static final int THERMAL_PROFILE_MBATTERY = 1;
    private static final int THERMAL_PROFILE_MPERFORMANCE = 6;
    private static final int THERMAL_PROFILE_MGAME = 19;

    private void updateUI(int profile) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.thermalprofile_title));
            String subtitle;
            switch (profile) {
                case THERMAL_PROFILE_DEFAULT:
                    subtitle = getString(R.string.thermalprofile_default);
                    break;
                case THERMAL_PROFILE_MBATTERY:
                    subtitle = getString(R.string.thermalprofile_battery);
                    break;
                case THERMAL_PROFILE_MPERFORMANCE:
                    subtitle = getString(R.string.thermalprofile_performance);
                    break;
                case THERMAL_PROFILE_MGAME:
                    subtitle = getString(R.string.thermalprofile_game);
                    break;
                default:
                    subtitle = getString(R.string.thermalprofile_unknown);
            }
            tile.setSubtitle(subtitle);
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        int profile = FileUtils.readLineInt(THERMAL_PROFILE_PATH);
        updateUI(profile);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        int currentProfile = FileUtils.readLineInt(THERMAL_PROFILE_PATH);

        // Cycle through profiles: DEFAULT → BATTERY → PERFORMANCE → GAME → DEFAULT ...
        int newProfile;
        switch (currentProfile) {
            case THERMAL_PROFILE_DEFAULT:
                newProfile = THERMAL_PROFILE_MBATTERY;
                break;
            case THERMAL_PROFILE_MBATTERY:
                newProfile = THERMAL_PROFILE_MPERFORMANCE;
                break;
            case THERMAL_PROFILE_MPERFORMANCE:
                newProfile = THERMAL_PROFILE_MGAME;
                break;
            case THERMAL_PROFILE_MGAME:
            default:
                newProfile = THERMAL_PROFILE_DEFAULT;
                break;
        }

        FileUtils.writeLine(THERMAL_PROFILE_PATH, newProfile);
        updateUI(newProfile);
    }
}
