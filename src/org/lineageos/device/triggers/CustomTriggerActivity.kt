package org.lineageos.device.triggers

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lineageos.device.R
import org.lineageos.device.theme.XiaomiPartsTheme
import org.lineageos.device.util.Action
import org.lineageos.device.util.ShortcutPickerHelper
import org.lineageos.device.util.Utils

class CustomTriggerActivity : ComponentActivity(), ShortcutPickerHelper.OnPickListener {

    private lateinit var picker: ShortcutPickerHelper
    private var pendingKey: String? = null
    var onShortcutPicked: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picker = ShortcutPickerHelper(this, this)

        setContent {
            XiaomiPartsTheme {
                CustomTriggerScreen(
                    onNavigateUp = { finish() },
                    onPickApp = { key ->
                        pendingKey = key
                        picker.pickShortcut(1) // Assuming 1 is some ID
                    },
                    activity = this
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ShortcutPickerHelper.REQUEST_PICK_SHORTCUT ||
                requestCode == ShortcutPickerHelper.REQUEST_PICK_APPLICATION ||
                requestCode == ShortcutPickerHelper.REQUEST_CREATE_SHORTCUT
            ) {
                picker.onActivityResult(requestCode, resultCode, data)
                return
            }
        } else {
            pendingKey = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun shortcutPicked(action: String?, description: String?, bmp: Bitmap?, isApplication: Boolean) {
        if (pendingKey != null && action != null) {
            Utils.putStringSystem(this, pendingKey, action)
            onShortcutPicked?.invoke()
            pendingKey = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomTriggerScreen(
    onNavigateUp: () -> Unit,
    onPickApp: (String) -> Unit,
    activity: CustomTriggerActivity
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var enableCustomTrigger by remember {
        mutableStateOf(Utils.getIntSystem(context, "custom_trigger_enable", 1) == 1)
    }
    var hapticFeedback by remember {
        mutableStateOf(Utils.getIntSystem(context, "custom_trigger_haptic_feedback", 1) != 0)
    }
    
    // Using simple state holder to force recomposition when shortcut is picked
    var refreshTrigger by remember { mutableStateOf(0) }
    activity.onShortcutPicked = { refreshTrigger++ }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        context.getString(R.string.custom_trigger),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Utils.putIntSystem(context, "custom_trigger_enable", 1)
                        Utils.putStringSystem(context, "custom_left_trigger_double_click", Action.ACTION_NULL)
                        Utils.putStringSystem(context, "custom_right_trigger_double_click", Action.ACTION_NULL)
                        Utils.putStringSystem(context, "custom_left_trigger_longpress", Action.ACTION_NULL)
                        Utils.putStringSystem(context, "custom_right_trigger_longpress", Action.ACTION_NULL)
                        Utils.putIntSystem(context, "custom_trigger_haptic_feedback", 1)
                        enableCustomTrigger = true
                        hapticFeedback = true
                        refreshTrigger++
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = "Reset")
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
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                org.lineageos.device.ui.SwitchFeatureCard(
                    title = context.getString(R.string.custom_trigger),
                    subtitle = context.getString(R.string.custom_trigger_summary),
                    checked = enableCustomTrigger,
                    onCheckedChange = {
                        enableCustomTrigger = it
                        Utils.putIntSystem(context, "custom_trigger_enable", if (it) 1 else 0)
                    },
                    icon = Icons.Default.Build
                )
            }
            
            item {
                org.lineageos.device.ui.SwitchFeatureCard(
                    title = context.getString(R.string.custom_trigger_haptic_feedback_title),
                    subtitle = "Provide haptic feedback when actions trigger", // Note: A subtitle is required, I'll add a placeholder or leave it blank
                    checked = hapticFeedback,
                    onCheckedChange = {
                        if (enableCustomTrigger) {
                            hapticFeedback = it
                            Utils.putIntSystem(context, "custom_trigger_haptic_feedback", if (it) 1 else 0)
                        }
                    },
                    icon = Icons.Default.Vibration
                )
            }

            // Using refreshTrigger to read from Settings.System when changed
            item {
                ActionItem(
                    title = context.getString(R.string.custom_left_trigger_double_click_title),
                    key = "custom_left_trigger_double_click",
                    enabled = enableCustomTrigger,
                    onPickApp = onPickApp,
                    refreshTrigger = refreshTrigger
                )
            }
            item {
                ActionItem(
                    title = context.getString(R.string.custom_right_trigger_double_click_title),
                    key = "custom_right_trigger_double_click",
                    enabled = enableCustomTrigger,
                    onPickApp = onPickApp,
                    refreshTrigger = refreshTrigger
                )
            }
            item {
                ActionItem(
                    title = context.getString(R.string.custom_left_trigger_longpress_title),
                    key = "custom_left_trigger_longpress",
                    enabled = enableCustomTrigger,
                    onPickApp = onPickApp,
                    refreshTrigger = refreshTrigger
                )
            }
            item {
                ActionItem(
                    title = context.getString(R.string.custom_right_trigger_longpress_title),
                    key = "custom_right_trigger_longpress",
                    enabled = enableCustomTrigger,
                    onPickApp = onPickApp,
                    refreshTrigger = refreshTrigger
                )
            }
        }
    }
}

@Composable
fun ActionItem(
    title: String,
    key: String,
    enabled: Boolean,
    onPickApp: (String) -> Unit,
    refreshTrigger: Int
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    
    val entries = context.resources.getStringArray(R.array.action_screen_off_entries)
    val values = context.resources.getStringArray(R.array.action_screen_off_values)
    
    val currentValue = remember(refreshTrigger) {
        Utils.getStringSystem(context, key, Action.ACTION_NULL)
    }
    
    val currentSummary = remember(currentValue) {
        val index = values.indexOf(currentValue)
        if (index >= 0) entries[index] else entries.firstOrNull() ?: ""
    }

    org.lineageos.device.ui.FeatureCard(
        title = title,
        subtitle = currentSummary,
        icon = Icons.Default.TouchApp,
        onClick = { if (enabled) showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(entries.size) { index ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val selectedValue = values[index]
                                    if (selectedValue == Action.ACTION_APP) {
                                        onPickApp(key)
                                    } else {
                                        Utils.putStringSystem(context, key, selectedValue)
                                    }
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentValue == values[index],
                                onClick = {
                                    val selectedValue = values[index]
                                    if (selectedValue == Action.ACTION_APP) {
                                        onPickApp(key)
                                    } else {
                                        Utils.putStringSystem(context, key, selectedValue)
                                    }
                                    showDialog = false
                                }
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(entries[index], style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(context.getString(android.R.string.cancel))
                }
            }
        )
    }
}
