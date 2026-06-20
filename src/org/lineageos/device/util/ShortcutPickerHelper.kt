package org.lineageos.device.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.lineageos.device.R

class ShortcutPickerHelper(
    private val parent: ComponentActivity,
    private val listener: OnPickListener
) {
    private val packageManager: PackageManager = parent.packageManager

    interface OnPickListener {
        fun shortcutPicked(uri: String?, friendlyName: String?, bmp: Bitmap?, isApplication: Boolean)
        fun onPickCancelled()
    }

    private val appLauncher: ActivityResultLauncher<Intent> = parent.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            completeSetCustomApp(result.data!!)
        } else {
            listener.onPickCancelled()
        }
    }

    private val createShortcutLauncher: ActivityResultLauncher<Intent> = parent.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            completeSetCustomShortcut(result.data!!)
        } else {
            listener.onPickCancelled()
        }
    }

    private val pickShortcutLauncher: ActivityResultLauncher<Intent> = parent.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            processShortcut(result.data!!)
        } else {
            listener.onPickCancelled()
        }
    }

    @Suppress("DEPRECATION")
    fun pickShortcut(fullAppsOnly: Boolean = false) {
        if (fullAppsOnly) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, mainIntent)
            }
            appLauncher.launch(pickIntent)
        } else {
            val shortcutNames = arrayListOf(parent.getString(R.string.group_applications))
            val shortcutIcons = arrayListOf(
                ShortcutIconResource.fromContext(parent, android.R.drawable.sym_def_app_icon)
            )

            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_CREATE_SHORTCUT))
                putExtra(Intent.EXTRA_TITLE, parent.getText(R.string.select_custom_app_title))
                putStringArrayListExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutNames)
                putParcelableArrayListExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons)
            }
            pickShortcutLauncher.launch(pickIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun processShortcut(intent: Intent) {
        val applicationName = parent.getString(R.string.group_applications)
        val shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)

        if (applicationName == shortcutName) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, mainIntent)
            }
            appLauncher.launch(pickIntent)
        } else {
            createShortcutLauncher.launch(intent)
        }
    }

    private fun completeSetCustomApp(data: Intent) {
        listener.shortcutPicked(
            data.toUri(0),
            getFriendlyActivityName(parent, packageManager, data, false),
            null,
            true
        )
    }

    @Suppress("DEPRECATION")
    private fun completeSetCustomShortcut(data: Intent) {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        } ?: return

        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
        var appUri = intent.toUri(0)
        appUri = appUri.replace("com.android.contacts.action.QUICK_CONTACT", "android.intent.action.VIEW")

        var bmp: Bitmap? = null
        var extra: Parcelable? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
        }
        
        if (extra is Bitmap) {
            bmp = extra
        }
        if (bmp == null) {
            extra = if (android.os.Build.VERSION.SDK_INT >= 33) {
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, ShortcutIconResource::class.java)
            } else {
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
            }
            if (extra is ShortcutIconResource) {
                try {
                    val resources = packageManager.getResourcesForApplication(extra.packageName)
                    val id = resources.getIdentifier(extra.resourceName, null, null)
                    bmp = BitmapFactory.decodeResource(resources, id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        listener.shortcutPicked(
            appUri,
            getFriendlyShortcutName(parent, packageManager, intent),
            bmp,
            false
        )
    }

    companion object {
        fun getFriendlyActivityName(
            context: Context,
            pm: PackageManager,
            intent: Intent,
            labelOnly: Boolean
        ): String {
            val ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES)
            var friendlyName: String? = null

            if (ai != null) {
                friendlyName = ai.loadLabel(pm).toString()
                if (friendlyName.isEmpty() && !labelOnly) {
                    friendlyName = ai.name
                }
            }

            if (friendlyName == null || friendlyName.startsWith("#Intent;")) {
                return context.getString(com.android.internal.R.string.error_message_title)
            }
            return if (labelOnly) friendlyName else friendlyName
        }

        @Suppress("DEPRECATION")
        fun getFriendlyShortcutName(
            context: Context,
            pm: PackageManager,
            intent: Intent
        ): String {
            val activityName = getFriendlyActivityName(context, pm, intent, true)
            val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)

            if (activityName.startsWith("#Intent;")) {
                return context.getString(com.android.internal.R.string.error_message_title)
            }
            if (name != null) {
                return "$activityName: $name"
            }
            return intent.toUri(0)
        }
    }
}
