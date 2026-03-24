/*
 * Copyright (C) 2025 XiaomiParts Project
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

package org.lineageos.xiaomiparts.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.lineageos.xiaomiparts.triggers.TriggerService
import org.lineageos.xiaomiparts.triggers.TriggerUtils
import org.lineageos.xiaomiparts.util.AppListActivity
import org.lineageos.xiaomiparts.triggers.CustomTriggerActivity
import org.lineageos.xiaomiparts.ui.components.*

private const val PREFS_NAME = "org.lineageos.xiaomiparts_preferences"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.createPackageContext(
            "org.lineageos.xiaomiparts",
            Context.CONTEXT_IGNORE_SECURITY
        ).getSharedPreferences(PREFS_NAME,
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
    }

    var triggerSound by remember { mutableStateOf(prefs.getBoolean("trigger_sound", false)) }
    var triggerSoundType by remember { mutableStateOf(prefs.getString("trigger_sound_type", "classic") ?: "classic") }
    var alertSliderMode by remember { mutableStateOf(prefs.getString("alert_slider_mode", "disabled") ?: "disabled") }

    val soundTypeEntries = listOf("Sword", "Bullet", "Future", "Engine")
    val soundTypeValues = listOf("classic", "bullet", "current", "wind")

    val alertSliderEntries = listOf("Disabled", "Vibrate", "Silent", "DND + Silent", "DND + Vibrate", "DND Total Silence")
    val alertSliderValues = listOf("disabled", "vibrate", "silent", "dnd_silent", "dnd_vibrate", "dnd_total")

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Triggers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(title = "TRIGGER MAP")

            ActionRow(
                title = "Open Trigger Map",
                summary = "Drag to set trigger positions. Long press save to reset.",
                icon = Icons.Filled.Map,
                onClick = { TriggerService.getInstance(context).show() }
            )

            SectionHeader(title = "SOUND & HAPTICS")

            SwitchRow(
                title = "Trigger Sound",
                summary = "Play sound when trigger opens/closes",
                checked = triggerSound,
                icon = Icons.Filled.VolumeUp,
                onCheckedChange = { newValue ->
                    triggerSound = newValue
                    prefs.edit().putBoolean("trigger_sound", newValue).apply()
                    Settings.System.putInt(
                        context.contentResolver, "trigger_sound",
                        if (newValue) 1 else 0
                    )
                }
            )

            DropdownRow(
                title = "Sound Type",
                summary = "Choose trigger feedback sound",
                selectedValue = triggerSoundType,
                entries = soundTypeEntries,
                values = soundTypeValues,
                icon = Icons.Filled.MusicNote,
                onValueChange = { newValue ->
                    triggerSoundType = newValue
                    prefs.edit().putString("trigger_sound_type", newValue).apply()
                    Settings.System.putString(
                        context.contentResolver, "trigger_sound_type", newValue
                    )
                    TriggerUtils.getInstance(context).triggerAction(true, true)
                }
            )

            SectionHeader(title = "ALERT SLIDER")

            DropdownRow(
                title = "Alert Slider Mode",
                summary = "Action when left slider is opened",
                selectedValue = alertSliderMode,
                entries = alertSliderEntries,
                values = alertSliderValues,
                icon = Icons.Filled.Notifications,
                onValueChange = { newValue ->
                    alertSliderMode = newValue
                    prefs.edit().putString("alert_slider_mode", newValue).apply()
                }
            )

            SectionHeader(title = "CUSTOMIZATION")

            ActionRow(
                title = "Gaming Apps",
                summary = "Add or remove gaming apps",
                icon = Icons.Filled.SportsEsports,
                onClick = {
                    context.startActivity(Intent(context, AppListActivity::class.java))
                }
            )

            ActionRow(
                title = "Trigger Actions",
                summary = "Customize trigger button actions for non-gaming apps",
                icon = Icons.Filled.Tune,
                onClick = {
                    context.startActivity(Intent(context, CustomTriggerActivity::class.java))
                }
            )
        }
    }
}
