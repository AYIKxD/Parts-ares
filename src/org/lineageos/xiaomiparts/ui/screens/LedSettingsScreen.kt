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
import org.lineageos.xiaomiparts.led.LedUtils
import org.lineageos.xiaomiparts.ui.components.*

private const val PREFS_NAME = "org.lineageos.xiaomiparts_preferences"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.createPackageContext(
            "org.lineageos.xiaomiparts",
            Context.CONTEXT_IGNORE_SECURITY
        ).getSharedPreferences(PREFS_NAME,
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
    }

    var ledDisco by remember { mutableStateOf(prefs.getBoolean("led_disco", false)) }
    var ledInGames by remember { mutableStateOf(prefs.getBoolean("led_in_games", false)) }
    var ledInCalls by remember {
        val value = Settings.System.getInt(context.contentResolver, "led_in_calls", 1)
        mutableStateOf(value == 1)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("LED Controls") },
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
            SectionHeader(title = "DISCO MODE")

            SwitchRow(
                title = "LED Disco",
                summary = "Enable RGB LED disco effect",
                checked = ledDisco,
                icon = Icons.Filled.Lightbulb,
                onCheckedChange = { newValue ->
                    ledDisco = newValue
                    prefs.edit().putBoolean("led_disco", newValue).apply()
                    LedUtils.getInstance(context).play(newValue)
                }
            )

            SwitchRow(
                title = "Play in Games",
                summary = "Only activate LED disco while gaming",
                checked = ledInGames,
                enabled = ledDisco,
                icon = Icons.Filled.SportsEsports,
                onCheckedChange = { newValue ->
                    ledInGames = newValue
                    prefs.edit().putBoolean("led_in_games", newValue).apply()
                    val shouldPlay = !newValue || (newValue && ledDisco)
                    LedUtils.getInstance(context).play(shouldPlay)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "CALLS")

            SwitchRow(
                title = "LED During Calls",
                summary = "Play LED light effect during phone calls",
                checked = ledInCalls,
                icon = Icons.Filled.Call,
                onCheckedChange = { newValue ->
                    ledInCalls = newValue
                    Settings.System.putInt(
                        context.contentResolver, "led_in_calls",
                        if (newValue) 1 else 0
                    )
                }
            )
        }
    }
}
