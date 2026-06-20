package org.lineageos.device

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lineageos.device.led.LedUtils
import org.lineageos.device.theme.XiaomiPartsTheme
import org.lineageos.device.triggers.CustomTriggerActivity
import org.lineageos.device.triggers.TriggerUtils
import org.lineageos.device.ui.FeatureCard
import org.lineageos.device.ui.LEDsIllustration
import org.lineageos.device.ui.MadeWithLoveFooter
import org.lineageos.device.ui.TriggersIllustration
import org.lineageos.device.ui.VisualCard
import org.lineageos.device.ui.SwitchFeatureCard
import org.lineageos.device.util.AppListActivity
import org.lineageos.device.util.Utils

enum class Screen {
    Dashboard, Triggers, LedEffects
}

class XiaomiPartsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Utils.getSharedPreferences(this)

        setContent {
            XiaomiPartsTheme {
                var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
                
                BackHandler(enabled = currentScreen != Screen.Dashboard) {
                    currentScreen = Screen.Dashboard
                }

                XiaomiPartsApp(
                    prefs = prefs,
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it },
                    onNavigateUp = { 
                        if (currentScreen == Screen.Dashboard) {
                            finish()
                        } else {
                            currentScreen = Screen.Dashboard
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiaomiPartsApp(
    prefs: SharedPreferences, 
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onNavigateUp: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    val title = when (currentScreen) {
        Screen.Dashboard -> "XiaomiParts"
        Screen.Triggers -> context.getString(R.string.triggers_category_title)
        Screen.LedEffects -> context.getString(R.string.leds_category_title)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold) 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen, 
            modifier = Modifier.padding(innerPadding)
        ) { screen ->
            when (screen) {
                Screen.Dashboard -> DashboardScreen(prefs, onNavigate)
                Screen.Triggers -> TriggersScreen(prefs)
                Screen.LedEffects -> LedEffectsScreen(prefs)
            }
        }
    }
}

@Composable
fun DashboardScreen(prefs: SharedPreferences, onNavigate: (Screen) -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VisualCard(
                title = context.getString(R.string.triggers_category_title),
                onClick = { onNavigate(Screen.Triggers) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                illustration = { TriggersIllustration() }
            )
            
            VisualCard(
                title = context.getString(R.string.leds_category_title),
                onClick = { onNavigate(Screen.LedEffects) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                illustration = { LEDsIllustration() }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "System",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        var alertMode by rememberStringPreference(prefs, "alert_slider_mode", "disabled")
        val entries = context.resources.getStringArray(R.array.alert_slider_mode_entries)
        val values = context.resources.getStringArray(R.array.alert_slider_mode_values)
        
        var expanded by remember { mutableStateOf(false) }

        FeatureCard(
            title = context.getString(R.string.alert_slider_mode_title),
            subtitle = entries.getOrNull(values.indexOf(alertMode)) ?: "Disabled",
            icon = Icons.Default.Tune,
            onClick = { expanded = true },
            illustrationColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = { Text(context.getString(R.string.alert_slider_mode_title)) },
                text = {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        alertMode = values[index]
                                        expanded = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = alertMode == values[index],
                                    onClick = {
                                        alertMode = values[index]
                                        expanded = false
                                    }
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(entry, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { expanded = false }) {
                        Text(context.getString(android.R.string.cancel))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        MadeWithLoveFooter()
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
fun TriggersScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            var sound by rememberBooleanPreference(prefs, "trigger_sound", false)
            SwitchFeatureCard(
                title = context.getString(R.string.trigger_sound_title),
                subtitle = context.getString(R.string.trigger_sound_summary),
                checked = sound,
                onCheckedChange = {
                    sound = it
                    Settings.System.putInt(context.contentResolver, "trigger_sound", if (it) 1 else 0)
                },
                icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp
            )
        }
        item {
            var soundType by rememberStringPreference(prefs, "trigger_sound_type", "classic")
            val entries = context.resources.getStringArray(R.array.trigger_sound_type_entries)
            val values = context.resources.getStringArray(R.array.trigger_sound_type_values)
            var expanded by remember { mutableStateOf(false) }

            FeatureCard(
                title = context.getString(R.string.trigger_sound_type_title),
                subtitle = entries.getOrNull(values.indexOf(soundType)) ?: "classic",
                icon = Icons.Default.MusicNote,
                onClick = { expanded = true }
            )

            if (expanded) {
                AlertDialog(
                    onDismissRequest = { expanded = false },
                    title = { Text(context.getString(R.string.trigger_sound_type_title)) },
                    text = {
                        Column {
                            entries.forEachIndexed { index, entry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            soundType = values[index]
                                            Settings.System.putString(context.contentResolver, "trigger_sound_type", values[index])
                                            TriggerUtils.getInstance(context).triggerAction(true, true)
                                            expanded = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = soundType == values[index],
                                        onClick = {
                                            soundType = values[index]
                                            Settings.System.putString(context.contentResolver, "trigger_sound_type", values[index])
                                            TriggerUtils.getInstance(context).triggerAction(true, true)
                                            expanded = false
                                        }
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(entry, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { expanded = false }) {
                            Text(LocalContext.current.getString(android.R.string.cancel))
                        }
                    }
                )
            }
        }
        item {
            FeatureCard(
                title = context.getString(R.string.gaming_apps_title),
                subtitle = context.getString(R.string.gaming_apps_summary),
                icon = Icons.Default.Gamepad,
                onClick = {
                    context.startActivity(Intent(context, AppListActivity::class.java))
                }
            )
        }
        item {
            FeatureCard(
                title = context.getString(R.string.custom_trigger),
                subtitle = context.getString(R.string.custom_trigger_summary),
                icon = Icons.Default.Build,
                onClick = {
                    context.startActivity(Intent(context, CustomTriggerActivity::class.java))
                }
            )
        }
        item {
            FeatureCard(
                title = context.getString(R.string.trigger_mapping_manager_title),
                subtitle = context.getString(R.string.trigger_mapping_manager_summary),
                icon = Icons.Default.Settings,
                onClick = {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.trigger_mapping_dialog_title)
                        .setMessage(android.text.Html.fromHtml(context.getString(R.string.trigger_mapping_dialog_message), android.text.Html.FROM_HTML_MODE_COMPACT))
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(R.string.trigger_mapping_reset_all) { _, _ ->
                            val editor = prefs.edit()
                            prefs.all.keys.forEach { key ->
                                if (key.startsWith("left_trigger_x") || key.startsWith("left_trigger_y") ||
                                    key.startsWith("right_trigger_x") || key.startsWith("right_trigger_y")
                                ) {
                                    editor.remove(key)
                                }
                            }
                            editor.apply()
                            Toast.makeText(context, R.string.trigger_mapping_reset_toast, Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
            )
        }
    }
}

@Composable
fun LedEffectsScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            var ledDisco by rememberBooleanPreference(prefs, "led_disco", false)
            var ledInGames by rememberBooleanPreference(prefs, "led_in_games", false)
            
            SwitchFeatureCard(
                title = context.getString(R.string.led_disco_title),
                subtitle = context.getString(R.string.led_disco_summary),
                checked = ledDisco,
                onCheckedChange = {
                    ledDisco = it
                    LedUtils.getInstance(context).play(it)
                    if (!it) { ledInGames = false }
                },
                icon = Icons.Default.Lightbulb
            )
        }
        item {
            var ledDisco by rememberBooleanPreference(prefs, "led_disco", false)
            var ledInGames by rememberBooleanPreference(prefs, "led_in_games", false)
            
            SwitchFeatureCard(
                title = context.getString(R.string.led_in_games_title),
                subtitle = context.getString(R.string.led_in_games_summary),
                checked = ledInGames,
                onCheckedChange = {
                    ledInGames = it
                    LedUtils.getInstance(context).play(!it || (it && ledDisco))
                },
                icon = Icons.Default.VideogameAsset
            )
        }
        item {
            var ledInCalls by rememberState(
                initial = Utils.getIntSystem(context, "led_in_calls", 1) == 1
            )
            SwitchFeatureCard(
                title = context.getString(R.string.led_in_calls_title),
                subtitle = context.getString(R.string.led_in_calls_summary),
                checked = ledInCalls,
                onCheckedChange = {
                    ledInCalls = it
                    Utils.putIntSystem(context, "led_in_calls", if (it) 1 else 0)
                },
                icon = Icons.Default.Call
            )
        }
    }
}

@Composable
fun rememberBooleanPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    return object : MutableState<Boolean> {
        override var value: Boolean
            get() = state.value
            set(value) {
                state.value = value
                prefs.edit().putBoolean(key, value).apply()
            }

        override fun component1() = value
        override fun component2() = { newValue: Boolean -> value = newValue }
    }
}

@Composable
fun rememberStringPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: String
): MutableState<String> {
    val state = remember { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    return object : MutableState<String> {
        override var value: String
            get() = state.value
            set(value) {
                state.value = value
                prefs.edit().putString(key, value).apply()
            }

        override fun component1() = value
        override fun component2() = { newValue: String -> value = newValue }
    }
}

@Composable
fun <T> rememberState(initial: T): MutableState<T> = remember { mutableStateOf(initial) }
