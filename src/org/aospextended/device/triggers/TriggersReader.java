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

import android.util.Slog;

import org.aospextended.device.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;

/**
 * Reads trigger state from /dev/gamekey device file.
 * The device returns 4 bytes:
 * - byte[0]: left hall sensor (0 = closed, 1 = open/extended)
 * - byte[1]: right hall sensor (0 = closed, 1 = open/extended)
 * - byte[2]: left key pressed (0 = not pressed, 1 = pressed)
 * - byte[3]: right key pressed (0 = not pressed, 1 = pressed)
 */
public class TriggersReader {
    private static final String TAG = "TriggersReader";
    private static final boolean DEBUG = Utils.DEBUG;

    private static final String GAMEKEY_PATH = "/dev/gamekey";
    private static final int BUFFER_SIZE = 4;
    private static final long DEVICE_POLL_DELAY_MS = 2000L;
    private static final long READ_DELAY_MS = 12L;
    private static final long ERROR_RETRY_DELAY_MS = 150L;

    private final TriggersCallback mCallback;
    private volatile boolean mIsRunning = false;
    private Thread mReaderThread;

    public interface TriggersCallback {
        /**
         * Called when trigger state changes
         * @param hallLeft true if left trigger is extended
         * @param hallRight true if right trigger is extended
         * @param keyLeft true if left trigger key is pressed
         * @param keyRight true if right trigger key is pressed
         */
        void onTriggersChanged(boolean hallLeft, boolean hallRight, 
                               boolean keyLeft, boolean keyRight);
    }

    public TriggersReader(TriggersCallback callback) {
        mCallback = callback;
    }

    public void startReading() {
        if (mIsRunning) return;

        mIsRunning = true;
        mReaderThread = new Thread(this::readLoop, "TriggersReader");
        mReaderThread.start();
        if (DEBUG) Slog.d(TAG, "TriggerReader started");
    }

    public void stopReading() {
        mIsRunning = false;
        if (mReaderThread != null) {
            mReaderThread.interrupt();
            mReaderThread = null;
        }
        if (DEBUG) Slog.d(TAG, "TriggerReader stopped");
    }

    private void readLoop() {
        while (mIsRunning) {
            try {
                if (waitForDeviceAvailability()) {
                    readInputEvents();
                }
            } catch (Exception e) {
                if (DEBUG) Slog.e(TAG, "Read loop error: " + e.getMessage(), e);
                try {
                    Thread.sleep(ERROR_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private boolean waitForDeviceAvailability() {
        File deviceFile = new File(GAMEKEY_PATH);
        while (mIsRunning && !deviceFile.exists()) {
            try {
                Thread.sleep(DEVICE_POLL_DELAY_MS);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return deviceFile.exists();
    }

    private void readInputEvents() {
        try (RandomAccessFile raf = new RandomAccessFile(GAMEKEY_PATH, "r");
             FileInputStream fis = new FileInputStream(raf.getFD())) {
            
            byte[] buffer = new byte[BUFFER_SIZE];

            while (mIsRunning) {
                try {
                    int bytesRead = readFullBuffer(fis, buffer);
                    if (bytesRead == BUFFER_SIZE) {
                        boolean hallLeft = buffer[0] == 1;
                        boolean hallRight = buffer[1] == 1;
                        boolean keyLeft = buffer[2] == 1;
                        boolean keyRight = buffer[3] == 1;

                        if (DEBUG) {
                            Slog.d(TAG, "Triggers: hallL=" + hallLeft + 
                                ", hallR=" + hallRight + 
                                ", keyL=" + keyLeft + 
                                ", keyR=" + keyRight);
                        }

                        // Notify listener
                        if (mCallback != null) {
                            mCallback.onTriggersChanged(hallLeft, hallRight, keyLeft, keyRight);
                        }
                    }
                    Thread.sleep(READ_DELAY_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    if (DEBUG) Slog.w(TAG, "Read error, retrying: " + e.getMessage());
                    Thread.sleep(ERROR_RETRY_DELAY_MS);
                }
            }
        } catch (Exception e) {
            if (DEBUG) Slog.e(TAG, "Error opening gamekey device: " + e.getMessage());
        }
    }

    private int readFullBuffer(FileInputStream stream, byte[] buffer) throws Exception {
        int totalRead = 0;
        while (totalRead < buffer.length && mIsRunning) {
            int read = stream.read(buffer, totalRead, buffer.length - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        return totalRead;
    }
}
