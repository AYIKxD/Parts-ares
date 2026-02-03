/*
 * Copyright (C) 2020 The AospExtended Project
 * Copyright (C) 2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aospextended.device.triggers;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.aospextended.device.util.Utils;

/**
 * Modern touch injector that uses InputManager.injectInputEvent() instead of
 * kernel-level touch driver manipulation. This approach is more reliable
 * and compatible with Android 16+.
 */
public class TouchInjector {
    private static final String TAG = "TouchInjector";
    private static final boolean DEBUG = Utils.DEBUG;

    private final InputManager mInputManager;
    private final Handler mHandler;
    private final Object mLock = new Object();
    private final Context mContext;
    private final SharedPreferences mPrefs;

    // Track current active trigger touches
    private Integer mLeftId = null;
    private float mLeftX = 0f;
    private float mLeftY = 0f;

    private Integer mRightId = null;
    private float mRightX = 0f;
    private float mRightY = 0f;

    private long mGestureDownTime = 0L;

    // Default positions (will be overridden by saved preferences)
    private static final float DEFAULT_Y_OFFSET = 0.5f;
    private static final float DEFAULT_X_OFFSET_LEFT = 0.25f;
    private static final float DEFAULT_X_OFFSET_RIGHT = 0.75f;

    public TouchInjector(Context context) {
        mContext = context;
        mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mPrefs = Utils.getSharedPreferences(context);
    }

    /**
     * Handle trigger press/release event
     * @param isLeft true for left trigger, false for right
     * @param isDown true for press, false for release
     */
    public void handleTrigger(boolean isLeft, boolean isDown) {
        if (isLeft) {
            if (isDown) {
                leftTriggerDown();
            } else {
                leftTriggerUp();
            }
        } else {
            if (isDown) {
                rightTriggerDown();
            } else {
                rightTriggerUp();
            }
        }
    }

    /**
     * Press left trigger at configured position
     */
    public void leftTriggerDown() {
        synchronized (mLock) {
            if (mLeftId != null) return;
            mLeftId = nextAvailablePointerId();
            float[] coords = getLeftTriggerCoordinates();
            mLeftX = coords[0];
            mLeftY = coords[1];
            if (DEBUG) Slog.d(TAG, "Left DOWN at (" + mLeftX + ", " + mLeftY + ")");
            updateTouchState();
        }
    }

    /**
     * Release left trigger
     */
    public void leftTriggerUp() {
        synchronized (mLock) {
            if (mLeftId == null) return;
            mLeftId = null;
            if (DEBUG) Slog.d(TAG, "Left UP");
            updateTouchState();
        }
    }

    /**
     * Press right trigger at configured position
     */
    public void rightTriggerDown() {
        synchronized (mLock) {
            if (mRightId != null) return;
            mRightId = nextAvailablePointerId();
            float[] coords = getRightTriggerCoordinates();
            mRightX = coords[0];
            mRightY = coords[1];
            if (DEBUG) Slog.d(TAG, "Right DOWN at (" + mRightX + ", " + mRightY + ")");
            updateTouchState();
        }
    }

    /**
     * Release right trigger
     */
    public void rightTriggerUp() {
        synchronized (mLock) {
            if (mRightId == null) return;
            mRightId = null;
            if (DEBUG) Slog.d(TAG, "Right UP");
            updateTouchState();
        }
    }

