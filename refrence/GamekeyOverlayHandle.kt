/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gamekey

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.lineageos.settings.R
import kotlin.math.abs

class GamekeyOverlayHandle : Service() {
    companion object {
        private const val TAG = "GamekeyOverlayHandle"

        private const val HANDLE_NORMAL_ALPHA = 0.3f
        private const val HANDLE_VISIBLE_ALPHA = 0.8f
        private const val HANDLE_ACTIVE_ALPHA = 1.0f

        private const val FADE_OUT_DELAY_MS = 3000L
        private const val FADE_DURATION_MS = 500L

        private const val SWIPE_DISTANCE_THRESHOLD_DP = 40f
        private const val SWIPE_VELOCITY_THRESHOLD = 800f
    }

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var handleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var handleWidthPx = 0
    private var handleHeightPx = 0
    private var swipeDistanceThresholdPx = 0

    private val handler = Handler(Looper.getMainLooper())
    private var fadeOutRunnable: Runnable? = null

    private lateinit var gestureDetector: GestureDetector
    private var isOverlayVisible = false
    private var isInteracting = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating overlay")

        windowManager = getSystemService(WindowManager::class.java)
        if (windowManager == null) {
            Log.e(TAG, "Cannot get WindowManager")
            stopSelf()
            return
        }

        initializeMetrics()
        setupGestureDetector()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlayPosition()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeMetrics() {
        val metrics = resources.displayMetrics
        val density = metrics.density

        handleWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 9f, metrics).toInt()
        handleHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, metrics).toInt()
        swipeDistanceThresholdPx = (SWIPE_DISTANCE_THRESHOLD_DP * density).toInt()

        Log.d(TAG, "Handle dimensions: ${handleWidthPx}x$handleHeightPx")
    }

    private fun setupGestureDetector() {
        gestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        isInteracting = true
                        cancelFadeOut()
                        animateHandleAlpha(HANDLE_ACTIVE_ALPHA, 100)
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        scheduleFadeOut()
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (e1 == null) return false

                        val deltaX = e2.x - e1.x
                        val deltaY = e2.y - e1.y

                        val isValidSwipe =
                            deltaX > 0 &&
                                abs(deltaX) > swipeDistanceThresholdPx &&
                                abs(deltaX) > abs(deltaY) * 2 &&
                                abs(velocityX) > SWIPE_VELOCITY_THRESHOLD

                        if (isValidSwipe) {
                            openEditorActivity()
                            return true
                        }
                        return false
                    }
                },
            )
    }

    private fun animateHandleAlpha(
        targetAlpha: Float,
        duration: Long,
    ) {
        handleView
            ?.animate()
            ?.alpha(targetAlpha)
            ?.setDuration(duration)
            ?.start()
    }

    private fun scheduleFadeOut() {
        cancelFadeOut()
        fadeOutRunnable =
            Runnable {
                if (!isInteracting && isOverlayVisible) {
                    animateHandleAlpha(HANDLE_NORMAL_ALPHA, FADE_DURATION_MS)
                }
            }
        handler.postDelayed(fadeOutRunnable!!, FADE_OUT_DELAY_MS)
    }

    private fun cancelFadeOut() {
        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        fadeOutRunnable = null
    }

    private fun showOverlay() {
        if (isOverlayVisible) return
        try {
            addOverlay()
            isOverlayVisible = true
            handleView?.alpha = HANDLE_NORMAL_ALPHA
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            stopSelf()
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return
        cancelFadeOut()
        removeOverlay()
        isOverlayVisible = false
        isInteracting = false
        stopSelf()
    }

    private fun addOverlay() {
        if (windowManager == null || rootView != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_handle, null)
        handleView = view.findViewById(R.id.handle)

        layoutParams =
            WindowManager
                .LayoutParams(
                    handleWidthPx,
                    handleHeightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.START or Gravity.TOP
                    val (xPos, yPos) = calculateFixedPosition()
                    x = xPos
                    y = yPos
                }

        windowManager!!.addView(view, layoutParams)
        setupTouchListener(view)
        rootView = view
        Log.d(TAG, "Overlay added")
    }

    private fun calculateFixedPosition(): Pair<Int, Int> {
        val screenHeight = resources.displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            0 to 0
        } else {
            0 to (screenHeight / 2 - handleHeightPx / 2)
        }
    }

    private fun updateOverlayPosition() {
        layoutParams?.let { params ->
            val (x, y) = calculateFixedPosition()
            if (params.x != x || params.y != y) {
                params.x = x
                params.y = y
                try {
                    rootView?.let { windowManager?.updateViewLayout(it, params) }
                    Log.d(TAG, "Overlay position updated")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update overlay position", e)
                }
            }
        }
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isInteracting = true
                    cancelFadeOut()
                    animateHandleAlpha(HANDLE_ACTIVE_ALPHA, 100)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isInteracting = false
                    animateHandleAlpha(HANDLE_VISIBLE_ALPHA, 200)
                    scheduleFadeOut()
                }
            }
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun openEditorActivity() {
        try {
            val intent =
                Intent(this, GamekeyEditorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            hideOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open editor", e)
        }
    }

    private fun removeOverlay() {
        try {
            rootView?.let { windowManager?.removeView(it) }
            Log.d(TAG, "Overlay removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        } finally {
            rootView = null
            handleView = null
            layoutParams = null
        }
    }

    private fun cleanup() {
        cancelFadeOut()
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
        windowManager = null
        isOverlayVisible = false
        isInteracting = false
    }
}
