/*
 * Copyright (C) 2020 The AospExtended Project
 * Licensed under the Apache License, Version 2.0
 */

package org.lineageos.xiaomiparts.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.input.InputManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import android.os.RemoteException
import android.os.SystemClock
import android.os.UserHandle
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.util.Slog
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MenuItem
import android.view.WindowManagerGlobal

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

import org.lineageos.xiaomiparts.R

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.net.URISyntaxException

// ─── Utils ────────────────────────────────────────────────────────
object Utils {
    const val TAG   = "XiaomiParts"
    const val DEBUG = true
    const val PREFERENCES = "XiaomiPartsPreferences"
    const val AMBIENT_GESTURE_HAPTIC_FEEDBACK    = "AMBIENT_GESTURE_HAPTIC_FEEDBACK"
    const val TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK = "TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK"

    @JvmStatic fun getSharedPreferences(context: Context): SharedPreferences =
        getAppContext(context).getSharedPreferences("org.lineageos.xiaomiparts_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)

    @JvmStatic fun getAppContext(context: Context): Context {
        return try { context.createPackageContext("org.lineageos.xiaomiparts", Context.CONTEXT_IGNORE_SECURITY) }
        catch (e: PackageManager.NameNotFoundException) { context }
    }

    @JvmStatic fun putStringSystem(context: Context, name: String, value: String): Boolean =
        Settings.System.putString(context.contentResolver, name, value)

    @JvmStatic fun getStringSystem(context: Context, name: String, def: String): String =
        Settings.System.getString(context.contentResolver, name) ?: def

    @JvmStatic fun getIntSystem(context: Context, name: String, def: Int): Int =
        Settings.System.getInt(context.contentResolver, name, def)

    @JvmStatic fun putIntSystem(context: Context, name: String, value: Int): Boolean =
        Settings.System.putInt(context.contentResolver, name, value)

    @JvmStatic fun getInt(context: Context?, name: String, def: Int): Int =
        getSharedPreferences(context!!).getInt(name, def)

    @JvmStatic fun putInt(context: Context, name: String, value: Int): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putInt(name, value); return editor.commit()
    }

    @JvmStatic fun isGameApp(context: Context): Boolean {
        val appName = Settings.System.getString(context.contentResolver, "appName")
        val appList = Settings.System.getString(context.contentResolver, "game_app_list")
        return appList != null && appList.contains(appName ?: "")
    }

    @JvmStatic fun writeValue(filename: String, value: String) {
        try { FileOutputStream(File(filename)).use { it.write(value.toByteArray()); it.flush() } }
        catch (e: IOException) { e.printStackTrace() }
    }

    @JvmStatic fun fileExists(filename: String): Boolean = File(filename).exists()
    @JvmStatic fun fileWritable(filename: String): Boolean = fileExists(filename) && File(filename).canWrite()

    @JvmStatic fun readLine(filename: String): String? {
        return try { BufferedReader(FileReader(filename), 1024).use { it.readLine() } }
        catch (e: IOException) { null }
    }

    @JvmStatic fun getFileValueAsBoolean(filename: String, defValue: Boolean): Boolean {
        val v = readLine(filename) ?: return defValue
        return v != "0"
    }

    @JvmStatic fun getFileValue(filename: String, defValue: String): String =
        readLine(filename) ?: defValue

    @JvmStatic fun readOneLine(fileName: String): String? {
        return try { BufferedReader(FileReader(fileName), 512).use { it.readLine() } }
        catch (e: IOException) { Slog.e(TAG, "Could not read from file $fileName", e); null }
    }

    @JvmStatic fun getTriggerEnabled(position: Int): Boolean {
        val file = File("/dev/gamekey")
        if (!file.exists()) return false
        val b = ByteArray(4)
        try { FileInputStream(file).use { it.read(b) } }
        catch (e: IOException) { e.printStackTrace() }
        return b[position] == 1.toByte()
    }

    @JvmStatic fun writeLine(fileName: String, value: String): Boolean {
        return try { FileOutputStream(fileName).use { it.write(value.toByteArray()); it.flush() }; true }
        catch (e: IOException) { Slog.e(TAG, "Could not write to file $fileName", e); false }
    }
}

