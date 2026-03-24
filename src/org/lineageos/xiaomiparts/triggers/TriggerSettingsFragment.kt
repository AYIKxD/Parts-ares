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

package org.lineageos.xiaomiparts.triggers

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.triggers.CustomTriggerActivity
import org.lineageos.xiaomiparts.triggers.TriggerService
import org.lineageos.xiaomiparts.triggers.TriggerUtils
import org.lineageos.xiaomiparts.util.AppListActivity
import org.lineageos.xiaomiparts.util.Utils

class TriggerSettingsFragment : PreferenceFragmentCompat(),
        Preference.OnPreferenceChangeListener {

    private var triggerSoundPref: SwitchPreferenceCompat? = null
    private var soundTypePref: ListPreference? = null
    private var alertSliderPref: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Utils.PREFERENCES
        preferenceManager.sharedPreferencesMode = android.content.Context.MODE_PRIVATE or
                android.content.Context.MODE_MULTI_PROCESS
        setPreferencesFromResource(R.xml.trigger_settings, rootKey)

        val prefs = preferenceManager.sharedPreferences!!

        triggerSoundPref = findPreference<SwitchPreferenceCompat>("trigger_sound")?.also {
            it.isChecked = prefs.getBoolean("trigger_sound", false)
            it.onPreferenceChangeListener = this
        }

        soundTypePref = findPreference<ListPreference>("trigger_sound_type")?.also {
            it.value = prefs.getString("trigger_sound_type", "classic")
            it.onPreferenceChangeListener = this
        }

        alertSliderPref = findPreference<ListPreference>("alert_slider_mode")?.also {
            it.value = prefs.getString("alert_slider_mode", "disabled")
            it.onPreferenceChangeListener = this
        }

        findPreference<Preference>("open_trigger_map")?.setOnPreferenceClickListener {
            TriggerService.getInstance(requireContext()).show()
            true
        }

        findPreference<Preference>("gaming_apps")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), AppListActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }

        findPreference<Preference>("trigger_actions")?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), CustomTriggerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val prefs = preferenceManager.sharedPreferences!!
        when (preference.key) {
            "trigger_sound" -> {
                val enabled = newValue as Boolean
                prefs.edit().putBoolean("trigger_sound", enabled).apply()
                Settings.System.putInt(
                    requireContext().contentResolver, "trigger_sound",
                    if (enabled) 1 else 0
                )
            }
            "trigger_sound_type" -> {
                val value = newValue as String
                prefs.edit().putString("trigger_sound_type", value).apply()
                Settings.System.putString(
                    requireContext().contentResolver, "trigger_sound_type", value
                )
                TriggerUtils.getInstance(requireContext()).triggerAction(true, true)
            }
            "alert_slider_mode" -> {
                val value = newValue as String
                prefs.edit().putString("alert_slider_mode", value).apply()
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.trigger_settings_title)
    }
}
