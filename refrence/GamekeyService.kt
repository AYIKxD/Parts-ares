/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gamekey

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import org.lineageos.settings.utils.TriggersReader

class GamekeyService : Service() {
    companion object {
        private const val TAG = "GamekeyService"
        private const val SUB_TAG = "GamekeyInput"
        const val PREFS_NAME = "gamekey_prefs"

        const val KEY_LT_X = "lt_x"
        const val KEY_LT_Y = "lt_y"
        const val KEY_RT_X = "rt_x"
        const val KEY_RT_Y = "rt_y"

        private const val DEFAULT_Y_OFFSET = 0.5f
        private const val DEFAULT_X_OFFSET_LEFT = 0.25f
        private const val DEFAULT_X_OFFSET_RIGHT = 0.75f
        private const val RESTART_DELAY_MS = 1000L

        fun startService(context: Context) {
            try {
                context.startService(Intent(context, GamekeyService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }
    }

    private lateinit var triggersReader: TriggersReader
    private lateinit var touchInjector: GamekeyTouchInjector
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var displayMetrics: DisplayMetrics
    private var screenStateReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    private var leftTriggerDown = false
    private var rightTriggerDown = false
    private var isOverlayVisible = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing")

        try {
            val inputManager = getSystemService(INPUT_SERVICE) as InputManager
            touchInjector = GamekeyTouchInjector(inputManager)
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            displayMetrics = resources.displayMetrics

            setupTriggerReader()
            registerScreenStateReceiver()
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Shutting down")

        triggersReader.stopReading()
        touchInjector.shutdown()
        hideOverlay()
        unregisterScreenStateReceiver()
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    private fun setupTriggerReader() {
        triggersReader =
            TriggersReader { hallLeft, hallRight, keyLeft, keyRight ->
                val shouldShowOverlay = hallLeft && hallRight
                handler.post {
                    if (shouldShowOverlay != isOverlayVisible) {
                        if (shouldShowOverlay) showOverlay() else hideOverlay()
                    }
                    processTriggerInputs(keyLeft, keyRight)
                }
            }
        triggersReader.startReading()
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            Log.d(TAG, "Screen off - stopping")
                            touchInjector.cancelAll()
                            leftTriggerDown = false
                            rightTriggerDown = false
                            stopSelf()
                        }
                        Intent.ACTION_USER_PRESENT -> {
                            Log.d(TAG, "Device unlocked - restarting")
                            handler.postDelayed({
                                startService(context)
                            }, RESTART_DELAY_MS)
                        }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let { unregisterReceiver(it) }
        screenStateReceiver = null
    }

    private fun showOverlay() {
        try {
            startService(Intent(this, GamekeyOverlayHandle::class.java))
            isOverlayVisible = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        try {
            stopService(Intent(this, GamekeyOverlayHandle::class.java))
            isOverlayVisible = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }

    private fun processTriggerInputs(
        leftPressed: Boolean,
        rightPressed: Boolean,
    ) {
        handleLeftTrigger(leftPressed)
        handleRightTrigger(rightPressed)
    }

    private fun handleLeftTrigger(pressed: Boolean) {
        when {
            pressed && !leftTriggerDown -> {
                val (x, y) = getLeftTriggerCoordinates()
                touchInjector.leftTriggerDown(x, y)
                leftTriggerDown = true
                Log.d(SUB_TAG, "Left DOWN at ($x, $y)")
            }
            !pressed && leftTriggerDown -> {
                touchInjector.leftTriggerUp()
                leftTriggerDown = false
                Log.d(SUB_TAG, "Left UP")
            }
        }
    }

    private fun handleRightTrigger(pressed: Boolean) {
        when {
            pressed && !rightTriggerDown -> {
                val (x, y) = getRightTriggerCoordinates()
                touchInjector.rightTriggerDown(x, y)
                rightTriggerDown = true
                Log.d(SUB_TAG, "Right DOWN at ($x, $y)")
            }
            !pressed && rightTriggerDown -> {
                touchInjector.rightTriggerUp()
                rightTriggerDown = false
                Log.d(SUB_TAG, "Right UP")
            }
        }
    }

    private fun getLeftTriggerCoordinates(): Pair<Float, Float> {
        val defaultX = displayMetrics.widthPixels * DEFAULT_X_OFFSET_LEFT
        val defaultY = displayMetrics.heightPixels * DEFAULT_Y_OFFSET
        return sharedPreferences.getInt(KEY_LT_X, defaultX.toInt()).toFloat() to
            sharedPreferences.getInt(KEY_LT_Y, defaultY.toInt()).toFloat()
    }

    private fun getRightTriggerCoordinates(): Pair<Float, Float> {
        val defaultX = displayMetrics.widthPixels * DEFAULT_X_OFFSET_RIGHT
        val defaultY = displayMetrics.heightPixels * DEFAULT_Y_OFFSET
        return sharedPreferences.getInt(KEY_RT_X, defaultX.toInt()).toFloat() to
            sharedPreferences.getInt(KEY_RT_Y, defaultY.toInt()).toFloat()
    }
}