// ─── Action ───────────────────────────────────────────────────────
object Action {
    const val ACTION_HOME                  = "home"
    const val ACTION_BACK                  = "back"
    const val ACTION_SEARCH                = "search"
    const val ACTION_VOICE_SEARCH          = "voice_search"
    const val ACTION_MENU                  = "menu"
    const val ACTION_MENU_BIG              = "menu_big"
    const val ACTION_POWER                 = "power"
    const val ACTION_NOTIFICATIONS         = "notifications"
    const val ACTION_RECENTS               = "recents"
    const val ACTION_SCREENSHOT            = "screenshot"
    const val ACTION_IME                   = "ime"
    const val ACTION_LAST_APP              = "lastapp"
    const val ACTION_KILL                  = "kill"
    const val ACTION_ASSIST                = "assist"
    const val ACTION_VIB                   = "ring_vib"
    const val ACTION_SILENT                = "ring_silent"
    const val ACTION_VIB_SILENT            = "ring_vib_silent"
    const val ACTION_POWER_MENU            = "power_menu"
    const val ACTION_TORCH                 = "torch"
    const val ACTION_EXPANDED_DESKTOP      = "expanded_desktop"
    const val ACTION_THEME_SWITCH          = "theme_switch"
    const val ACTION_KEYGUARD_SEARCH       = "keyguard_search"
    const val ACTION_PIE                   = "pie"
    const val ACTION_NAVBAR                = "nav_bar"
    const val ACTION_IME_NAVIGATION_LEFT   = "ime_nav_left"
    const val ACTION_IME_NAVIGATION_RIGHT  = "ime_nav_right"
    const val ACTION_IME_NAVIGATION_UP     = "ime_nav_up"
    const val ACTION_IME_NAVIGATION_DOWN   = "ime_nav_down"
    const val ACTION_CAMERA                = "camera"
    const val ACTION_MEDIA_PREVIOUS        = "media_previous"
    const val ACTION_MEDIA_NEXT            = "media_next"
    const val ACTION_MEDIA_PLAY_PAUSE      = "media_play_pause"
    const val ACTION_WAKE_DEVICE           = "wake_device"
    const val ACTION_NULL                  = "null"
    const val ACTION_APP                   = "app"
    const val ICON_EMPTY                   = "empty"
    const val SYSTEM_ICON_IDENTIFIER       = "system_shortcut="
    const val ACTION_DELIMITER             = "|"

    @JvmStatic private var sTorchEnabled = false
    @JvmStatic val TAG = Utils.TAG

