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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.lineageos.settings.R;

public class TouchSamplingTileService extends TileService {

    private static final String TAG = "TouchSamplingTileService";

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile added");
        updateTileState();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.d(TAG, "Tile removed");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "Tile started listening");
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "Tile clicked");
        toggleTouchSampling();
        updateTileState();
    }

    private void updateTileState() {
        boolean htsrEnabled = isTouchSamplingEnabled();

        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(htsrEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    private void toggleTouchSampling() {
        boolean currentState = isTouchSamplingEnabled();
        boolean newState = !currentState;

        // Save state to SharedPreferences
        saveTouchSamplingState(newState);

        // Control service
        Intent serviceIntent = new Intent(this, TouchSamplingService.class);
        if (newState) {
            startService(serviceIntent);
        } else {
            stopService(serviceIntent);
        }

        // Write to hardware file
        TouchSamplingUtils.writeTouchSamplingState(newState ? 1 : 0);
    }

    private boolean isTouchSamplingEnabled() {
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(TouchSamplingSettingsFragment.HTSR_STATE, false);
    }

    private void saveTouchSamplingState(boolean state) {
        SharedPreferences sharedPref = getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        sharedPref.edit().putBoolean(TouchSamplingSettingsFragment.HTSR_STATE, state).apply();
    }

    /**
     * Receiver to handle boot completion and reinitialize the tile state.
     */
    public static class BootCompletedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                Log.d(TAG, "Boot completed - restoring touch sampling state");
                TouchSamplingUtils.restoreSamplingValue(context);
            }
        }
    }
}
