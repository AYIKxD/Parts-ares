/*
 * Copyright (C) 2020 The AospExtended Project
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

package org.aospextended.device.led;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Slog;

import java.util.List;
import java.util.Random;

import org.aospextended.device.util.Utils;

/**
 * Controls the RGB LED on the back of the device using the Android Light HAL.
 * Uses LightsManager API instead of direct sysfs writes to comply with SELinux policy.
 */
public class LedUtils {
    private static final boolean DEBUG = Utils.DEBUG;
    private static final String TAG = "LedUtils";

    private Context mContext;
    private SharedPreferences mPrefs;
    public static LedUtils sInstance;
    private boolean mStop;
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private LedUtils(Context context) {
        mContext = context;
        mHandlerThread = new HandlerThread("XiaomiParts.HandlerThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mPrefs = Utils.getSharedPreferences(context);
    }

    public void play(boolean play) {
        play(play, false);
    }

    public void play(boolean play, boolean force) {
        stopDisco();
        mStop = !play;
        if (mHandler != null) {
            mHandler.post(() -> disco(play, force));
        }
    }

    private void disco(boolean play, boolean force) {
        boolean playInGames = mPrefs.getBoolean("led_in_games", false);
        if ((play && !(playInGames && !Utils.isGameApp(mContext))) || force) {
            mUpdateInfo.run();
        }
    }

    private void stopDisco() {
        mHandler.removeCallbacks(mUpdateInfo);
        setLedColor(0, 0, 0);
        closeSession();
    }

    /**
     * Set the RGB LED color using direct sysfs writes instead of Light HAL.
     */
    private void setLedColor(int r, int g, int b) {
        try {
            writeSysfs("/sys/class/leds/red/brightness", String.valueOf(r));
            writeSysfs("/sys/class/leds/green/brightness", String.valueOf(g));
            writeSysfs("/sys/class/leds/blue/brightness", String.valueOf(b));
            if (DEBUG) Slog.d(TAG, "Set LED color: R=" + r + " G=" + g + " B=" + b);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set LED color via sysfs", e);
        }
    }

    private void writeSysfs(String path, String value) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(path);
            fos.write(value.getBytes());
            fos.flush();
            fos.close();
        } catch (Exception e) {
            if (DEBUG) Slog.e(TAG, "Failed to write " + value + " to " + path, e);
        }
    }

    private void closeSession() {
        // No longer using LightSession, nothing to close.
    }

    private int rgb_limit(int value) {
            int x = 35;
            int y = 1;
            int _value = value;

            if (y == 1) {
                _value += x;
            } else {
                _value -= x;
            }

            if (_value >= 255) {
                _value = 255;
                y = 0;
            }

            if (_value < 0) {
                _value = 0;
                y = 1;
            }

            return _value;
    }

    private final Runnable mUpdateInfo = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            long next = now + (1000 - now % 1000);

            Random r = new Random();
            int R = rgb_limit(r.nextInt(255));
            int G = rgb_limit(r.nextInt(255));
            int B = rgb_limit(r.nextInt(255));

            setLedColor(R, G, B);

            if (mHandler != null) {
                mHandler.postAtTime(mUpdateInfo, next);
            }
        }
    };
}