    @JvmStatic fun processAction(context: Context, action: String?, isLongpress: Boolean) {
        if (action == null || action == ACTION_NULL) { Slog.w(TAG, "action is null"); return }
        var isKeyguardShowing = false
        try { isKeyguardShowing = WindowManagerGlobal.getWindowManagerService()?.isKeyguardLocked() ?: false }
        catch (e: RemoteException) { Slog.w(TAG, "Error getting window manager service", e) }

        when (action) {
            ACTION_HOME        -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_HOME, isLongpress); return }
            ACTION_BACK        -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_BACK, isLongpress); return }
            ACTION_SEARCH      -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_SEARCH, isLongpress); return }
            ACTION_MENU, ACTION_MENU_BIG -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_MENU, isLongpress); return }
            ACTION_IME_NAVIGATION_LEFT  -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_DPAD_LEFT, isLongpress); return }
            ACTION_IME_NAVIGATION_RIGHT -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_DPAD_RIGHT, isLongpress); return }
            ACTION_IME_NAVIGATION_UP    -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_DPAD_UP, isLongpress); return }
            ACTION_IME_NAVIGATION_DOWN  -> { triggerVirtualKeypress(context, KeyEvent.KEYCODE_DPAD_DOWN, isLongpress); return }
            ACTION_TORCH -> {
                try {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    for (id in cm.cameraIdList) {
                        val ch = cm.getCameraCharacteristics(id)
                        val flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        val orient = ch.get(CameraCharacteristics.LENS_FACING)
                        if (flash == true && orient == CameraCharacteristics.LENS_FACING_BACK) {
                            cm.setTorchMode(id, !sTorchEnabled); sTorchEnabled = !sTorchEnabled; break
                        }
                    }
                } catch (e: CameraAccessException) {}
                return
            }
            ACTION_POWER -> { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).goToSleep(SystemClock.uptimeMillis()); return }
            ACTION_IME -> {
                if (isKeyguardShowing) return
                context.sendBroadcastAsUser(Intent("android.settings.SHOW_INPUT_METHOD_PICKER"), UserHandle(UserHandle.USER_CURRENT))
                return
            }
            ACTION_VOICE_SEARCH -> {
                val intent = Intent(Intent.ACTION_SEARCH_LONG_PRESS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                try { startActivity(context, intent) } catch (e: ActivityNotFoundException) { Slog.e(TAG, "No activity to handle assist long press.", e) }
                return
            }
            ACTION_VIB -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.ringerMode != AudioManager.RINGER_MODE_VIBRATE) {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(50)
                } else { am.ringerMode = AudioManager.RINGER_MODE_NORMAL; ToneGenerator(AudioManager.STREAM_NOTIFICATION, (ToneGenerator.MAX_VOLUME * 0.85).toInt()).startTone(ToneGenerator.TONE_PROP_BEEP) }
                return
            }
            ACTION_SILENT -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.ringerMode != AudioManager.RINGER_MODE_SILENT) am.ringerMode = AudioManager.RINGER_MODE_SILENT
                else { am.ringerMode = AudioManager.RINGER_MODE_NORMAL; ToneGenerator(AudioManager.STREAM_NOTIFICATION, (ToneGenerator.MAX_VOLUME * 0.85).toInt()).startTone(ToneGenerator.TONE_PROP_BEEP) }
                return
            }
            ACTION_VIB_SILENT -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (am.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL   -> { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE; (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(50) }
                    AudioManager.RINGER_MODE_VIBRATE  -> am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    else -> { am.ringerMode = AudioManager.RINGER_MODE_NORMAL; ToneGenerator(AudioManager.STREAM_NOTIFICATION, (ToneGenerator.MAX_VOLUME * 0.85).toInt()).startTone(ToneGenerator.TONE_PROP_BEEP) }
                }
                return
            }
            ACTION_CAMERA -> { startActivity(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, null)); return }
            ACTION_MEDIA_PREVIOUS  -> { dispatchMediaKeyWithWakeLock(KeyEvent.KEYCODE_MEDIA_PREVIOUS, context); return }
            ACTION_MEDIA_NEXT      -> { dispatchMediaKeyWithWakeLock(KeyEvent.KEYCODE_MEDIA_NEXT, context); return }
            ACTION_MEDIA_PLAY_PAUSE-> { dispatchMediaKeyWithWakeLock(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, context); return }
            ACTION_WAKE_DEVICE -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isScreenOn) pm.wakeUp(SystemClock.uptimeMillis())
                return
            }
            else -> {
                val intent = try { Intent.parseUri(action, 0) } catch (e: URISyntaxException) { Slog.e(TAG, "URISyntaxException: [$action]"); return }
                startActivity(context, intent)
            }
        }
    }

    @JvmStatic fun isActionKeyEvent(action: String) =
        action in listOf(ACTION_HOME, ACTION_BACK, ACTION_SEARCH, ACTION_MENU, ACTION_MENU_BIG, ACTION_NULL)

    @JvmStatic private fun startActivity(context: Context, intent: Intent?) {
        intent ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivityAsUser(intent, UserHandle(UserHandle.USER_CURRENT))
    }

    @JvmStatic private fun dispatchMediaKeyWithWakeLock(keycode: Int, context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keycode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP,   keycode, 0))
    }

    @JvmStatic fun triggerVirtualKeypress(context: Context, keyCode: Int, longpress: Boolean) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val now = SystemClock.uptimeMillis()
        val isDpad = keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        var downFlags = if (isDpad) KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                        else KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY
        if (longpress) downFlags = downFlags or KeyEvent.FLAG_LONG_PRESS
        im.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, downFlags, InputDevice.SOURCE_KEYBOARD),
            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        im.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, downFlags, InputDevice.SOURCE_KEYBOARD),
            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
    }
}

// ─── AppListActivity ──────────────────────────────────────────────
class AppListActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, AppList())
            .commit()
    }
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}

