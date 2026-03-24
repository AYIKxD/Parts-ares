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

package org.lineageos.xiaomiparts

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.xiaomiparts.gestures.TouchGesturesActivity
import org.lineageos.xiaomiparts.led.LedSettingsFragment
import org.lineageos.xiaomiparts.triggers.TriggerSettingsFragment
import org.lineageos.xiaomiparts.util.Utils

class XiaomiPartsFragment : PreferenceFragmentCompat() {

    // Known touch gesture sysfs node — if it exists, gestures are supported
    private val GESTURE_NODE = "/proc/touchpanel/gesture_enable"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.xiaomiparts_main, rootKey)

        findPreference<Preference>("trigger_settings")?.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, TriggerSettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }

        findPreference<Preference>("led_settings")?.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, LedSettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }

        val gesturesPref = findPreference<Preference>("gesture_settings")
        if (Utils.fileExists(GESTURE_NODE)) {
            gesturesPref?.setOnPreferenceClickListener {
                startActivity(
                    Intent(requireContext(), TouchGesturesActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }
        } else {
            gesturesPref?.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.XiaomiParts)
    }
}
