/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.lineageos.settings.gamekey.GamekeyService

/** Everything begins at boot. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Log.d(TAG, "Received intent: ${intent.action}")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        GamekeyService.startService(context)

        Log.i(TAG, "Boot completed, starting services")
    }

    companion object {
        private const val TAG = "XiaomiParts"
    }
}
