/*
 * Copyright (C) 2020 The AospExtended Project
 * Licensed under the Apache License, Version 2.0
 */

package org.lineageos.xiaomiparts.led

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.TelephonyManager
import android.util.Slog
import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.util.Utils
import java.io.FileOutputStream
import java.util.Random

// ─── LedUtils ─────────────────────────────────────────────────────
class LedUtils private constructor(context: Context) {
    private val DEBUG = Utils.DEBUG
    private val TAG   = "LedUtils"
    private val mContext: Context = context.applicationContext
    private val mPrefs: SharedPreferences = Utils.getSharedPreferences(mContext)
    private val mHandlerThread = HandlerThread("XiaomiParts.HandlerThread").also { it.start() }
    private val mHandler = Handler(mHandlerThread.looper)

    companion object {
        @JvmStatic var sInstance: LedUtils? = null
        @JvmStatic fun getInstance(context: Context): LedUtils {
            if (sInstance == null) sInstance = LedUtils(context.applicationContext)
            return sInstance!!
        }
    }

    @JvmOverloads
    fun play(play: Boolean, force: Boolean = false) {
        stopDisco()
        mHandler.post { disco(play, force) }
    }

    private fun disco(play: Boolean, force: Boolean) {
        val playInGames = mPrefs.getBoolean("led_in_games", false)
        if ((play && !(playInGames && !Utils.isGameApp(mContext))) || force) mUpdateInfo.run()
    }

    private fun stopDisco() {
        mHandler.removeCallbacks(mUpdateInfo)
        setLedColor(0, 0, 0)
    }

    private fun setLedColor(r: Int, g: Int, b: Int) {
        try {
            writeSysfs("/sys/class/leds/red/brightness",   r.toString())
            writeSysfs("/sys/class/leds/green/brightness", g.toString())
            writeSysfs("/sys/class/leds/blue/brightness",  b.toString())
        } catch (e: Exception) { Slog.e(TAG, "Failed to set LED color via sysfs", e) }
    }

    private fun writeSysfs(path: String, value: String) {
        try { FileOutputStream(path).use { it.write(value.toByteArray()); it.flush() } }
        catch (e: Exception) { if (DEBUG) Slog.e(TAG, "Failed to write $value to $path", e) }
    }

    private val mUpdateInfo = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val next = now + (1000 - now % 1000)
            val rnd = Random()
            setLedColor((rnd.nextInt(255) + 35).coerceIn(0, 255),
                        (rnd.nextInt(255) + 35).coerceIn(0, 255),
                        (rnd.nextInt(255) + 35).coerceIn(0, 255))
            mHandler.postAtTime(this, next)
        }
    }
}

// ─── LedDiscoTile ─────────────────────────────────────────────────
class LedDiscoTile : TileService() {
    override fun onStartListening() { super.onStartListening(); updateTileState() }
    override fun onClick() {
        super.onClick()
        val prefs = Utils.getSharedPreferences(this)
        val newState = !prefs.getBoolean("led_disco", false)
        prefs.edit().putBoolean("led_disco", newState).apply()
        LedUtils.getInstance(this).play(newState)
        updateTileState()
    }
    private fun updateTileState() {
        val tile = qsTile ?: return
        val active = Utils.getSharedPreferences(this).getBoolean("led_disco", false)
        tile.state    = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label    = getString(R.string.qs_led_disco_label)
        tile.subtitle = getString(if (active) R.string.switch_bar_on else R.string.switch_bar_off)
        tile.icon     = Icon.createWithResource(this, R.drawable.ic_qs_led_disco)
        tile.updateTile()
    }
}

// ─── LedOnCall ────────────────────────────────────────────────────
class LedOnCall : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val enable = state == TelephonyManager.EXTRA_STATE_OFFHOOK ||
                     state == TelephonyManager.EXTRA_STATE_RINGING
        enableLed(context, enable)
    }
    companion object {
        @JvmStatic fun enableLed(context: Context, enable: Boolean) {
            val on = enable && Utils.getIntSystem(context, "led_in_calls", 1) == 1
            LedUtils.getInstance(context).play(on, on)
        }
    }
}
