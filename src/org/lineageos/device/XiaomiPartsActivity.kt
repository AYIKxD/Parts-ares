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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// Gestures removed
import org.lineageos.device.led.LedUtils
import org.lineageos.device.theme.XiaomiPartsTheme
import org.lineageos.device.triggers.CustomTriggerActivity
import org.lineageos.device.triggers.TriggerUtils
import org.lineageos.device.util.AppListActivity
import org.lineageos.device.util.Utils

class XiaomiPartsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Utils.getSharedPreferences(this)

        setContent {
            XiaomiPartsTheme {
                XiaomiPartsScreen(prefs, onNavigateUp = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XiaomiPartsScreen(prefs: SharedPreferences, onNavigateUp: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "XiaomiParts", 
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold) 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            item { CategoryHeader(context.getString(R.string.triggers_category_title)) }
            item {
                var sound by rememberBooleanPreference(prefs, "trigger_sound", false)
                SwitchSettingsItem(
                    title = context.getString(R.string.trigger_sound_title),
                    summary = context.getString(R.string.trigger_sound_summary),
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        Settings.System.putInt(context.contentResolver, "trigger_sound", if (it) 1 else 0)
                    },
                    icon = { Icon(Icons.Default.VolumeUp, null) }
                )
            }
            item {
                var soundType by rememberStringPreference(prefs, "trigger_sound_type", "classic")
                val entries = context.resources.getStringArray(R.array.trigger_sound_type_entries)
                val values = context.resources.getStringArray(R.array.trigger_sound_type_values)
                ListSettingsItem(
                    title = context.getString(R.string.trigger_sound_type_title),
                    summary = entries.getOrNull(values.indexOf(soundType)) ?: "classic",
                    icon = { Icon(Icons.Default.MusicNote, null) },
                    entries = entries,
                    values = values,
                    currentValue = soundType,
                    onValueChange = {
                        soundType = it
                        Settings.System.putString(context.contentResolver, "trigger_sound_type", it)
                        TriggerUtils.getInstance(context).triggerAction(true, true)
                    }
                )
            }
            item {
                SettingsItem(
                    title = context.getString(R.string.gaming_apps_title),
                    summary = context.getString(R.string.gaming_apps_summary),
                    icon = { Icon(Icons.Default.Gamepad, null) },
                    onClick = {
                        context.startActivity(Intent(context, AppListActivity::class.java))
                    }
                )
            }
            item {
                SettingsItem(
                    title = context.getString(R.string.custom_trigger),
                    summary = context.getString(R.string.custom_trigger_summary),
                    icon = { Icon(Icons.Default.Build, null) },
                    onClick = {
                        context.startActivity(Intent(context, CustomTriggerActivity::class.java))
                    }
                )
            }
            item {
                SettingsItem(
                    title = context.getString(R.string.trigger_mapping_manager_title),
                    summary = context.getString(R.string.trigger_mapping_manager_summary),
                    icon = { Icon(Icons.Default.Settings, null) },
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
            item {
                var alertMode by rememberStringPreference(prefs, "alert_slider_mode", "disabled")
                val entries = context.resources.getStringArray(R.array.alert_slider_mode_entries)
                val values = context.resources.getStringArray(R.array.alert_slider_mode_values)
                ListSettingsItem(
                    title = context.getString(R.string.alert_slider_mode_title),
                    summary = entries.getOrNull(values.indexOf(alertMode)) ?: "Disabled",
                    icon = { Icon(Icons.Default.Tune, null) },
                    entries = entries,
                    values = values,
                    currentValue = alertMode,
                    onValueChange = { alertMode = it }
                )
            }

            item { CategoryHeader(context.getString(R.string.leds_category_title)) }
            item {
                var ledDisco by rememberBooleanPreference(prefs, "led_disco", false)
                var ledInGames by rememberBooleanPreference(prefs, "led_in_games", false)
                
                SwitchSettingsItem(
                    title = context.getString(R.string.led_disco_title),
                    summary = context.getString(R.string.led_disco_summary),
                    checked = ledDisco,
                    onCheckedChange = {
                        ledDisco = it
                        LedUtils.getInstance(context).play(it)
                        if (!it) { ledInGames = false }
                    },
                    icon = { Icon(Icons.Default.Lightbulb, null) }
                )
            }
            item {
                var ledDisco by rememberBooleanPreference(prefs, "led_disco", false)
                var ledInGames by rememberBooleanPreference(prefs, "led_in_games", false)
                
                SwitchSettingsItem(
                    title = context.getString(R.string.led_in_games_title),
                    summary = context.getString(R.string.led_in_games_summary),
                    checked = ledInGames,
                    onCheckedChange = {
                        ledInGames = it
                        LedUtils.getInstance(context).play(!it || (it && ledDisco))
                    },
                    enabled = ledDisco,
                    icon = { Icon(Icons.Default.VideogameAsset, null) }
                )
            }
            item {
                var ledInCalls by rememberState(
                    initial = Utils.getIntSystem(context, "led_in_calls", 1) == 1
                )
                SwitchSettingsItem(
                    title = context.getString(R.string.led_in_calls_title),
                    summary = context.getString(R.string.led_in_calls_summary),
                    checked = ledInCalls,
                    onCheckedChange = {
                        ledInCalls = it
                        Utils.putIntSystem(context, "led_in_calls", if (it) 1 else 0)
                    },
                    icon = { Icon(Icons.Default.Call, null) }
                )
            }
        }
    }
}

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchSettingsItem(
    title: String,
    summary: String? = null,
    icon: @Composable (() -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

@Composable
fun ListSettingsItem(
    title: String,
    summary: String,
    icon: @Composable (() -> Unit)? = null,
    entries: Array<String>,
    values: Array<String>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = icon,
        modifier = Modifier.clickable { expanded = true }
    )

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEachIndexed { index, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(values[index])
                                    expanded = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentValue == values[index],
                                onClick = {
                                    onValueChange(values[index])
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
