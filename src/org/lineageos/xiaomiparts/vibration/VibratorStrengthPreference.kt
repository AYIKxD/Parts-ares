/*
 * Copyright (C) 2020 The AospExtended Project
 * Licensed under the Apache License, Version 2.0
 */

package org.lineageos.xiaomiparts.vibration

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.util.Utils

class VibratorStrengthPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs), SeekBar.OnSeekBarChangeListener {

    companion object {
        private const val FILE_LEVEL = "/sys/class/leds/vibrator/vmax_mv"
        const val KEY_VIBSTRENGTH = "vib_strength"
        private val TEST_PATTERN = longArrayOf(0, 250)

        @JvmStatic fun isSupported(): Boolean = Utils.fileWritable(FILE_LEVEL)

        @JvmStatic fun getValue(context: Context): String = Utils.getFileValue(FILE_LEVEL, "3596")

        @JvmStatic fun restore(context: Context) {
            if (!isSupported()) return
            val stored = Utils.getSharedPreferences(context).getString(KEY_VIBSTRENGTH, "2700") ?: "2700"
            Utils.writeValue(FILE_LEVEL, stored)
        }
    }

    private val mMinValue = 116
    private val mMaxValue = 3596
    private var mOldStrength = 0
    private var mSeekBar: SeekBar? = null
    private val mVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator

    init { layoutResource = R.layout.preference_seek_bar }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        mOldStrength = getValue(context).toInt()
        mSeekBar = holder.findViewById(R.id.seekbar) as SeekBar
        mSeekBar!!.max = mMaxValue - mMinValue
        mSeekBar!!.progress = mOldStrength - mMinValue
        mSeekBar!!.setOnSeekBarChangeListener(this)
    }

    private fun setValue(newValue: String, withFeedback: Boolean) {
        Utils.writeValue(FILE_LEVEL, newValue)
        Utils.getSharedPreferences(context).edit().putString(KEY_VIBSTRENGTH, newValue).apply()
        if (withFeedback) mVibrator.vibrate(TEST_PATTERN, -1)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromTouch: Boolean) {
        setValue((progress + mMinValue).toString(), true)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {}
}
