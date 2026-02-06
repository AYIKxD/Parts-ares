/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospextended.device.gamekey

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Reads trigger states from /dev/gamekey device node.
 * 
 * The device outputs 4 bytes:
 * - byte[0]: hall_left (slider open/closed)
 * - byte[1]: hall_right (slider open/closed)
 * - byte[2]: key_left (button pressed)
 * - byte[3]: key_right (button pressed)
 */
class TriggersReader(
    private val onTriggersChanged: (hallLeft: Boolean, hallRight: Boolean, keyLeft: Boolean, keyRight: Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "TriggersReader"
        private const val GAMEKEY_PATH = "/dev/gamekey"
        private const val BUFFER_SIZE = 4
        private const val DEVICE_POLL_DELAY_MS = 2000L
        private const val READ_DELAY_MS = 12L
        private const val ERROR_RETRY_DELAY_MS = 150L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun startReading() {
        if (isRunning) return

        isRunning = true
        scope.launch {
            readLoop()
        }
        Log.d(TAG, "TriggersReader started")
    }

    fun stopReading() {
        isRunning = false
        scope.cancel()
        Log.d(TAG, "TriggersReader stopped")
    }

    private suspend fun readLoop() {
        while (isRunning) {
            try {
                if (waitForDeviceAvailability()) {
                    readInputEvents()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read loop error: ${e.message}", e)
                delay(ERROR_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun waitForDeviceAvailability(): Boolean {
        val deviceFile = File(GAMEKEY_PATH)
        while (isRunning && !deviceFile.exists()) {
            delay(DEVICE_POLL_DELAY_MS)
        }
        return deviceFile.exists()
    }

    private suspend fun readInputEvents() {
        RandomAccessFile(GAMEKEY_PATH, "r").use { raf ->
            FileInputStream(raf.fd).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)

                while (isRunning) {
                    try {
                        val bytesRead = readFullBuffer(fis, buffer)
                        if (bytesRead == BUFFER_SIZE) {
                            val hallLeft = buffer[0].toInt() == 1
                            val hallRight = buffer[1].toInt() == 1
                            val keyLeft = buffer[2].toInt() == 1
                            val keyRight = buffer[3].toInt() == 1

                            // Notify listener
                            onTriggersChanged(hallLeft, hallRight, keyLeft, keyRight)
                        }
                        delay(READ_DELAY_MS)
                    } catch (e: Exception) {
                        Log.w(TAG, "Read error, retrying: ${e.message}")
                        delay(ERROR_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun readFullBuffer(
        stream: FileInputStream,
        buffer: ByteArray,
    ): Int {
        var totalRead = 0
        while (totalRead < buffer.size && isRunning) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }
}
