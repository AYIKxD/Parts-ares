/*
 * Copyright (C) 2025 XiaomiParts Project
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

package org.lineageos.xiaomiparts.led

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.util.Utils

class LedSettingsFragment : PreferenceFragmentCompat(),
        Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Utils.PREFERENCES
        preferenceManager.sharedPreferencesMode = android.content.Context.MODE_PRIVATE or
                android.content.Context.MODE_MULTI_PROCESS
        setPreferencesFromResource(R.xml.led_settings, rootKey)

        val prefs = preferenceManager.sharedPreferences!!

        findPreference<SwitchPreferenceCompat>("led_disco")?.also {
            it.isChecked = prefs.getBoolean("led_disco", false)
            it.onPreferenceChangeListener = this
        }

        findPreference<SwitchPreferenceCompat>("led_in_games")?.also {
            it.isChecked = prefs.getBoolean("led_in_games", false)
            it.onPreferenceChangeListener = this
        }

        findPreference<SwitchPreferenceCompat>("led_in_calls")?.also {
            it.isChecked = (Settings.System.getInt(
                requireContext().contentResolver, "led_in_calls", 1) == 1)
            it.onPreferenceChangeListener = this
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val enabled = newValue as Boolean
        val prefs = preferenceManager.sharedPreferences!!
        when (preference.key) {
            "led_disco" -> {
                prefs.edit().putBoolean("led_disco", enabled).apply()
                LedUtils.getInstance(requireContext()).play(enabled)
            }
            "led_in_games" -> {
                prefs.edit().putBoolean("led_in_games", enabled).apply()
                val ledDiscoOn = prefs.getBoolean("led_disco", false)
                val shouldPlay = !enabled || (enabled && ledDiscoOn)
                LedUtils.getInstance(requireContext()).play(shouldPlay)
            }
            "led_in_calls" -> {
                Settings.System.putInt(
                    requireContext().contentResolver, "led_in_calls",
                    if (enabled) 1 else 0
                )
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.led_settings_title)
    }
}
