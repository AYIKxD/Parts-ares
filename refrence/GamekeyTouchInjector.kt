/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gamekey

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent

class GamekeyTouchInjector(
    context: Context,
) {
    companion object {
        private const val TAG = "GamekeyTouchInjector"
    }

    private val inputManager: InputManager =
        context.getSystemService(InputManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var gestureDownTime = 0L

    // Track current active trigger touches
    private var leftId: Int? = null
    private var leftX = 0f
    private var leftY = 0f

    private var rightId: Int? = null
    private var rightX = 0f
    private var rightY = 0f

    /*
     * Press left trigger at (x,y)
     */
    fun leftTriggerDown(
        x: Float,
        y: Float,
    ) {
        synchronized(lock) {
            if (leftId != null) return
            leftId = nextAvailablePointerId()
            leftX = x
            leftY = y
            updateTouchState()
        }
    }

    /*
     * Release left trigger
     */
    fun leftTriggerUp() {
        synchronized(lock) {
            if (leftId == null) return
            leftId = null
            updateTouchState()
        }
    }

    /*
     * Press right trigger at (x,y)
     */
    fun rightTriggerDown(
        x: Float,
        y: Float,
    ) {
        synchronized(lock) {
            if (rightId != null) return
            rightId = nextAvailablePointerId()
            rightX = x
            rightY = y
            updateTouchState()
        }
    }

    fun rightTriggerUp() {
        synchronized(lock) {
            if (rightId == null) return
            rightId = null
            updateTouchState()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            cancelAll()
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun cancelAll() {
        synchronized(lock) {
            if (leftId != null || rightId != null) {
                sendCancelEvent()
                leftId = null
                rightId = null
                gestureDownTime = 0L
            }
        }
    }

    private fun updateTouchState() {
        synchronized(lock) {
            val activeIds = listOfNotNull(leftId, rightId)
            val eventTime = SystemClock.uptimeMillis()

            when {
                activeIds.isEmpty() -> {
                    if (gestureDownTime != 0L) {
                        sendEvent(createTouchEvent(MotionEvent.ACTION_UP, eventTime))
                        gestureDownTime = 0L
                    }
                }
                activeIds.size == 1 && gestureDownTime == 0L -> {
                    gestureDownTime = eventTime
                    sendEvent(createTouchEvent(MotionEvent.ACTION_DOWN, eventTime))
                }
                activeIds.size == 2 && gestureDownTime != 0L -> {
                    sendEvent(createTouchEvent(MotionEvent.ACTION_POINTER_DOWN, eventTime))
                }
                else -> {
                    sendEvent(createTouchEvent(MotionEvent.ACTION_MOVE, eventTime))
                }
            }
        }
    }

    private fun createTouchEvent(
        action: Int,
        eventTime: Long,
    ): MotionEvent {
        synchronized(lock) {
            val pointerProps = mutableListOf<MotionEvent.PointerProperties>()
            val pointerCoords = mutableListOf<MotionEvent.PointerCoords>()

            leftId?.let {
                pointerProps.add(
                    MotionEvent.PointerProperties().apply {
                        id = it
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    },
                )
                pointerCoords.add(
                    MotionEvent.PointerCoords().apply {
                        x = leftX
                        y = leftY
                        pressure = 1f
                        size = 0.1f
                    },
                )
            }

            rightId?.let {
                pointerProps.add(
                    MotionEvent.PointerProperties().apply {
                        id = it
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    },
                )
                pointerCoords.add(
                    MotionEvent.PointerCoords().apply {
                        x = rightX
                        y = rightY
                        pressure = 1f
                        size = 0.1f
                    },
                )
            }

            val finalAction =
                when {
                    pointerProps.isEmpty() -> MotionEvent.ACTION_UP
                    pointerProps.size == 1 && action == MotionEvent.ACTION_DOWN -> MotionEvent.ACTION_DOWN
                    pointerProps.size == 2 && action == MotionEvent.ACTION_POINTER_DOWN ->
                        MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    else -> MotionEvent.ACTION_MOVE
                }

            return MotionEvent.obtain(
                gestureDownTime,
                eventTime,
                finalAction,
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
                0,
            )
        }
    }

    private fun sendCancelEvent() {
        synchronized(lock) {
            val eventTime = SystemClock.uptimeMillis()
            val pointerProps = mutableListOf<MotionEvent.PointerProperties>()
            val pointerCoords = mutableListOf<MotionEvent.PointerCoords>()

            leftId?.let {
                pointerProps.add(
                    MotionEvent.PointerProperties().apply {
                        id = it
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    },
                )
                pointerCoords.add(
                    MotionEvent.PointerCoords().apply {
                        x = leftX
                        y = leftY
                        pressure = 0f
                        size = 0f
                    },
                )
            }

            rightId?.let {
                pointerProps.add(
                    MotionEvent.PointerProperties().apply {
                        id = it
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    },
                )
                pointerCoords.add(
                    MotionEvent.PointerCoords().apply {
                        x = rightX
                        y = rightY
                        pressure = 0f
                        size = 0f
                    },
                )
            }

            if (pointerProps.isEmpty()) return

            val cancelEvent =
                MotionEvent.obtain(
                    gestureDownTime,
                    eventTime,
                    MotionEvent.ACTION_CANCEL,
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
                    0,
                )

            sendEvent(cancelEvent)
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

    /*
     * find next available pointer ID
     */
    private fun nextAvailablePointerId(): Int {
        val used = listOfNotNull(leftId, rightId)
        var id = 0
        while (used.contains(id)) id++
        return id
    }
}
