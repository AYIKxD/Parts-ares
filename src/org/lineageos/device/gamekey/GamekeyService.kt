/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.gamekey

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.lineageos.device.triggers.TriggerService
import org.lineageos.device.triggers.TriggerUtils
import org.lineageos.device.util.Utils

/**
 * Service that monitors trigger hardware via /dev/gamekey and bridges to existing XiaomiParts functionality.
 * 
 * Combines:
 * - Reference implementation: TriggersReader for hardware detection
 * - Existing XiaomiParts: TriggerUtils for sounds, actions, haptics
 * 
 * Trigger mapping activation:
 * - Both triggers must be opened within a 6-second window
 * - User must be inside an app selected in the game app list
 * - Auto-shows the trigger mapping overlay when conditions are met
 */
class GamekeyService : Service() {
    companion object {
        private const val TAG = "GamekeyService"

        // Double-click detection
        private const val DOUBLE_CLICK_TIMEOUT_MS = 300L
        private const val LONG_PRESS_DURATION_MS = 500L

        // Dual-trigger activation window (6 seconds)
        private const val TRIGGER_WINDOW_MS = 6000L

        fun startService(context: Context) {
            try {
                context.startService(Intent(context, GamekeyService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stopServiceIfRunning(context: Context) {
            try {
                context.stopService(Intent(context, GamekeyService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }

    private lateinit var triggersReader: TriggersReader
    private lateinit var touchInjector: GamekeyTouchInjector
    private var triggerUtils: TriggerUtils? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    
    // Screen state receiver
    private var screenStateReceiver: BroadcastReceiver? = null

    // Trigger state tracking
    private var leftTriggerDown = false
    private var rightTriggerDown = false
    private var leftSliderOpen = false
    private var rightSliderOpen = false
    
    // Dual-trigger activation timestamps
    private var leftSliderOpenTime = 0L
    private var rightSliderOpenTime = 0L
    private var triggerMappingAutoShown = false
    
    // Double-click detection
    private var lastLeftClickTime = 0L
    private var lastRightClickTime = 0L
    private var leftClickCount = 0
    private var rightClickCount = 0
    
    // Long-press detection
    private var leftPressStartTime = 0L
    private var rightPressStartTime = 0L
    private var leftLongPressHandled = false
    private var rightLongPressHandled = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GamekeyService starting")

        try {
            prefs = Utils.getSharedPreferences(this)
            triggerUtils = TriggerUtils.getInstance(this)
            touchInjector = GamekeyTouchInjector(this)
            
            setupTriggersReader()
            registerScreenStateReceiver()
            
            Log.i(TAG, "GamekeyService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "GamekeyService shutting down")
        
        triggersReader.stopReading()
        touchInjector.shutdown()
        unregisterScreenStateReceiver()
        handler.removeCallbacksAndMessages(null)
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun setupTriggersReader() {
        triggersReader = TriggersReader { hallLeft, hallRight, keyLeft, keyRight ->
            handler.post {
                processTriggerState(hallLeft, hallRight, keyLeft, keyRight)
            }
        }
        triggersReader.startReading()
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off - canceling touches")
                        touchInjector.cancelAll()
                        leftTriggerDown = false
                        rightTriggerDown = false
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let { unregisterReceiver(it) }
        screenStateReceiver = null
    }

    /**
     * Get trigger position from SharedPreferences, using per-app profile if available
     */
    private fun getTriggerX(isLeft: Boolean): Float {
        val pkg = Utils.getForegroundApp(this) ?: ""
        val suffix = if (pkg.isNotEmpty() && Utils.isGameApp(this)) "_$pkg" else ""
        
        val baseKey = if (isLeft) "left_trigger_x" else "right_trigger_x"
        val appKey = baseKey + suffix
        val default = if (isLeft) "540" else "540"
        
        return prefs.getString(appKey, prefs.getString(baseKey, default))?.toFloatOrNull() ?: 540f
    }

    private fun getTriggerY(isLeft: Boolean): Float {
        val pkg = Utils.getForegroundApp(this) ?: ""
        val suffix = if (pkg.isNotEmpty() && Utils.isGameApp(this)) "_$pkg" else ""
        
        val baseKey = if (isLeft) "left_trigger_y" else "right_trigger_y"
        val appKey = baseKey + suffix
        val default = if (isLeft) "700" else "1700"
        
        return prefs.getString(appKey, prefs.getString(baseKey, default))?.toFloatOrNull() ?: if (isLeft) 700f else 1700f
    }

    /**
     * Process trigger state changes from TriggersReader.
     * 
     * - hallLeft/hallRight: slider open/closed state
     * - keyLeft/keyRight: button press state
     * 
     * Dual-trigger activation: When both sliders are opened within a 6-second
     * window AND the user is inside an app from the game app list, the trigger
     * mapping overlay is automatically shown.
     */
    private fun processTriggerState(
        hallLeft: Boolean,
        hallRight: Boolean,
        keyLeft: Boolean,
        keyRight: Boolean
    ) {
        val now = SystemClock.uptimeMillis()

        // Handle left slider state change (for sounds + alert slider)
        if (hallLeft != leftSliderOpen) {
            leftSliderOpen = hallLeft
            triggerUtils?.triggerAction(true, hallLeft)
            
            if (hallLeft) {
                leftSliderOpenTime = now
            }
            
            // Alert slider: left slider triggers selected mode
            val alertMode = prefs.getString("alert_slider_mode", "disabled")
            if (alertMode != "disabled") {
                handleAlertSlider(hallLeft, alertMode)
            }
            
            Log.d(TAG, "Left slider: $hallLeft")
        }
        
        // Handle right slider state change (for sounds)
        if (hallRight != rightSliderOpen) {
            rightSliderOpen = hallRight
            triggerUtils?.triggerAction(false, hallRight)

            if (hallRight) {
                rightSliderOpenTime = now
            }

            Log.d(TAG, "Right slider: $hallRight")
        }
        
        // Dual-trigger activation check:
        // Both sliders must be open, both openings within 6-second window,
        // and user must be inside a game app from the app list
        checkDualTriggerActivation(now)
        
        // Handle button presses
        handleButtonState(true, keyLeft)
        handleButtonState(false, keyRight)
    }

    /**
     * Check if both triggers are in the open state within a 6-second window
     * and the user is inside a selected game app. If so, auto-show the
     * trigger mapping overlay.
     */
    private fun checkDualTriggerActivation(now: Long) {
        if (leftSliderOpen && rightSliderOpen) {
            // Both sliders are open — check if both opened within the 6-second window
            val timeBetweenOpenings = Math.abs(leftSliderOpenTime - rightSliderOpenTime)
            
            if (timeBetweenOpenings <= TRIGGER_WINDOW_MS) {
                // Within window — check if we're in a game app
                if (Utils.isGameApp(this) && !triggerMappingAutoShown) {
                    Log.i(TAG, "Dual-trigger activation: both triggers opened within ${timeBetweenOpenings}ms, showing overlay")
                    val triggerService = TriggerService.getInstance(this)
                    if (!triggerService.isShowing) {
                        triggerService.show()
                    }
                    triggerMappingAutoShown = true
                } else if (!Utils.isGameApp(this)) {
                    Log.d(TAG, "Dual-trigger detected but not in game app, skipping overlay")
                }
            } else {
                Log.d(TAG, "Both triggers open but outside 6s window (${timeBetweenOpenings}ms)")
            }
        } else if (!leftSliderOpen && !rightSliderOpen) {
            // Both sliders closed — reset auto-show flag so it can trigger again
            if (triggerMappingAutoShown) {
                Log.d(TAG, "Both triggers closed, resetting auto-show flag")
                triggerMappingAutoShown = false
            }
        }
    }

    private fun handleButtonState(isLeft: Boolean, pressed: Boolean) {
        val wasDown = if (isLeft) leftTriggerDown else rightTriggerDown
        val now = SystemClock.uptimeMillis()
        
        if (pressed && !wasDown) {
            // Button just pressed
            if (isLeft) {
                leftTriggerDown = true
                leftPressStartTime = now
                leftLongPressHandled = false
                
                // Double-click detection
                if (now - lastLeftClickTime < DOUBLE_CLICK_TIMEOUT_MS) {
                    leftClickCount++
                } else {
                    leftClickCount = 1
                }
                lastLeftClickTime = now
                
                // Schedule long-press check
                handler.postDelayed({
                    if (leftTriggerDown && !leftLongPressHandled) {
                        leftLongPressHandled = true
                        triggerUtils?.handleLongPress(true)
                        Log.d(TAG, "Left long press triggered")
                    }
                }, LONG_PRESS_DURATION_MS)
            } else {
                rightTriggerDown = true
                rightPressStartTime = now
                rightLongPressHandled = false
                
                // Double-click detection
                if (now - lastRightClickTime < DOUBLE_CLICK_TIMEOUT_MS) {
                    rightClickCount++
                } else {
                    rightClickCount = 1
                }
                lastRightClickTime = now
                
                // Schedule long-press check
                handler.postDelayed({
                    if (rightTriggerDown && !rightLongPressHandled) {
                        rightLongPressHandled = true
                        triggerUtils?.handleLongPress(false)
                        Log.d(TAG, "Right long press triggered")
                    }
                }, LONG_PRESS_DURATION_MS)
            }
            
            // Only inject touch events in game apps
            if (Utils.isGameApp(this)) {
                val x = getTriggerX(isLeft)
                val y = getTriggerY(isLeft)
                
                if (isLeft) {
                    touchInjector.leftTriggerDown(x, y)
                } else {
                    touchInjector.rightTriggerDown(x, y)
                }
                Log.d(TAG, "${if (isLeft) "Left" else "Right"} trigger DOWN at ($x, $y) - GAME APP")
            } else {
                Log.d(TAG, "${if (isLeft) "Left" else "Right"} trigger DOWN - NOT GAME APP, skipping touch injection")
            }
            
        } else if (!pressed && wasDown) {
            // Button just released
            if (isLeft) {
                leftTriggerDown = false
                
                // Check for double-click on release (if not long-pressed)
                if (!leftLongPressHandled && leftClickCount >= 2) {
                    triggerUtils?.handleDoubleClick(true)
                    leftClickCount = 0
                    Log.d(TAG, "Left double click triggered")
                }
            } else {
                rightTriggerDown = false
                
                // Check for double-click on release (if not long-pressed)
                if (!rightLongPressHandled && rightClickCount >= 2) {
                    triggerUtils?.handleDoubleClick(false)
                    rightClickCount = 0
                    Log.d(TAG, "Right double click triggered")
                }
            }
            
            // Only send UP if we were in a game app (touch was injected)
            if (Utils.isGameApp(this)) {
                if (isLeft) {
                    touchInjector.leftTriggerUp()
                } else {
                    touchInjector.rightTriggerUp()
                }
            }
            
            Log.d(TAG, "${if (isLeft) "Left" else "Right"} trigger UP")
        }
    }

    /**
     * Alert slider implementation with configurable modes.
     * Modes: disabled, vibrate, silent, dnd_silent, dnd_vibrate, dnd_total
     */
    private fun handleAlertSlider(sliderOpen: Boolean, mode: String?) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Provide haptic feedback for slider toggle
            triggerUtils?.triggerVibration(50) // Short 50ms pulse
            
            if (sliderOpen) {
                // Slider OPEN: Apply selected profile
                when (mode) {
                    "vibrate" -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                    "silent" -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    }
                    "dnd_silent" -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        }
                    }
                    "dnd_vibrate" -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        }
                    }
                    "dnd_total" -> {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                        }
                    }
                }
                Log.d(TAG, "Alert slider OPENED: Applied mode $mode")
            } else {
                // Slider CLOSED: Restore to normal
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                
                // Disable DND for any DND mode
                if (mode?.startsWith("dnd_") == true && notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                
                Log.d(TAG, "Alert slider CLOSED: Restored to NORMAL mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle alert slider", e)
        }
    }
}
