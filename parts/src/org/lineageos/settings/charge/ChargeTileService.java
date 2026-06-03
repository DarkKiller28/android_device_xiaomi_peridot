package org.lineageos.settings.charge;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;

public class ChargeTileService extends TileService {
    private static final String PREF_KEY = "saved_charge_mode";
    private static final String[][] MODES = {
        {"OK", "3500000"},
        {"COOL", "6000000"},
        {"NUKE", "10000000"}
    };
   
    @Override
    public void onClick() {
        int nextIndex = (getCurrentIndex() + 1) % MODES.length;
        saveIndex(nextIndex);
        updateTile();

        Intent intent = new Intent(this, ChargeEnforcementService.class);
        if (nextIndex > 0) {
            startService(intent);
        } else {
            stopService(intent);
        }
    }

    private int getCurrentIndex() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(PREF_KEY, 0);
    }
    
    private void saveIndex(int index) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(PREF_KEY, index).apply();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(MODES[getCurrentIndex()][0]);
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }
    
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }
}
