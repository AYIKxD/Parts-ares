/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospextended.device.gamekey

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import org.aospextended.device.triggers.TriggerUtils

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
    
    // Screen state receiver
    private var screenStateReceiver: BroadcastReceiver? = null

    // Trigger state tracking
    private var leftTriggerDown = false
    private var rightTriggerDown = false
    private var slidersOpen = false
    
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
        val newSlidersOpen = hallLeft && hallRight
        
        // Handle slider state change (for sounds)
        if (newSlidersOpen != slidersOpen) {
            slidersOpen = newSlidersOpen
            if (hallLeft) {
                triggerUtils?.triggerAction(true, hallLeft)
            }
            if (hallRight) {
                triggerUtils?.triggerAction(false, hallRight)
            }
            Log.d(TAG, "Sliders state: $slidersOpen")
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
            
            // Inject touch down for gaming
            val displayMetrics = resources.displayMetrics
            if (isLeft) {
                val x = displayMetrics.widthPixels * 0.25f
                val y = displayMetrics.heightPixels * 0.5f
                touchInjector.leftTriggerDown(x, y)
            } else {
                val x = displayMetrics.widthPixels * 0.75f
                val y = displayMetrics.heightPixels * 0.5f
                touchInjector.rightTriggerDown(x, y)
            }
            
            Log.d(TAG, "${if (isLeft) "Left" else "Right"} trigger DOWN")
            
        } else if (!pressed && wasDown) {
            // Button just released
            if (isLeft) {
                leftTriggerDown = false
                touchInjector.leftTriggerUp()
                
                // Check for double-click on release (if not long-pressed)
                if (!leftLongPressHandled && leftClickCount >= 2) {
                    triggerUtils?.handleDoubleClick(true)
                    leftClickCount = 0
                    Log.d(TAG, "Left double click triggered")
                }
            } else {
                rightTriggerDown = false
                touchInjector.rightTriggerUp()
                
                // Check for double-click on release (if not long-pressed)
                if (!rightLongPressHandled && rightClickCount >= 2) {
                    triggerUtils?.handleDoubleClick(false)
                    rightClickCount = 0
                    Log.d(TAG, "Right double click triggered")
                }
            }
            
            Log.d(TAG, "${if (isLeft) "Left" else "Right"} trigger UP")
        }
    }
}
