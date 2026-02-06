/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gamekey

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import org.lineageos.settings.R

class GamekeyEditorActivity : Activity() {
    companion object {
        private const val TAG = "GamekeyEditorActivity"

        private const val DEFAULT_Y_OFFSET = 0.5f
        private const val DEFAULT_X_OFFSET_LEFT = 0.25f
        private const val DEFAULT_X_OFFSET_RIGHT = 0.75f
    }

    private lateinit var circleL: View
    private lateinit var circleR: View
    private lateinit var btnSave: Button
    private lateinit var rootView: View

    private var rootWidth = 0
    private var rootHeight = 0
    private var isInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gamekey_editor)
        Log.d(TAG, "GamekeyEditorActivity created")

        initializeViews()
        setupViewTreeObserver()
        setupEventListeners()
    }

    override fun onDestroy() {
        Log.d(TAG, "GamekeyEditorActivity destroyed")
        super.onDestroy()
    }

    private fun initializeViews() {
        try {
            rootView = findViewById(R.id.editor_root)
            circleL = findViewById(R.id.circle_l)
            circleR = findViewById(R.id.circle_r)
            btnSave = findViewById(R.id.btn_save)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize views: ${e.message}", e)
            finish()
        }
    }

    private fun setupViewTreeObserver() {
        rootView.post {
            try {
                rootWidth = rootView.width
                rootHeight = rootView.height

                if (rootWidth == 0 || rootHeight == 0) {
                    Log.d(TAG, "Root view has zero dimensions, retrying...")
                    rootView.post { initializePositions() }
                } else {
                    initializePositions()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in view tree observer: ${e.message}", e)
            }
        }
    }

    private fun initializePositions() {
        try {
            circleL.post {
                restorePosition(circleL, GamekeyService.KEY_LT_X, GamekeyService.KEY_LT_Y, DEFAULT_X_OFFSET_LEFT)
            }
            circleR.post {
                restorePosition(circleR, GamekeyService.KEY_RT_X, GamekeyService.KEY_RT_Y, DEFAULT_X_OFFSET_RIGHT)
            }
            isInitialized = true
            Log.d(TAG, "Positions initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize positions: ${e.message}", e)
        }
    }

    private fun setupEventListeners() {
        setupDragListener(circleL)
        setupDragListener(circleR)

        btnSave.setOnClickListener {
            savePositionsAndFinish()
        }

        btnSave.setOnLongClickListener {
            resetToDefaultPositions()
            true
        }
    }

    private fun setupDragListener(view: View) {
        view.setOnTouchListener { _, event ->
            if (!isInitialized) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handleDragStart(view, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleDragMove(view, event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleDragEnd(view)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleDragStart(
        view: View,
        event: MotionEvent,
    ) {
        view.tag = Pair(event.rawX - view.x, event.rawY - view.y)
        view.alpha = 0.7f
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun handleDragMove(
        view: View,
        event: MotionEvent,
    ) {
        val startOffset = view.tag as? Pair<Float, Float> ?: return

        var newX = event.rawX - startOffset.first
        var newY = event.rawY - startOffset.second

        // Constrain within parent bounds
        newX = newX.coerceIn(0f, (rootWidth - view.width).toFloat())
        newY = newY.coerceIn(0f, (rootHeight - view.height).toFloat())

        view.x = newX
        view.y = newY
    }

    private fun handleDragEnd(view: View) {
        view.alpha = 1.0f
        view.tag = null
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun getViewCenter(view: View): Pair<Int, Int> =
        Pair(
            (view.x + view.width / 2f).toInt(),
            (view.y + view.height / 2f).toInt(),
        )

    private fun restorePosition(
        view: View,
        keyX: String,
        keyY: String,
        defaultFractionX: Float,
    ) {
        try {
            val prefs = getSharedPreferences(GamekeyService.PREFS_NAME, MODE_PRIVATE)
            val displayMetrics = resources.displayMetrics

            val defaultX = (displayMetrics.widthPixels * defaultFractionX).toInt()
            val defaultY = (displayMetrics.heightPixels * DEFAULT_Y_OFFSET).toInt()

            val savedX = prefs.getInt(keyX, defaultX)
            val savedY = prefs.getInt(keyY, defaultY)

            view.x = (savedX - view.width / 2f).coerceIn(0f, (rootWidth - view.width).toFloat())
            view.y = (savedY - view.height / 2f).coerceIn(0f, (rootHeight - view.height).toFloat())

            Log.d(TAG, "Restored position for ${view.id}: ($savedX, $savedY)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore position for ${view.id}: ${e.message}", e)
        }
    }

    private fun resetToDefaultPositions() {
        try {
            val displayMetrics = resources.displayMetrics

            val defaultLeftX = (displayMetrics.widthPixels * DEFAULT_X_OFFSET_LEFT).toInt()
            val defaultRightX = (displayMetrics.widthPixels * DEFAULT_X_OFFSET_RIGHT).toInt()
            val defaultY = (displayMetrics.heightPixels * DEFAULT_Y_OFFSET).toInt()

            circleL.x = (defaultLeftX - circleL.width / 2f).coerceIn(0f, (rootWidth - circleL.width).toFloat())
            circleL.y = (defaultY - circleL.height / 2f).coerceIn(0f, (rootHeight - circleL.height).toFloat())

            circleR.x = (defaultRightX - circleR.width / 2f).coerceIn(0f, (rootWidth - circleR.width).toFloat())
            circleR.y = (defaultY - circleR.height / 2f).coerceIn(0f, (rootHeight - circleR.height).toFloat())

            // Provide haptic feedback
            circleL.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            circleR.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            Log.d(TAG, "Positions reset to defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset positions: ${e.message}", e)
        }
    }

    private fun savePositionsAndFinish() {
        try {
            savePositions()
            Log.d(TAG, "Positions saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save positions: ${e.message}", e)
        } finally {
            finish()
        }
    }

    private fun savePositions() {
        val prefs = getSharedPreferences(GamekeyService.PREFS_NAME, MODE_PRIVATE).edit()

        val (leftX, leftY) = getViewCenter(circleL)
        val (rightX, rightY) = getViewCenter(circleR)

        prefs.putInt(GamekeyService.KEY_LT_X, leftX)
        prefs.putInt(GamekeyService.KEY_LT_Y, leftY)
        prefs.putInt(GamekeyService.KEY_RT_X, rightX)
        prefs.putInt(GamekeyService.KEY_RT_Y, rightY)

        prefs.apply()
        Log.d(TAG, "Saved positions - L: ($leftX, $leftY), R: ($rightX, $rightY)")
    }
}
