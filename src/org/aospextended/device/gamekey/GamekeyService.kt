/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospextended.device.gamekey

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
import org.aospextended.device.triggers.TriggerService
import org.aospextended.device.triggers.TriggerUtils
import org.aospextended.device.util.Utils

/**
 * Service that monitors trigger hardware via /dev/gamekey and bridges to existing XiaomiParts functionality.
 * 
 * Combines:
 * - Reference implementation: TriggersReader for hardware detection
 * - Existing XiaomiParts: TriggerUtils for sounds, actions, haptics
 */
class GamekeyService : Service() {
    companion object {
        private const val TAG = "GamekeyService"

        // Double-click detection
        private const val DOUBLE_CLICK_TIMEOUT_MS = 300L
        private const val LONG_PRESS_DURATION_MS = 500L

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
     * Get trigger position from SharedPreferences
     */
    private fun getTriggerX(isLeft: Boolean): Float {
        val key = if (isLeft) "left_trigger_x" else "right_trigger_x"
        val default = if (isLeft) "540" else "540"
        return prefs.getString(key, default)?.toFloatOrNull() ?: 540f
    }

    private fun getTriggerY(isLeft: Boolean): Float {
        val key = if (isLeft) "left_trigger_y" else "right_trigger_y"
        val default = if (isLeft) "700" else "1700"
        return prefs.getString(key, default)?.toFloatOrNull() ?: if (isLeft) 700f else 1700f
    }

    /**
     * Process trigger state changes from TriggersReader.
     * 
     * - hallLeft/hallRight: slider open/closed state
     * - keyLeft/keyRight: button press state
     */
    private fun processTriggerState(
        hallLeft: Boolean,
        hallRight: Boolean,
        keyLeft: Boolean,
        keyRight: Boolean
    ) {
        // Handle left slider state change (for sounds + alert slider)
        if (hallLeft != leftSliderOpen) {
            leftSliderOpen = hallLeft
            triggerUtils?.triggerAction(true, hallLeft)
            
            // Alert slider: left slider toggles vibrate + DND (only if enabled)
            if (prefs.getBoolean("alert_slider_enabled", false)) {
                handleAlertSlider(hallLeft)
            }
            
            Log.d(TAG, "Left slider: $hallLeft")
        }
        
        // Handle right slider state change (for sounds)
        if (hallRight != rightSliderOpen) {
            rightSliderOpen = hallRight
            triggerUtils?.triggerAction(false, hallRight)
            Log.d(TAG, "Right slider: $hallRight")
        }
        
        // Auto-show trigger overlay when BOTH sliders are open
        val bothSlidersOpen = leftSliderOpen && rightSliderOpen
        if (bothSlidersOpen) {
            try {
                val triggerService = TriggerService.getInstance(this)
                triggerService.init(this)
                if (!triggerService.isShowing()) {
                    triggerService.show()
                    Log.d(TAG, "Both sliders open - showing trigger overlay")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show trigger overlay", e)
            }
        } else if (!leftSliderOpen && !rightSliderOpen) {
            // Auto-hide when BOTH sliders are closed
            try {
                val triggerService = TriggerService.getInstance(this)
                if (triggerService.isShowing()) {
                    triggerService.hide()
                    Log.d(TAG, "Both sliders closed - hiding trigger overlay")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide trigger overlay", e)
            }
        }
        
        // Handle button presses
        handleButtonState(true, keyLeft)
        handleButtonState(false, keyRight)
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
     * Alert slider implementation: left slider toggles vibrate + DND mode
     * - Slider OPEN: Set to vibrate mode + enable DND
     * - Slider CLOSED: Set to normal ringer mode + disable DND
     */
    private fun handleAlertSlider(sliderOpen: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (sliderOpen) {
                // Slider opened = vibrate + DND
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                
                // Enable DND if we have permission
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
                
                Log.d(TAG, "Alert slider: VIBRATE + DND mode enabled")
            } else {
                // Slider closed = normal mode
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                
                // Disable DND if we have permission
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                
                Log.d(TAG, "Alert slider: NORMAL mode enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle alert slider", e)
        }
    }
}

