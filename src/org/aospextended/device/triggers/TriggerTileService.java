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

package org.aospextended.device.triggers;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.aospextended.device.R;

/**
 * Quick Settings tile to show/hide the trigger position overlay.
 * Users can tap this tile to quickly adjust trigger positions during gaming.
 */
public class TriggerTileService extends TileService {

    private static final String TAG = "TriggerTileService";
    private TriggerService mTriggerService;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        mTriggerService = TriggerService.getInstance(this);
        
        if (mTriggerService != null) {
            if (mTriggerService.isShowing()) {
                mTriggerService.hide();
                Log.d(TAG, "Hiding trigger overlay");
            } else {
                mTriggerService.show();
                Log.d(TAG, "Showing trigger overlay");
            }
            updateTileState();
        } else {
            Log.e(TAG, "TriggerService is null");
        }
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        mTriggerService = TriggerService.getInstance(this);
        boolean isActive = mTriggerService != null && mTriggerService.isShowing();

        tile.setState(isActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.trigger_tile_label));
        tile.setSubtitle(isActive ? getString(R.string.trigger_tile_on) : getString(R.string.trigger_tile_off));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_trigger_tile));
        tile.updateTile();
    }
}
