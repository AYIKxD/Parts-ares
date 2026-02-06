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
 * IMPORTANT: Each trigger is handled completely independently as separate touch gestures.
 * This means left and right triggers don't interfere with each other.
 * Each trigger gets its own downTime and lifecycle.
 */
class GamekeyTouchInjector(context: Context) {
    companion object {
        private const val TAG = "GamekeyTouchInjector"
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val handler = Handler(Looper.getMainLooper())
    
    // Left trigger state - completely independent
    private var leftActive = false
    private var leftX = 0f
    private var leftY = 0f
    private var leftDownTime = 0L
    private var leftPointerId = 0  // Fixed pointer ID for left
    
    // Right trigger state - completely independent
    private var rightActive = false
    private var rightX = 0f
    private var rightY = 0f
    private var rightDownTime = 0L
    private var rightPointerId = 1  // Fixed pointer ID for right
    
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
            sendSingleTouchEvent(MotionEvent.ACTION_DOWN, leftDownTime, leftDownTime, leftX, leftY, leftPointerId)
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
            sendSingleTouchEvent(MotionEvent.ACTION_UP, leftDownTime, eventTime, leftX, leftY, leftPointerId)
            
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
            sendSingleTouchEvent(MotionEvent.ACTION_DOWN, rightDownTime, rightDownTime, rightX, rightY, rightPointerId)
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
            sendSingleTouchEvent(MotionEvent.ACTION_UP, rightDownTime, eventTime, rightX, rightY, rightPointerId)
            
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
                sendSingleTouchEvent(MotionEvent.ACTION_CANCEL, leftDownTime, eventTime, leftX, leftY, leftPointerId)
                leftActive = false
                leftDownTime = 0L
            }
            if (rightActive) {
                sendSingleTouchEvent(MotionEvent.ACTION_CANCEL, rightDownTime, eventTime, rightX, rightY, rightPointerId)
                rightActive = false
                rightDownTime = 0L
            }
        }
    }

    /**
     * Send a single-touch event with one pointer.
     * Each trigger is treated as a completely separate touch sequence.
     */
    private fun sendSingleTouchEvent(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        pointerId: Int
    ) {
        val props = MotionEvent.PointerProperties().apply {
            id = pointerId
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
            1,  // Always 1 pointer per event
            arrayOf(props),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        
        sendEvent(event)
        event.recycle()
    }

    private fun sendEvent(event: MotionEvent) {
        try {
            // Use reflection to call injectInputEvent
            val method = InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(inputManager, event, 0)  // INJECT_INPUT_EVENT_MODE_ASYNC = 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject touch event", e)
        }
    }
}
