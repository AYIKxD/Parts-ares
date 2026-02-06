/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospextended.device.gamekey

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Injects multi-touch events for trigger buttons.
 * 
 * Properly supports pressing both triggers simultaneously by using
 * ACTION_POINTER_DOWN/UP for the second touch while maintaining the first.
 */
class GamekeyTouchInjector(context: Context) {
    companion object {
        private const val TAG = "GamekeyTouchInjector"
        // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2
        private const val INJECT_MODE = 2
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    
    // Pointer ID 0 = left trigger, Pointer ID 1 = right trigger
    private var leftActive = false
    private var leftX = 0f
    private var leftY = 0f
    private var leftDownTime = 0L
    
    private var rightActive = false
    private var rightX = 0f
    private var rightY = 0f
    private var rightDownTime = 0L
    
    // Shared down time for multi-touch sequence
    private var multiTouchDownTime = 0L
    
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
            
            val now = SystemClock.uptimeMillis()
            
            if (!rightActive) {
                // First finger down - use ACTION_DOWN
                multiTouchDownTime = now
                leftDownTime = now
                injectSingleTouch(MotionEvent.ACTION_DOWN, multiTouchDownTime, now, leftX, leftY, 0)
            } else {
                // Second finger down - use ACTION_POINTER_DOWN with index 0 (left is pointer 0)
                leftDownTime = now
                injectMultiTouch(
                    MotionEvent.ACTION_POINTER_DOWN or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    multiTouchDownTime, now
                )
            }
            Log.d(TAG, "Left DOWN at ($x, $y), rightActive=$rightActive")
        }
    }

    /**
     * Release left trigger
     */
    fun leftTriggerUp() {
        synchronized(lock) {
            if (!leftActive) return
            
            val now = SystemClock.uptimeMillis()
            
            if (rightActive) {
                // Left going up while right still down - use ACTION_POINTER_UP
                injectMultiTouch(
                    MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    multiTouchDownTime, now
                )
            } else {
                // Only left was down - use ACTION_UP
                injectSingleTouch(MotionEvent.ACTION_UP, leftDownTime, now, leftX, leftY, 0)
            }
            
            leftActive = false
            leftDownTime = 0L
            Log.d(TAG, "Left UP, rightActive=$rightActive")
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
            
            val now = SystemClock.uptimeMillis()
            
            if (!leftActive) {
                // First finger down - use ACTION_DOWN
                multiTouchDownTime = now
                rightDownTime = now
                injectSingleTouch(MotionEvent.ACTION_DOWN, multiTouchDownTime, now, rightX, rightY, 1)
            } else {
                // Second finger down - use ACTION_POINTER_DOWN with index 1 (right is pointer 1)
                rightDownTime = now
                injectMultiTouch(
                    MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    multiTouchDownTime, now
                )
            }
            Log.d(TAG, "Right DOWN at ($x, $y), leftActive=$leftActive")
        }
    }

    /**
     * Release right trigger
     */
    fun rightTriggerUp() {
        synchronized(lock) {
            if (!rightActive) return
            
            val now = SystemClock.uptimeMillis()
            
            if (leftActive) {
                // Right going up while left still down - use ACTION_POINTER_UP
                injectMultiTouch(
                    MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    multiTouchDownTime, now
                )
            } else {
                // Only right was down - use ACTION_UP
                injectSingleTouch(MotionEvent.ACTION_UP, rightDownTime, now, rightX, rightY, 1)
            }
            
            rightActive = false
            rightDownTime = 0L
            Log.d(TAG, "Right UP, leftActive=$leftActive")
        }
    }

    fun shutdown() {
        synchronized(lock) {
            cancelAll()
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            if (leftActive || rightActive) {
                if (leftActive && rightActive) {
                    // Both active - cancel multi-touch
                    injectMultiTouch(MotionEvent.ACTION_CANCEL, multiTouchDownTime, now)
                } else if (leftActive) {
                    injectSingleTouch(MotionEvent.ACTION_CANCEL, leftDownTime, now, leftX, leftY, 0)
                } else {
                    injectSingleTouch(MotionEvent.ACTION_CANCEL, rightDownTime, now, rightX, rightY, 1)
                }
                leftActive = false
                rightActive = false
                leftDownTime = 0L
                rightDownTime = 0L
            }
        }
    }

    /**
     * Inject single-touch event (when only one trigger is active)
     */
    private fun injectSingleTouch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float, pointerId: Int) {
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
            downTime, eventTime, action,
            1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        
        injectEvent(event)
        event.recycle()
    }

    /**
     * Inject multi-touch event (when both triggers are active or one is leaving)
     */
    private fun injectMultiTouch(action: Int, downTime: Long, eventTime: Long) {
        // Always include both pointers in the event
        val propsArray = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0  // Left trigger
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1  // Right trigger
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        
        val coordsArray = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = leftX
                y = leftY
                pressure = 1.0f
                size = 1.0f
            },
            MotionEvent.PointerCoords().apply {
                x = rightX
                y = rightY
                pressure = 1.0f
                size = 1.0f
            }
        )
        
        val event = MotionEvent.obtain(
            downTime, eventTime, action,
            2, propsArray, coordsArray,
            0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        
        injectEvent(event)
        event.recycle()
    }

    private fun injectEvent(event: MotionEvent) {
        try {
            val method = InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            val result = method.invoke(inputManager, event, INJECT_MODE)
            Log.d(TAG, "Injected: action=${event.actionMasked} pointers=${event.pointerCount} result=$result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject touch event", e)
        }
    }
}
