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

package org.lineageos.device;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.lineageos.device.util.TaskService;
import org.lineageos.device.gamekey.GamekeyService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        // VibratorStrengthPreference.restore(context);

        // TriggerUtils tr = TriggerUtils.getInstance(context);
        // tr.onBoot();
        context.startService(new Intent(context, TaskService.class));

        // Start the new Kotlin-based GamekeyService for trigger detection
        GamekeyService.Companion.startService(context);
    }

    private void enableComponent(Context context, String component) {
        ComponentName name = new ComponentName(context, component);
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(name) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