    /**
     * Shutdown and release all touches
     */
    public void shutdown() {
        synchronized (mLock) {
            cancelAll();
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Cancel all active touches
     */
    public void cancelAll() {
        synchronized (mLock) {
            if (mLeftId != null || mRightId != null) {
                sendCancelEvent();
                mLeftId = null;
                mRightId = null;
                mGestureDownTime = 0L;
            }
        }
    }

    private void updateTouchState() {
        synchronized (mLock) {
            int activeCount = (mLeftId != null ? 1 : 0) + (mRightId != null ? 1 : 0);
            long eventTime = SystemClock.uptimeMillis();

            if (activeCount == 0) {
                // All fingers up
                if (mGestureDownTime != 0L) {
                    sendEvent(createTouchEvent(MotionEvent.ACTION_UP, eventTime));
                    mGestureDownTime = 0L;
                }
            } else if (activeCount == 1 && mGestureDownTime == 0L) {
                // First finger down
                mGestureDownTime = eventTime;
                sendEvent(createTouchEvent(MotionEvent.ACTION_DOWN, eventTime));
            } else if (activeCount == 2 && mGestureDownTime != 0L) {
                // Second finger down
                sendEvent(createTouchEvent(MotionEvent.ACTION_POINTER_DOWN, eventTime));
            } else {
                // Move event
                sendEvent(createTouchEvent(MotionEvent.ACTION_MOVE, eventTime));
            }
        }
    }

    private MotionEvent createTouchEvent(int action, long eventTime) {
        synchronized (mLock) {
            int pointerCount = (mLeftId != null ? 1 : 0) + (mRightId != null ? 1 : 0);
            if (pointerCount == 0) {
                pointerCount = 1; // For ACTION_UP we need at least one pointer
            }

            MotionEvent.PointerProperties[] pointerProps = 
                new MotionEvent.PointerProperties[pointerCount];
            MotionEvent.PointerCoords[] pointerCoords = 
                new MotionEvent.PointerCoords[pointerCount];

            int index = 0;

            if (mLeftId != null) {
                pointerProps[index] = new MotionEvent.PointerProperties();
                pointerProps[index].id = mLeftId;
                pointerProps[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

                pointerCoords[index] = new MotionEvent.PointerCoords();
                pointerCoords[index].x = mLeftX;
                pointerCoords[index].y = mLeftY;
                pointerCoords[index].pressure = 1f;
                pointerCoords[index].size = 0.1f;
                index++;
            }

            if (mRightId != null) {
                pointerProps[index] = new MotionEvent.PointerProperties();
                pointerProps[index].id = mRightId;
                pointerProps[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

                pointerCoords[index] = new MotionEvent.PointerCoords();
                pointerCoords[index].x = mRightX;
                pointerCoords[index].y = mRightY;
                pointerCoords[index].pressure = 1f;
                pointerCoords[index].size = 0.1f;
                index++;
            }

            // Handle ACTION_UP when no pointers are active (use last known position)
            if (index == 0) {
                pointerProps[0] = new MotionEvent.PointerProperties();
                pointerProps[0].id = 0;
                pointerProps[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

                pointerCoords[0] = new MotionEvent.PointerCoords();
                pointerCoords[0].x = mLeftX != 0 ? mLeftX : mRightX;
                pointerCoords[0].y = mLeftY != 0 ? mLeftY : mRightY;
                pointerCoords[0].pressure = 0f;
                pointerCoords[0].size = 0f;
            }

            int finalAction;
            if (pointerCount == 0 || action == MotionEvent.ACTION_UP) {
                finalAction = MotionEvent.ACTION_UP;
            } else if (pointerCount == 1 && action == MotionEvent.ACTION_DOWN) {
                finalAction = MotionEvent.ACTION_DOWN;
            } else if (pointerCount == 2 && action == MotionEvent.ACTION_POINTER_DOWN) {
                // Second pointer down - encode pointer index
                finalAction = MotionEvent.ACTION_POINTER_DOWN | 
                    (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else {
                finalAction = MotionEvent.ACTION_MOVE;
            }

            return MotionEvent.obtain(
                mGestureDownTime,
                eventTime,
                finalAction,
                pointerProps.length,
                pointerProps,
                pointerCoords,
                0, // metaState
                0, // buttonState
                1f, // xPrecision
                1f, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN,
                0  // flags
            );
        }
    }

    private void sendCancelEvent() {
        synchronized (mLock) {
            long eventTime = SystemClock.uptimeMillis();
            int pointerCount = (mLeftId != null ? 1 : 0) + (mRightId != null ? 1 : 0);
            if (pointerCount == 0) return;

            MotionEvent.PointerProperties[] pointerProps = 
                new MotionEvent.PointerProperties[pointerCount];
            MotionEvent.PointerCoords[] pointerCoords = 
                new MotionEvent.PointerCoords[pointerCount];

            int index = 0;
            if (mLeftId != null) {
                pointerProps[index] = new MotionEvent.PointerProperties();
                pointerProps[index].id = mLeftId;
                pointerProps[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

                pointerCoords[index] = new MotionEvent.PointerCoords();
                pointerCoords[index].x = mLeftX;
                pointerCoords[index].y = mLeftY;
                pointerCoords[index].pressure = 0f;
                pointerCoords[index].size = 0f;
                index++;
            }

            if (mRightId != null) {
                pointerProps[index] = new MotionEvent.PointerProperties();
                pointerProps[index].id = mRightId;
                pointerProps[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

                pointerCoords[index] = new MotionEvent.PointerCoords();
                pointerCoords[index].x = mRightX;
                pointerCoords[index].y = mRightY;
                pointerCoords[index].pressure = 0f;
                pointerCoords[index].size = 0f;
            }

            MotionEvent cancelEvent = MotionEvent.obtain(
                mGestureDownTime,
                eventTime,
                MotionEvent.ACTION_CANCEL,
                pointerCount,
                pointerProps,
                pointerCoords,
                0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            );

            sendEvent(cancelEvent);
        }
    }

    private void sendEvent(final MotionEvent event) {
        mHandler.post(() -> {
            try {
                mInputManager.injectInputEvent(event, 
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            } catch (Exception e) {
                Slog.e(TAG, "Error injecting event: " + e.getMessage());
            } finally {
                event.recycle();
            }
        });
    }

    private int nextAvailablePointerId() {
        int id = 0;
        while ((mLeftId != null && mLeftId == id) || (mRightId != null && mRightId == id)) {
            id++;
        }
        return id;
    }

    private float[] getLeftTriggerCoordinates() {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        float defaultX = metrics.widthPixels * DEFAULT_X_OFFSET_LEFT;
        float defaultY = metrics.heightPixels * DEFAULT_Y_OFFSET;

        float x = Float.parseFloat(mPrefs.getString("left_trigger_x", String.valueOf(defaultX)));
        float y = Float.parseFloat(mPrefs.getString("left_trigger_y", String.valueOf(defaultY)));

        return new float[]{x, y};
    }

    private float[] getRightTriggerCoordinates() {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        float defaultX = metrics.widthPixels * DEFAULT_X_OFFSET_RIGHT;
        float defaultY = metrics.heightPixels * DEFAULT_Y_OFFSET;

        float x = Float.parseFloat(mPrefs.getString("right_trigger_x", String.valueOf(defaultX)));
        float y = Float.parseFloat(mPrefs.getString("right_trigger_y", String.valueOf(defaultY)));

        return new float[]{x, y};
    }
}