// ─── ShortcutPickerHelper ─────────────────────────────────────────
class ShortcutPickerHelper(private val mParent: Activity, private val mListener: OnPickListener) {
    companion object {
        const val REQUEST_PICK_SHORTCUT     = 100
        const val REQUEST_PICK_APPLICATION  = 101
        const val REQUEST_CREATE_SHORTCUT   = 102

        @JvmStatic fun getFriendlyActivityName(context: Context, pm: PackageManager, intent: Intent, labelOnly: Boolean): String {
            val ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES)
            val friendly = ai?.loadLabel(pm)?.toString()
            if (friendly == null || friendly.startsWith("#Intent;")) {
                return context.resources.getString(com.android.internal.R.string.error_message_title)
            }
            return if (!labelOnly) (friendly ?: ai?.name ?: intent.toUri(0)) else (friendly ?: intent.toUri(0))
        }

        @JvmStatic fun getFriendlyShortcutName(context: Context, pm: PackageManager, intent: Intent): String {
            val actName = getFriendlyActivityName(context, pm, intent, true)
            val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            if (actName.startsWith("#Intent;")) return context.resources.getString(com.android.internal.R.string.error_message_title)
            return if (name != null) "$actName: $name" else name ?: intent.toUri(0)
        }
    }

    interface OnPickListener {
        fun shortcutPicked(uri: String?, friendlyName: String?, bmp: Bitmap?, isApplication: Boolean)
    }

    private val mPackageManager = mParent.packageManager
    private var lastFragmentId = 0

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_PICK_APPLICATION -> completeSetCustomApp(data)
            REQUEST_CREATE_SHORTCUT  -> completeSetCustomShortcut(data)
            REQUEST_PICK_SHORTCUT    -> processShortcut(data, REQUEST_PICK_APPLICATION, REQUEST_CREATE_SHORTCUT)
        }
    }

    @JvmOverloads
    fun pickShortcut(fragmentId: Int, fullAppsOnly: Boolean = false) {
        lastFragmentId = fragmentId
        if (fullAppsOnly) {
            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
            startFragmentOrActivity(pickIntent, REQUEST_PICK_APPLICATION)
        } else {
            val bundle = Bundle().apply {
                putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, arrayListOf(mParent.getString(R.string.group_applications)))
                putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, arrayListOf(
                    Intent.ShortcutIconResource.fromContext(mParent, android.R.drawable.sym_def_app_icon)))
            }
            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_CREATE_SHORTCUT))
                putExtra(Intent.EXTRA_TITLE, mParent.getText(R.string.select_custom_app_title))
                putExtras(bundle)
            }
            startFragmentOrActivity(pickIntent, REQUEST_PICK_SHORTCUT)
        }
    }

    private fun startFragmentOrActivity(pickIntent: Intent, requestCode: Int) {
        if (lastFragmentId == 0) {
            mParent.startActivityForResult(pickIntent, requestCode)
        } else {
            val frag = mParent.fragmentManager.findFragmentById(lastFragmentId)
            if (frag != null) mParent.startActivityFromFragment(frag, pickIntent, requestCode)
        }
    }

    private fun processShortcut(intent: Intent, appCode: Int, shortcutCode: Int) {
        val appName  = mParent.resources.getString(R.string.group_applications)
        val shortcut = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        if (appName == shortcut) {
            startFragmentOrActivity(Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }, appCode)
        } else startFragmentOrActivity(intent, shortcutCode)
    }

    private fun completeSetCustomApp(data: Intent) {
        mListener.shortcutPicked(data.toUri(0), getFriendlyActivityName(mParent, mPackageManager, data, false), null, true)
    }

    private fun completeSetCustomShortcut(data: Intent) {
        val intent = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)!!
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
        var appUri = intent.toUri(0).replace("com.android.contacts.action.QUICK_CONTACT", "android.intent.action.VIEW")
        var bmp: Bitmap? = (data.getParcelableExtra<Parcelable>(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap)
        if (bmp == null) {
            val res = data.getParcelableExtra<Parcelable>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
            if (res is Intent.ShortcutIconResource) {
                try {
                    val resources = mPackageManager.getResourcesForApplication(res.packageName)
                    bmp = BitmapFactory.decodeResource(resources, resources.getIdentifier(res.resourceName, null, null))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        mListener.shortcutPicked(appUri, getFriendlyShortcutName(mParent, mPackageManager, intent), bmp, false)
    }
}
