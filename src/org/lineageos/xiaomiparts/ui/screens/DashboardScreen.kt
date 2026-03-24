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

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.lineageos.xiaomiparts.gestures.TouchGestures
import org.lineageos.xiaomiparts.gestures.TouchGesturesActivity
import org.lineageos.xiaomiparts.ui.components.CategoryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTriggers: () -> Unit,
    onNavigateToLeds: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gesturesSupported = TouchGestures.isSupported()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "XiaomiParts",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            CategoryCard(
                title = "Triggers",
                summary = "Configure trigger buttons, sounds, and alert slider",
                icon = Icons.Filled.Gamepad,
                onClick = onNavigateToTriggers
            )

            CategoryCard(
                title = "LED Controls",
                summary = "RGB LED disco mode, gaming integration, call effects",
                icon = Icons.Filled.LightMode,
                onClick = onNavigateToLeds
            )

            if (gesturesSupported) {
                CategoryCard(
                    title = "Gestures",
                    summary = "Screen-off touchscreen gestures",
                    icon = Icons.Filled.TouchApp,
                    onClick = {
                        context.startActivity(
                            Intent(context, TouchGesturesActivity::class.java)
                        )
                    }
                )
            }
        }
    }
}
