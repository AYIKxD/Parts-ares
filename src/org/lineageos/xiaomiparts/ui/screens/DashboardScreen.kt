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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.gestures.TouchGestures
import org.lineageos.xiaomiparts.gestures.TouchGesturesActivity
import org.lineageos.xiaomiparts.ui.components.BottomNavBar
import org.lineageos.xiaomiparts.ui.components.NavItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gesturesSupported = TouchGestures.isSupported()

    val navItems = buildList {
        add(NavItem(
            route = "triggers",
            label = stringResource(R.string.trigger_title),
            icon = Icons.Filled.Gamepad
        ))
        add(NavItem(
            route = "leds",
            label = stringResource(R.string.led_title),
            icon = Icons.Filled.LightMode
        ))
        if (gesturesSupported) {
            add(NavItem(
                route = "gestures",
                label = stringResource(R.string.gesture_title),
                icon = Icons.Filled.TouchApp
            ))
        }
    }

    var selectedRoute by rememberSaveable { mutableStateOf(navItems[0].route) }
    var previousIndex by rememberSaveable { mutableIntStateOf(0) }

    val currentIndex = navItems.indexOfFirst { it.route == selectedRoute }
    val isNavigatingForward = currentIndex >= previousIndex

    fun onNavSelected(route: String) {
        previousIndex = navItems.indexOfFirst { it.route == selectedRoute }
        selectedRoute = route
    }

    val currentTitle = navItems.find { it.route == selectedRoute }?.label
        ?: stringResource(R.string.app_name)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = currentTitle,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )).togetherWith(
                                fadeOut(tween(150)) + scaleOut(targetScale = 0.92f)
                            )
                        },
                        label = "titleAnimation"
                    ) { title ->
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )

            AnimatedContent(
                targetState = selectedRoute,
                transitionSpec = {
                    val slideDirection = if (isNavigatingForward) 1 else -1

                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> slideDirection * fullWidth / 4 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        animationSpec = tween(250)
                    )).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -slideDirection * fullWidth / 4 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(
                            animationSpec = tween(200)
                        )
                    )
                },
                label = "screenTransition",
                modifier = Modifier.weight(1f)
            ) { route ->
                when (route) {
                    "triggers" -> TriggerSettingsScreen()
                    "leds" -> LedSettingsScreen()
                    "gestures" -> {
                        LaunchedEffect(Unit) {
                            context.startActivity(
                                Intent(context, TouchGesturesActivity::class.java)
                            )
                            // Navigate back to triggers after launching gestures
                            onNavSelected("triggers")
                        }
                        // Show triggers as fallback while launching the activity
                        TriggerSettingsScreen()
                    }
                }
            }
        }

        BottomNavBar(
            items = navItems,
            selectedRoute = selectedRoute,
            onItemSelected = { onNavSelected(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
