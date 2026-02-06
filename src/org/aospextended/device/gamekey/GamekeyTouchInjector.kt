/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospextended.device.gamekey

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Injects touch events for trigger buttons.
 * 
 * Each trigger is handled completely independently as separate touch gestures.
 * Both triggers use pointer ID 0 since they are independent touch sequences.
 */
class GamekeyTouchInjector(context: Context) {
    companion object {
        private const val TAG = "GamekeyTouchInjector"
        // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2 (more reliable for games)
        private const val INJECT_MODE = 2
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val handler = Handler(Looper.getMainLooper())
    
    // Left trigger state - completely independent
    private var leftActive = false
    private var leftX = 0f
    private var leftY = 0f
    private var leftDownTime = 0L
    
    // Right trigger state - completely independent
    private var rightActive = false
    private var rightX = 0f
    private var rightY = 0f
    private var rightDownTime = 0L
    
    private val lock = Any()

    /**
     * Press left trigger at (x,y)
     */
    fun leftTriggerDown(x: Float, y: Float) {
        synchronized(lock) {
            if (leftActive) return
            leftActive = true
            leftX = x
            leftY = y
            leftDownTime = SystemClock.uptimeMillis()
            
            // Send independent DOWN event for left trigger
            injectTouchEvent(MotionEvent.ACTION_DOWN, leftDownTime, leftDownTime, leftX, leftY)
            Log.d(TAG, "Left DOWN at ($x, $y)")
        }
    }

    /**
     * Release left trigger
     */
    fun leftTriggerUp() {
        synchronized(lock) {
            if (!leftActive) return
            
            val eventTime = SystemClock.uptimeMillis()
            // Send independent UP event for left trigger
            injectTouchEvent(MotionEvent.ACTION_UP, leftDownTime, eventTime, leftX, leftY)
            
            leftActive = false
            leftDownTime = 0L
            Log.d(TAG, "Left UP")
        }
    }

    /**
     * Press right trigger at (x,y)
     */
    fun rightTriggerDown(x: Float, y: Float) {
        synchronized(lock) {
            if (rightActive) return
            rightActive = true
            rightX = x
            rightY = y
            rightDownTime = SystemClock.uptimeMillis()
            
            // Send independent DOWN event for right trigger
            injectTouchEvent(MotionEvent.ACTION_DOWN, rightDownTime, rightDownTime, rightX, rightY)
            Log.d(TAG, "Right DOWN at ($x, $y)")
        }
    }

    /**
     * Release right trigger
     */
    fun rightTriggerUp() {
        synchronized(lock) {
            if (!rightActive) return
            
            val eventTime = SystemClock.uptimeMillis()
            // Send independent UP event for right trigger
            injectTouchEvent(MotionEvent.ACTION_UP, rightDownTime, eventTime, rightX, rightY)
            
            rightActive = false
            rightDownTime = 0L
            Log.d(TAG, "Right UP")
        }
    }

    fun shutdown() {
        synchronized(lock) {
            cancelAll()
            handler.removeCallbacksAndMessages(null)
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            val eventTime = SystemClock.uptimeMillis()
            if (leftActive) {
                injectTouchEvent(MotionEvent.ACTION_CANCEL, leftDownTime, eventTime, leftX, leftY)
                leftActive = false
                leftDownTime = 0L
            }
            if (rightActive) {
                injectTouchEvent(MotionEvent.ACTION_CANCEL, rightDownTime, eventTime, rightX, rightY)
                rightActive = false
                rightDownTime = 0L
            }
        }
    }

    /**
     * Inject a touch event using InputManager.
     * Uses pointer ID 0 for all events (standard single-touch).
     */
    private fun injectTouchEvent(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float
    ) {
        val props = MotionEvent.PointerProperties().apply {
            id = 0  // Always use pointer ID 0 for single-touch events
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1.0f
            size = 1.0f
        }
        
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,  // 1 pointer
            arrayOf(props),
            arrayOf(coords),
            0,  // metaState
            0,  // buttonState
            1f, // xPrecision
            1f, // yPrecision
            0,  // deviceId (0 = virtual)
            0,  // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN,
            0   // flags
        )
        
        try {
            // Use reflection to call injectInputEvent
            val method = InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            val result = method.invoke(inputManager, event, INJECT_MODE)
            Log.d(TAG, "Injected touch event: action=$action x=$x y=$y result=$result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject touch event", e)
        } finally {
            event.recycle()
        }
    }
}
