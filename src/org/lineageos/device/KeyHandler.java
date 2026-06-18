/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.device;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import android.content.BroadcastReceiver;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Slog;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManagerGlobal;
import android.view.ViewConfiguration;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import org.lineageos.device.util.Action;
import org.lineageos.device.util.Utils;

import org.lineageos.device.triggers.TriggerService;
import org.lineageos.device.triggers.TriggerUtils;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = Utils.TAG;
    private static final boolean DEBUG = Utils.DEBUG;

    private final Context mContext;
    private Context mAppContext = null;

    private Vibrator mVibrator;

    long mPrevEventTime;
    boolean mLeftOpen, mLeftClosed;
    boolean mRightOpen, mRightClosed;

    private final CustomSettingsObserver mCustomSettingsObserver;

    public TriggerUtils tr = null;
    public TriggerService triggerService;

    public KeyHandler(Context context) {
        mContext = context;

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        mAppContext = Utils.getAppContext(mContext);
        mCustomSettingsObserver = new CustomSettingsObserver(new Handler(Looper.getMainLooper()));
        mCustomSettingsObserver.observe();
        tr = TriggerUtils.getInstance(mAppContext);
        triggerService = TriggerService.getInstance(mAppContext);
    }

    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void update() {
            onChange(false, Settings.System.getUriFor("trigger_sound"));
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("triggerleft"),
                    false, this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("triggerright"),
                    false, this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("trigger_sound"),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor("trigger_sound"))) {
                if (Settings.System.getInt(mContext.getContentResolver(), "trigger_sound", 0) == 1) {
                    tr.loadSoundResource();
                } else {
                    tr.releaseSoundResource();
                }
                return;
            }
            boolean left = uri.equals(Settings.System.getUriFor("triggerleft"));
            boolean open = Utils.getIntSystem(mContext, left ? "triggerleft" : "triggerright", -1) == 1;
            tr.triggerAction(left, open);
            long now = SystemClock.uptimeMillis();
            long time = now - mPrevEventTime;
            if (time < 3000 && ((mLeftOpen && !left && open) || (mRightOpen && left && open))) {
                if (DEBUG)
                    Slog.d(TAG, "starting service");
                triggerService.show();
            } else if (time < 3000 && ((mLeftClosed && !left && !open) || (mRightClosed && left && !open))) {
                if (DEBUG)
                    Slog.d(TAG, "stopping service");
                triggerService.hide();
            }
            mPrevEventTime = now;
            mLeftOpen = left && open;
            mRightOpen = !left && open;
            mLeftClosed = left && !open;
            mRightClosed = !left && !open;
        }

    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (DEBUG)
            Slog.d(TAG, "Got KeyEvent: " + event);

        if (event.getDevice().getProductId() == 1576) {
            return handleTriggerEvent(event);
        }

        if (event.getAction() != KeyEvent.ACTION_UP) {
            return event;
        }
        return event;
    }

    public KeyEvent handleTriggerEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Slider keycodes (KEY_F3-F6): update Settings.System for slider state
        // KEY_F3 (61) = Left slider open, KEY_F4 (62) = Left slider close
        // KEY_F5 (63) = Right slider open, KEY_F6 (64) = Right slider close
        if (keyCode >= 61 && keyCode <= 64 && event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isLeft = (keyCode == 61 || keyCode == 62);
            boolean isOpen = (keyCode == 61 || keyCode == 63);
            String setting = isLeft ? "triggerleft" : "triggerright";
            Settings.System.putInt(mContext.getContentResolver(), setting, isOpen ? 1 : 0);
            if (DEBUG)
                Slog.d(TAG, "Slider event: " + setting + "=" + (isOpen ? 1 : 0));
            return event;
        }

        // Trigger button keycodes (KEY_F1=59, KEY_F2=60)
        if (!Utils.isGameApp(mContext)) {
            if (DEBUG)
                Slog.d(TAG, "not a game app");
            tr.onEvent(event);
            return event;
        }

        // Touch injection is now handled by GamekeyService via /dev/gamekey
        // KeyHandler no longer needs to inject touch events
        return event;
    }

    private void injectMotionEvent(int id, int inputSource, int action, long downTime, long when,
            float x, float y, float pressure, int displayId) {
        final int pointerCount = id;
        MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            pointerProperties[i] = new MotionEvent.PointerProperties();
            pointerProperties[i].id = i;
            pointerProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            pointerCoords[i] = new MotionEvent.PointerCoords();
            pointerCoords[i].x = x;
            pointerCoords[i].y = y;
            pointerCoords[i].pressure = pressure;
            pointerCoords[i].size = 1.0f;
        }
        if (displayId == INVALID_DISPLAY
                && (inputSource & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            displayId = DEFAULT_DISPLAY;
        }
        MotionEvent event = MotionEvent.obtain(downTime, when, action, pointerCount,
                pointerProperties, pointerCoords, 0, 0,
                1.0f, 1.0f, getInputDeviceId(inputSource),
                0, inputSource, displayId, 0);
        InputManager im = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
        im.injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private int getInputDeviceId(int inputSource) {
        int[] devIds = InputDevice.getDeviceIds();
        for (int devId : devIds) {
            InputDevice inputDev = InputDevice.getDevice(devId);
            if (inputDev.supportsSource(inputSource)) {
                return devId;
            }
        }
        return 0;
    }
}
