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
import android.view.MotionEvent

/**
 * Injects touch events for trigger buttons during gaming.
 * Uses InputManager.injectInputEvent() to simulate screen touches.
 */
class GamekeyTouchInjector(context: Context) {
    companion object {
        private const val TAG = "GamekeyTouchInjector"
    }

    private val inputManager: InputManager = context.getSystemService(InputManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var gestureDownTime = 0L

    // Track current active trigger touches
    private var leftActive = false
    private var leftId = 0
    private var leftX = 0f
    private var leftY = 0f

    private var rightActive = false
    private var rightId = 1
    private var rightX = 0f
    private var rightY = 0f

    /**
     * Press left trigger at (x,y)
     */
    fun leftTriggerDown(x: Float, y: Float) {
        synchronized(lock) {
            if (leftActive) return
            leftActive = true
            leftX = x
            leftY = y
            
            val eventTime = SystemClock.uptimeMillis()
            if (!rightActive) {
                // First finger down
                gestureDownTime = eventTime
                sendEvent(createEvent(MotionEvent.ACTION_DOWN, eventTime))
            } else {
                // Second finger down
                sendEvent(createEvent(MotionEvent.ACTION_POINTER_DOWN or (getPointerIndex(true) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), eventTime))
            }
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
            if (rightActive) {
                // Other finger still down - pointer up
                sendEvent(createEvent(MotionEvent.ACTION_POINTER_UP or (getPointerIndex(true) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), eventTime))
            } else {
                // Last finger up
                sendEvent(createEvent(MotionEvent.ACTION_UP, eventTime))
                gestureDownTime = 0L
            }
            leftActive = false
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
            
            val eventTime = SystemClock.uptimeMillis()
            if (!leftActive) {
                // First finger down
                gestureDownTime = eventTime
                sendEvent(createEvent(MotionEvent.ACTION_DOWN, eventTime))
            } else {
                // Second finger down
                sendEvent(createEvent(MotionEvent.ACTION_POINTER_DOWN or (getPointerIndex(false) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), eventTime))
            }
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
            if (leftActive) {
                // Other finger still down - pointer up
                sendEvent(createEvent(MotionEvent.ACTION_POINTER_UP or (getPointerIndex(false) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), eventTime))
            } else {
                // Last finger up
                sendEvent(createEvent(MotionEvent.ACTION_UP, eventTime))
                gestureDownTime = 0L
            }
            rightActive = false
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
            if (leftActive || rightActive) {
                val eventTime = SystemClock.uptimeMillis()
                sendEvent(createEvent(MotionEvent.ACTION_CANCEL, eventTime))
                leftActive = false
                rightActive = false
                gestureDownTime = 0L
            }
        }
    }

    private fun getPointerIndex(isLeft: Boolean): Int {
        // When both are active, left is index 0, right is index 1
        return if (isLeft) 0 else if (leftActive) 1 else 0
    }

    private fun createEvent(action: Int, eventTime: Long): MotionEvent {
        synchronized(lock) {
            val pointerProps = mutableListOf<MotionEvent.PointerProperties>()
            val pointerCoords = mutableListOf<MotionEvent.PointerCoords>()
            
            // Always add active pointers (or the one being released for UP events)
            val actionMasked = action and MotionEvent.ACTION_MASK
            val isUpAction = actionMasked == MotionEvent.ACTION_UP || 
                            actionMasked == MotionEvent.ACTION_POINTER_UP ||
                            actionMasked == MotionEvent.ACTION_CANCEL

            // For UP events, include the pointer being released
            // For other events, include only active pointers
            val includeLeft = leftActive || (isUpAction && !leftActive && !rightActive)
            val includeRight = rightActive || (isUpAction && !leftActive && !rightActive && !includeLeft)
            
            // Determine which pointers to include
            val shouldIncludeLeft = if (isUpAction) {
                // For single UP, include the last active pointer
                // For POINTER_UP, include all current pointers
                leftActive || (actionMasked == MotionEvent.ACTION_UP && gestureDownTime != 0L)
            } else {
                leftActive
            }
            
            val shouldIncludeRight = if (isUpAction) {
                rightActive || (actionMasked == MotionEvent.ACTION_UP && gestureDownTime != 0L && !shouldIncludeLeft)
            } else {
                rightActive
            }

            if (leftActive || (actionMasked == MotionEvent.ACTION_UP && !rightActive)) {
                pointerProps.add(MotionEvent.PointerProperties().apply {
                    id = leftId
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                })
                pointerCoords.add(MotionEvent.PointerCoords().apply {
                    x = leftX
                    y = leftY
                    pressure = if (isUpAction && !rightActive) 0f else 1f
                    size = 0.1f
                })
            }

            if (rightActive || (actionMasked == MotionEvent.ACTION_UP && !leftActive && pointerProps.isEmpty())) {
                pointerProps.add(MotionEvent.PointerProperties().apply {
                    id = rightId
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                })
                pointerCoords.add(MotionEvent.PointerCoords().apply {
                    x = rightX
                    y = rightY
                    pressure = if (isUpAction && !leftActive) 0f else 1f
                    size = 0.1f
                })
            }

            // Safety check - must have at least 1 pointer
            if (pointerProps.isEmpty()) {
                Log.w(TAG, "No pointers for event, using fallback")
                pointerProps.add(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                })
                pointerCoords.add(MotionEvent.PointerCoords().apply {
                    x = leftX.takeIf { it != 0f } ?: rightX
                    y = leftY.takeIf { it != 0f } ?: rightY
                    pressure = 0f
                    size = 0.1f
                })
            }

            return MotionEvent.obtain(
                gestureDownTime.takeIf { it != 0L } ?: eventTime,
                eventTime,
                action,
                pointerProps.size,
                pointerProps.toTypedArray(),
                pointerCoords.toTypedArray(),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        }
    }

    private fun sendEvent(event: MotionEvent) {
        handler.post {
            try {
                inputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
            } catch (e: Exception) {
                Log.e(TAG, "Error injecting event: ${e.message}")
            } finally {
                event.recycle()
            }
        }
    }
}
