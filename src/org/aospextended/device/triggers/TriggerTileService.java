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

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.aospextended.device.R;

/**
 * Quick Settings tile to show/hide the trigger position overlay.
 * Collapses the panel first, then shows the overlay in the current app.
 */
public class TriggerTileService extends TileService {

    private static final String TAG = "TriggerTileService";
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        TriggerService triggerService = TriggerService.getInstance(this);
        
        if (triggerService != null) {
            boolean wasShowing = triggerService.isShowing();
            
            // Collapse the panel first, then toggle overlay
            collapsePanelAndRun(() -> {
                if (wasShowing) {
                    triggerService.hide();
                    Log.d(TAG, "Hiding trigger overlay");
                } else {
                    triggerService.init(this);
                    triggerService.show();
                    Log.d(TAG, "Showing trigger overlay");
                }
            });
        } else {
            Log.e(TAG, "TriggerService is null");
        }
        
        updateTileState();
    }

    private void collapsePanelAndRun(Runnable action) {
        // Collapse the Quick Settings panel
        try {
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception e) {
            Log.e(TAG, "Failed to collapse panel", e);
        }
        
        // Run action after a short delay to allow panel to close
        mHandler.postDelayed(action, 300);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        TriggerService triggerService = TriggerService.getInstance(this);
        boolean isActive = triggerService != null && triggerService.isShowing();

        tile.setState(isActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.trigger_tile_label));
        tile.setSubtitle(isActive ? getString(R.string.trigger_tile_on) : getString(R.string.trigger_tile_off));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_trigger_tile));
        tile.updateTile();
    }
}
