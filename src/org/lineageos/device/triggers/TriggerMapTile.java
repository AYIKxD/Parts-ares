/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.device.triggers;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import org.lineageos.device.R;
import org.lineageos.device.util.Utils;

/**
 * QS Tile to toggle the trigger mapping overlay.
 * Only activates when user is inside an app from the game app list.
 * Allows adjusting trigger positions while in-game.
 */
public class TriggerMapTile extends TileService {

    private TriggerService mTriggerService;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();

        // Only allow trigger mapping inside game apps
        if (!Utils.isGameApp(this)) {
            Toast.makeText(this, R.string.trigger_map_not_in_game, Toast.LENGTH_SHORT).show();
            updateTileState();
            return;
        }

        mTriggerService = TriggerService.getInstance(this);

        if (mTriggerService.isShowing()) {
            mTriggerService.hide();
        } else {
            mTriggerService.show();
        }

        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null)
            return;

        mTriggerService = TriggerService.getInstance(this);
        boolean active = mTriggerService != null && mTriggerService.isShowing();
        boolean inGame = Utils.isGameApp(this);

        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.qs_trigger_map_label));

        if (active) {
            tile.setSubtitle(getString(R.string.switch_bar_on));
        } else if (!inGame) {
            tile.setSubtitle(getString(R.string.trigger_map_tile_not_in_game));
        } else {
            tile.setSubtitle(getString(R.string.switch_bar_off));
        }

        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_trigger_map));
        tile.updateTile();
    }
}
