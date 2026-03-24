/*
 * Copyright (C) 2020 The AospExtended Project
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

package org.lineageos.xiaomiparts

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentObserver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.RemoteException
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.util.Slog
import android.view.KeyEvent

import com.android.internal.os.DeviceKeyHandler
import org.lineageos.xiaomiparts.gamekey.GamekeyService
import org.lineageos.xiaomiparts.gestures.TouchGestures
import org.lineageos.xiaomiparts.led.LedUtils
import org.lineageos.xiaomiparts.triggers.TriggerService
import org.lineageos.xiaomiparts.triggers.TriggerUtils
import org.lineageos.xiaomiparts.util.Action
import org.lineageos.xiaomiparts.util.Utils

// ─────────────────────────────────────────────────────────────────
// BootReceiver – re-enables gestures and starts services on boot
// ─────────────────────────────────────────────────────────────────
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            enableComponent(context, TouchGestures::class.java.name)
            val prefs: SharedPreferences = Utils.getSharedPreferences(context)
            TouchGestures.enableGestures(prefs.getBoolean(TouchGestures.PREF_GESTURE_ENABLE, true))
            TouchGestures.enableDt2w(prefs.getBoolean(TouchGestures.PREF_DT2W_ENABLE, true))
        }

        TriggerService.onBoot(context)
        context.startService(Intent(context, TaskService::class.java))
        GamekeyService.startService(context)
    }

    private fun enableComponent(context: Context, component: String) {
        val name = ComponentName(context, component)
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(name) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(
                name,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// KeyHandler – handles touchscreen gesture key events
// ─────────────────────────────────────────────────────────────────
class KeyHandler(private val mContext: Context) : DeviceKeyHandler {

    private val TAG = Utils.TAG
    private val DEBUG = Utils.DEBUG

    private val GESTURE_REQUEST = 1
    private val GESTURE_DOUBLE_TAP_SCANCODE = 250
    private val GESTURE_W_SCANCODE = 246
    private val GESTURE_M_SCANCODE = 247
    private val GESTURE_CIRCLE_SCANCODE = 249
    private val GESTURE_TWO_SWIPE_SCANCODE = 248
    private val GESTURE_UP_ARROW_SCANCODE = 252
    private val GESTURE_DOWN_ARROW_SCANCODE = 251
    private val GESTURE_LEFT_ARROW_SCANCODE = 254
    private val GESTURE_RIGHT_ARROW_SCANCODE = 253
    private val GESTURE_SWIPE_UP_SCANCODE = 256
    private val GESTURE_SWIPE_DOWN_SCANCODE = 255
    private val GESTURE_SWIPE_LEFT_SCANCODE = 258
    private val GESTURE_SWIPE_RIGHT_SCANCODE = 257

    private var mAppContext: Context? = Utils.getAppContext(mContext)
    private val mEventHandler = EventHandler()
    private val mVibrator: Vibrator? = mContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val mCustomSettingsObserver = CustomSettingsObserver(Handler(Looper.getMainLooper()))

    var mPrevEventTime: Long = 0
    var mLeftOpen = false; var mLeftClosed = false
    var mRightOpen = false; var mRightClosed = false

    val tr: TriggerUtils = TriggerUtils.getInstance(mAppContext!!)
    val triggerService: TriggerService = TriggerService.getInstance(mAppContext!!)

    init {
        mCustomSettingsObserver.observe()
    }

    private inner class CustomSettingsObserver(handler: Handler) : ContentObserver(handler) {
        fun observe() {
            mContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor("triggerleft"), false, this, android.os.UserHandle.USER_ALL)
            mContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor("triggerright"), false, this, android.os.UserHandle.USER_ALL)
            mContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor("trigger_sound"), false, this, android.os.UserHandle.USER_ALL)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            if (uri == Settings.System.getUriFor("trigger_sound")) {
                if (Settings.System.getInt(mContext.contentResolver, "trigger_sound", 0) == 1) {
                    tr.loadSoundResource()
                } else {
                    tr.releaseSoundResource()
                }
                return
            }
            val left = uri == Settings.System.getUriFor("triggerleft")
            val open = Utils.getIntSystem(mContext, if (left) "triggerleft" else "triggerright", -1) == 1
            tr.triggerAction(left, open)
            val now = SystemClock.uptimeMillis()
            val time = now - mPrevEventTime
            if (time < 3000 && ((mLeftOpen && !left && open) || (mRightOpen && left && open))) {
                if (DEBUG) Slog.d(TAG, "starting service")
                triggerService.show()
            } else if (time < 3000 && ((mLeftClosed && !left && !open) || (mRightClosed && left && !open))) {
                if (DEBUG) Slog.d(TAG, "stopping service")
                triggerService.hide()
            }
            mPrevEventTime = now
            mLeftOpen = left && open
            mRightOpen = !left && open
            mLeftClosed = left && !open
            mRightClosed = !left && !open
        }
    }

    private inner class EventHandler : Handler() {
        override fun handleMessage(msg: Message) {
            val event = msg.obj as KeyEvent
            var action: String? = null
            val mPref: SharedPreferences = mAppContext!!.getSharedPreferences(
                "org.lineageos.xiaomiparts_preferences",
                Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
            )
            action = when (event.scanCode) {
                GESTURE_DOUBLE_TAP_SCANCODE -> {
                    if (mPref.getBoolean(TouchGestures.PREF_DT2W_ENABLE, true)) {
                        doHapticFeedback()
                        mPref.getString(TouchGestures.PREF_GESTURE_DOUBLE_TAP, Action.ACTION_WAKE_DEVICE)
                    } else null
                }
                GESTURE_W_SCANCODE          -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_W, Action.ACTION_CAMERA) }
                GESTURE_M_SCANCODE          -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_M, Action.ACTION_MEDIA_PLAY_PAUSE) }
                GESTURE_CIRCLE_SCANCODE     -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_CIRCLE, Action.ACTION_TORCH) }
                GESTURE_TWO_SWIPE_SCANCODE  -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_TWO_SWIPE, Action.ACTION_MEDIA_PREVIOUS) }
                GESTURE_UP_ARROW_SCANCODE   -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_UP_ARROW, Action.ACTION_WAKE_DEVICE) }
                GESTURE_DOWN_ARROW_SCANCODE -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_DOWN_ARROW, Action.ACTION_VIB_SILENT) }
                GESTURE_LEFT_ARROW_SCANCODE -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_LEFT_ARROW, Action.ACTION_MEDIA_PREVIOUS) }
                GESTURE_RIGHT_ARROW_SCANCODE-> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_RIGHT_ARROW, Action.ACTION_MEDIA_NEXT) }
                GESTURE_SWIPE_UP_SCANCODE   -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_SWIPE_UP, Action.ACTION_WAKE_DEVICE) }
                GESTURE_SWIPE_DOWN_SCANCODE -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_SWIPE_DOWN, Action.ACTION_VIB_SILENT) }
                GESTURE_SWIPE_LEFT_SCANCODE -> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_SWIPE_LEFT, Action.ACTION_MEDIA_PREVIOUS) }
                GESTURE_SWIPE_RIGHT_SCANCODE-> { doHapticFeedback(); mPref.getString(TouchGestures.PREF_GESTURE_SWIPE_RIGHT, Action.ACTION_MEDIA_NEXT) }
                else -> null
            }
            if (DEBUG) Slog.d(TAG, "scancode: ${event.scanCode}, action: $action")
            if (action == null || action == Action.ACTION_NULL) return
            if (action == Action.ACTION_CAMERA) {
                Action.processAction(mContext, Action.ACTION_WAKE_DEVICE, false)
            }
            Action.processAction(mContext, action, false)
        }
    }

    private fun doHapticFeedback() {
        val enabled = Utils.getInt(mAppContext, Utils.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1) != 0
        if (enabled && mVibrator != null && mVibrator.hasVibrator()) {
            mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun handleKeyEvent(event: KeyEvent): KeyEvent {
        if (DEBUG) Slog.d(TAG, "Got KeyEvent: $event")
        if (event.device.productId == 1576) {
            return handleTriggerEvent(event)
        }
        if (event.action != KeyEvent.ACTION_UP) return event
        if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            mEventHandler.sendMessage(mEventHandler.obtainMessage(GESTURE_REQUEST).also { it.obj = event })
        }
        return event
    }

    fun handleTriggerEvent(event: KeyEvent): KeyEvent {
        val keyCode = event.keyCode
        if (keyCode in 61..64 && event.action == KeyEvent.ACTION_DOWN) {
            val isLeft = (keyCode == 61 || keyCode == 62)
            val isOpen = (keyCode == 61 || keyCode == 63)
            val setting = if (isLeft) "triggerleft" else "triggerright"
            Settings.System.putInt(mContext.contentResolver, setting, if (isOpen) 1 else 0)
            if (DEBUG) Slog.d(TAG, "Slider event: $setting=${if (isOpen) 1 else 0}")
            return event
        }
        if (!Utils.isGameApp(mContext)) {
            if (DEBUG) Slog.d(TAG, "not a game app")
            tr.onEvent(event)
            return event
        }
        return event
    }
}

// ─────────────────────────────────────────────────────────────────
// TaskService – monitors foreground app for game-mode detection
// ─────────────────────────────────────────────────────────────────
class TaskService : Service() {

    private val TAG = "TaskService"
    private val DEBUG = Utils.DEBUG

    private var mTaskComponentName: ComponentName? = null
    private lateinit var mPm: PackageManager

    private val mTaskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            Thread {
                try {
                    val focusedStack = ActivityTaskManager.getService().focusedRootTaskInfo
                    if (focusedStack?.topActivity != null) {
                        mTaskComponentName = focusedStack.topActivity
                    }
                } catch (e: Exception) {}
                try {
                    mTaskComponentName?.let { cn ->
                        val ai = mPm.getActivityInfo(cn, 0)
                        val appName = ai.applicationInfo?.packageName ?: cn.packageName
                        saveAppName(appName)
                    }
                } catch (e: PackageManager.NameNotFoundException) {}
            }.start()
        }
    }

    override fun onCreate() {
        mPm = packageManager
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to register task stack listener", e)
        }
        try {
            mTaskListener.onTaskStackChanged()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to trigger initial task stack check", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "Starting service")
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(mTaskListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unregister task stack listener", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun saveAppName(appName: String) {
        if (DEBUG) Log.d(TAG, "appName=$appName")
        Settings.System.putString(contentResolver, "appName", appName)
        val ledUtils = LedUtils.getInstance(this)
        ledUtils.play(Utils.isGameApp(this) && Utils.getSharedPreferences(this).getBoolean("led_disco", false))
    }
}
