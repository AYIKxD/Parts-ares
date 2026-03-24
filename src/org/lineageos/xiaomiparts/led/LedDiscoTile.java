/*
 * Copyright (C) 2020 The AospExtended Project
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

package org.lineageos.xiaomiparts.led;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.xiaomiparts.R;
import org.lineageos.xiaomiparts.util.Utils;

/**
 * QS Tile to quickly toggle LED disco mode on/off.
 */
public class LedDiscoTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();

        SharedPreferences prefs = Utils.getSharedPreferences(this);
        boolean currentState = prefs.getBoolean("led_disco", false);
        boolean newState = !currentState;

        prefs.edit().putBoolean("led_disco", newState).apply();

        LedUtils ledUtils = LedUtils.getInstance(this);
        ledUtils.play(newState);

        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences prefs = Utils.getSharedPreferences(this);
        boolean active = prefs.getBoolean("led_disco", false);

        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.qs_led_disco_label));
        tile.setSubtitle(active ? getString(R.string.switch_bar_on) : getString(R.string.switch_bar_off));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_led_disco));
        tile.updateTile();
    }
}
