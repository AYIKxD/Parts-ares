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

package org.lineageos.xiaomiparts.gestures

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.app.DialogFragment
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceScreen
import android.preference.SwitchPreference

import androidx.appcompat.app.AppCompatActivity

import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.util.Action
import org.lineageos.xiaomiparts.util.ShortcutPickerHelper
import org.lineageos.xiaomiparts.util.Utils

// ─────────────────────────────────────────────────────────────────
// TouchGesturesActivity – host Activity for the gesture settings
// ─────────────────────────────────────────────────────────────────
class TouchGesturesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, TouchGestures())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// TouchGestures – PreferenceFragment for gesture configuration
// ─────────────────────────────────────────────────────────────────
class TouchGestures : PreferenceFragment(),
    Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener,
    ShortcutPickerHelper.OnPickListener {

    companion object {
        private const val GESTURE_PATH = "/proc/touchpanel/gesture_enable"
        private const val DT2W_PATH    = "/proc/touchpanel/double_tap_enable"

        const val PREF_DT2W_ENABLE             = "enable_dt2w"
        const val PREF_GESTURE_ENABLE          = "enable_gestures"
        const val PREF_GESTURE_DOUBLE_TAP      = "gesture_double_tap"
        const val PREF_GESTURE_W               = "gesture_w"
        const val PREF_GESTURE_M               = "gesture_m"
        const val PREF_GESTURE_CIRCLE          = "gesture_circle"
        const val PREF_GESTURE_TWO_SWIPE       = "gesture_two_swipe"
        const val PREF_GESTURE_UP_ARROW        = "gesture_up_arrow"
        const val PREF_GESTURE_DOWN_ARROW      = "gesture_down_arrow"
        const val PREF_GESTURE_LEFT_ARROW      = "gesture_left_arrow"
        const val PREF_GESTURE_RIGHT_ARROW     = "gesture_right_arrow"
        const val PREF_GESTURE_SWIPE_UP        = "gesture_swipe_up"
        const val PREF_GESTURE_SWIPE_DOWN      = "gesture_swipe_down"
        const val PREF_GESTURE_SWIPE_LEFT      = "gesture_swipe_left"
        const val PREF_GESTURE_SWIPE_RIGHT     = "gesture_swipe_right"

        @JvmStatic fun isSupported(): Boolean = Utils.fileWritable(GESTURE_PATH) || Utils.fileWritable(DT2W_PATH)
        @JvmStatic fun isSupported(filepath: String): Boolean = Utils.fileWritable(filepath)
        @JvmStatic fun enableGestures(enable: Boolean) { if (Utils.fileExists(GESTURE_PATH)) Utils.writeLine(GESTURE_PATH, if (enable) "1" else "0") }
        @JvmStatic fun enableDt2w(enable: Boolean)     { if (Utils.fileExists(DT2W_PATH))    Utils.writeLine(DT2W_PATH,    if (enable) "1" else "0") }
    }

    private val KEY_GESTURE_HAPTIC_FEEDBACK = "gesture_haptic_feedback"
    private val DLG_SHOW_ACTION_DIALOG = 0
    private val DLG_RESET_TO_DEFAULT   = 1
    private val MENU_RESET = Menu.FIRST

    private var mGestureDoubleTap: Preference? = null
    private var mGestureW: Preference? = null; private var mGestureM: Preference? = null
    private var mGestureCircle: Preference? = null; private var mGestureTwoSwipe: Preference? = null
    private var mGestureUpArrow: Preference? = null; private var mGestureDownArrow: Preference? = null
    private var mGestureLeftArrow: Preference? = null; private var mGestureRightArrow: Preference? = null
    private var mGestureSwipeUp: Preference? = null; private var mGestureSwipeDown: Preference? = null
    private var mGestureSwipeLeft: Preference? = null; private var mGestureSwipeRight: Preference? = null
    private var mEnableDt2w: SwitchPreference? = null
    private var mEnableGestures: SwitchPreference? = null
    private var mHapticFeedback: SwitchPreference? = null

    private lateinit var mPrefs: android.content.SharedPreferences
    private lateinit var mActionEntries: Array<String>
    private lateinit var mActionValues: Array<String>
    private lateinit var mPicker: ShortcutPickerHelper
    private var mPendingkey: String? = null

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        mPicker = ShortcutPickerHelper(activity, this)
        mPrefs = Utils.getSharedPreferences(activity)
        mActionValues = resources.getStringArray(R.array.action_screen_off_values)
        mActionEntries = resources.getStringArray(R.array.action_screen_off_entries)
        initPrefs()
        setHasOptionsMenu(true)
    }

    private fun initPrefs(): PreferenceScreen {
        preferenceScreen?.removeAll()
        addPreferencesFromResource(R.xml.screen_off_gesture)
        val prefs = preferenceScreen

        val dt2w    = prefs.findPreference("dt2w") as? PreferenceCategory
        val gestures = prefs.findPreference("gestures") as? PreferenceCategory
        val haptic  = prefs.findPreference("haptic") as? PreferenceCategory

        mEnableDt2w    = prefs.findPreference(PREF_DT2W_ENABLE) as? SwitchPreference
        mEnableGestures= prefs.findPreference(PREF_GESTURE_ENABLE) as? SwitchPreference
        mHapticFeedback= findPreference(KEY_GESTURE_HAPTIC_FEEDBACK) as? SwitchPreference

        mHapticFeedback?.isChecked = mPrefs.getInt(Utils.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1) != 0
        mHapticFeedback?.onPreferenceChangeListener = this

        mGestureDoubleTap   = prefs.findPreference(PREF_GESTURE_DOUBLE_TAP)
        mGestureW           = prefs.findPreference(PREF_GESTURE_W)
        mGestureM           = prefs.findPreference(PREF_GESTURE_M)
        mGestureCircle      = prefs.findPreference(PREF_GESTURE_CIRCLE)
        mGestureTwoSwipe    = prefs.findPreference(PREF_GESTURE_TWO_SWIPE)
        mGestureUpArrow     = prefs.findPreference(PREF_GESTURE_UP_ARROW)
        mGestureDownArrow   = prefs.findPreference(PREF_GESTURE_DOWN_ARROW)
        mGestureLeftArrow   = prefs.findPreference(PREF_GESTURE_LEFT_ARROW)
        mGestureRightArrow  = prefs.findPreference(PREF_GESTURE_RIGHT_ARROW)
        mGestureSwipeUp     = prefs.findPreference(PREF_GESTURE_SWIPE_UP)
        mGestureSwipeDown   = prefs.findPreference(PREF_GESTURE_SWIPE_DOWN)
        mGestureSwipeLeft   = prefs.findPreference(PREF_GESTURE_SWIPE_LEFT)
        mGestureSwipeRight  = prefs.findPreference(PREF_GESTURE_SWIPE_RIGHT)

        setPref(mGestureDoubleTap,  mPrefs.getString(PREF_GESTURE_DOUBLE_TAP,  Action.ACTION_WAKE_DEVICE)!!)
        setPref(mGestureW,          mPrefs.getString(PREF_GESTURE_W,           Action.ACTION_CAMERA)!!)
        setPref(mGestureM,          mPrefs.getString(PREF_GESTURE_M,           Action.ACTION_MEDIA_PLAY_PAUSE)!!)
        setPref(mGestureCircle,     mPrefs.getString(PREF_GESTURE_CIRCLE,      Action.ACTION_VIB_SILENT)!!)
        setPref(mGestureTwoSwipe,   mPrefs.getString(PREF_GESTURE_TWO_SWIPE,   Action.ACTION_MEDIA_PREVIOUS)!!)
        setPref(mGestureUpArrow,    mPrefs.getString(PREF_GESTURE_UP_ARROW,    Action.ACTION_MEDIA_NEXT)!!)
        setPref(mGestureDownArrow,  mPrefs.getString(PREF_GESTURE_DOWN_ARROW,  Action.ACTION_MEDIA_NEXT)!!)
        setPref(mGestureLeftArrow,  mPrefs.getString(PREF_GESTURE_LEFT_ARROW,  Action.ACTION_MEDIA_NEXT)!!)
        setPref(mGestureRightArrow, mPrefs.getString(PREF_GESTURE_RIGHT_ARROW, Action.ACTION_MEDIA_NEXT)!!)
        setPref(mGestureSwipeUp,    mPrefs.getString(PREF_GESTURE_SWIPE_UP,    Action.ACTION_WAKE_DEVICE)!!)
        setPref(mGestureSwipeDown,  mPrefs.getString(PREF_GESTURE_SWIPE_DOWN,  Action.ACTION_VIB_SILENT)!!)
        setPref(mGestureSwipeLeft,  mPrefs.getString(PREF_GESTURE_SWIPE_LEFT,  Action.ACTION_MEDIA_PREVIOUS)!!)
        setPref(mGestureSwipeRight, mPrefs.getString(PREF_GESTURE_SWIPE_RIGHT, Action.ACTION_MEDIA_NEXT)!!)

        mEnableDt2w?.apply    { isChecked = mPrefs.getBoolean(PREF_DT2W_ENABLE, true);    onPreferenceChangeListener = this@TouchGestures }
        mEnableGestures?.apply{ isChecked = mPrefs.getBoolean(PREF_GESTURE_ENABLE, true); onPreferenceChangeListener = this@TouchGestures }

        if (!isSupported(DT2W_PATH))  dt2w?.let  { preferenceScreen.removePreference(it) }
        if (!isSupported())           haptic?.let { preferenceScreen.removePreference(it) }
        gestures?.let { preferenceScreen.removePreference(it) }

        return prefs
    }

    private fun setPref(pref: Preference?, action: String) {
        pref ?: return
        pref.summary = getDescription(action)
        pref.onPreferenceClickListener = this
    }

    private fun getDescription(action: String?): String? {
        action ?: return null
        mActionValues.forEachIndexed { i, v -> if (action == v) return mActionEntries[i] }
        return null
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val (key, title) = when (preference) {
            mGestureDoubleTap   -> PREF_GESTURE_DOUBLE_TAP  to R.string.gesture_double_tap_title
            mGestureW           -> PREF_GESTURE_W           to R.string.gesture_w_title
            mGestureM           -> PREF_GESTURE_M           to R.string.gesture_m_title
            mGestureCircle      -> PREF_GESTURE_CIRCLE      to R.string.gesture_circle_title
            mGestureTwoSwipe    -> PREF_GESTURE_TWO_SWIPE   to R.string.gesture_two_swipe_title
            mGestureUpArrow     -> PREF_GESTURE_UP_ARROW    to R.string.gesture_up_arrow_title
            mGestureDownArrow   -> PREF_GESTURE_DOWN_ARROW  to R.string.gesture_down_arrow_title
            mGestureLeftArrow   -> PREF_GESTURE_LEFT_ARROW  to R.string.gesture_left_arrow_title
            mGestureRightArrow  -> PREF_GESTURE_RIGHT_ARROW to R.string.gesture_right_arrow_title
            mGestureSwipeUp     -> PREF_GESTURE_SWIPE_UP    to R.string.gesture_swipe_up_title
            mGestureSwipeDown   -> PREF_GESTURE_SWIPE_DOWN  to R.string.gesture_swipe_down_title
            mGestureSwipeLeft   -> PREF_GESTURE_SWIPE_LEFT  to R.string.gesture_swipe_left_title
            mGestureSwipeRight  -> PREF_GESTURE_SWIPE_RIGHT to R.string.gesture_swipe_right_title
            else -> return false
        }
        showDialogInner(DLG_SHOW_ACTION_DIALOG, key, title)
        return true
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when (preference) {
            mEnableDt2w -> {
                mPrefs.edit().putBoolean(PREF_DT2W_ENABLE, newValue as Boolean).apply()
                enableDt2w(newValue)
                true
            }
            mEnableGestures -> {
                mPrefs.edit().putBoolean(PREF_GESTURE_ENABLE, newValue as Boolean).apply()
                enableGestures(newValue)
                true
            }
            else -> {
                if (KEY_GESTURE_HAPTIC_FEEDBACK == preference.key) {
                    mPrefs.edit().putInt(Utils.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, if (newValue as Boolean) 1 else 0).apply()
                    true
                } else false
            }
        }
    }

    private fun resetToDefault() {
        mPrefs.edit()
            .putBoolean(PREF_DT2W_ENABLE, true)
            .putBoolean(PREF_GESTURE_ENABLE, true)
            .putString(PREF_GESTURE_DOUBLE_TAP,  Action.ACTION_WAKE_DEVICE)
            .putString(PREF_GESTURE_W,           Action.ACTION_CAMERA)
            .putString(PREF_GESTURE_M,           Action.ACTION_MEDIA_PLAY_PAUSE)
            .putString(PREF_GESTURE_CIRCLE,      Action.ACTION_VIB_SILENT)
            .putString(PREF_GESTURE_TWO_SWIPE,   Action.ACTION_MEDIA_PREVIOUS)
            .putString(PREF_GESTURE_UP_ARROW,    Action.ACTION_WAKE_DEVICE)
            .putString(PREF_GESTURE_DOWN_ARROW,  Action.ACTION_VIB_SILENT)
            .putString(PREF_GESTURE_LEFT_ARROW,  Action.ACTION_MEDIA_PREVIOUS)
            .putString(PREF_GESTURE_RIGHT_ARROW, Action.ACTION_MEDIA_NEXT)
            .putString(PREF_GESTURE_SWIPE_UP,    Action.ACTION_WAKE_DEVICE)
            .putString(PREF_GESTURE_SWIPE_DOWN,  Action.ACTION_VIB_SILENT)
            .putString(PREF_GESTURE_SWIPE_LEFT,  Action.ACTION_MEDIA_PREVIOUS)
            .putString(PREF_GESTURE_SWIPE_RIGHT, Action.ACTION_MEDIA_NEXT)
            .apply()
        mHapticFeedback?.isChecked = true
        enableDt2w(true)
        enableGestures(true)
        initPrefs()
    }

    override fun shortcutPicked(action: String?, description: String?, bmp: Bitmap?, isApplication: Boolean) {
        val key = mPendingkey ?: return
        action ?: return
        mPrefs.edit().putString(key, action).apply()
        initPrefs()
        mPendingkey = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK &&
            requestCode in listOf(
                ShortcutPickerHelper.REQUEST_PICK_SHORTCUT,
                ShortcutPickerHelper.REQUEST_PICK_APPLICATION,
                ShortcutPickerHelper.REQUEST_CREATE_SHORTCUT)) {
            mPicker.onActivityResult(requestCode, resultCode, data)
        } else {
            mPendingkey = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_RESET) showDialogInner(DLG_RESET_TO_DEFAULT, null, 0)
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
            .setIcon(R.drawable.ic_settings_reset)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun showDialogInner(id: Int, key: String?, title: Int) {
        val f = MyAlertDialogFragment.newInstance(id, key, title)
        f.show(fragmentManager, "dialog $id")
    }

    class MyAlertDialogFragment : DialogFragment() {
        companion object {
            fun newInstance(id: Int, key: String?, title: Int): MyAlertDialogFragment {
                return MyAlertDialogFragment().also {
                    it.arguments = Bundle().apply {
                        putInt("id", id); putString("key", key); putInt("title", title)
                    }
                }
            }
        }

        private fun getOwner() = targetFragment as TouchGestures

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val id    = arguments!!.getInt("id")
            val key   = arguments!!.getString("key")
            val title = arguments!!.getInt("title")
            return when (id) {
                0 -> AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setNegativeButton(R.string.cancel, null)
                    .setItems(getOwner().mActionEntries) { _: DialogInterface, item: Int ->
                        if (getOwner().mActionValues[item] == Action.ACTION_APP) {
                            getOwner().mPendingkey = key
                            getOwner().mPicker.pickShortcut(getOwner().id)
                        } else {
                            getOwner().mPrefs.edit().putString(key, getOwner().mActionValues[item]).apply()
                            getOwner().initPrefs()
                        }
                    }.create()
                1 -> AlertDialog.Builder(activity)
                    .setTitle(R.string.reset)
                    .setMessage(R.string.reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok) { _, _ -> getOwner().resetToDefault() }
                    .create()
                else -> throw IllegalArgumentException("unknown id $id")
            }
        }

        override fun onCancel(dialog: DialogInterface) {}
    }
}
