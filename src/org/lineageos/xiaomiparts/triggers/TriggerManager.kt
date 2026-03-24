/*
 * Copyright (C) 2020 The AospExtended Project
 * Licensed under the Apache License, Version 2.0
 */

package org.lineageos.xiaomiparts.triggers

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.util.Slog
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.Toast
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragment
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference

import org.lineageos.xiaomiparts.R
import org.lineageos.xiaomiparts.util.Action
import org.lineageos.xiaomiparts.util.ShortcutPickerHelper
import org.lineageos.xiaomiparts.util.Utils

import java.util.ArrayList
import java.util.HashMap

// ─── CustomTriggerActivity ────────────────────────────────────────
class CustomTriggerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, CustomTrigger())
            .commit()
    }
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}

// ─── CustomTrigger ────────────────────────────────────────────────
class CustomTrigger : PreferenceFragment(),
    Preference.OnPreferenceChangeListener,
    CompoundButton.OnCheckedChangeListener,
    Preference.OnPreferenceClickListener,
    ShortcutPickerHelper.OnPickListener {

    companion object {
        const val PREF_CUSTOM_TRIGGER_ENABLE        = "custom_trigger_enable"
        const val PREF_LEFT_TRIGGER_DOUBLE_CLICK    = "custom_left_trigger_double_click"
        const val PREF_RIGHT_TRIGGER_DOUBLE_CLICK   = "custom_right_trigger_double_click"
        const val PREF_LEFT_TRIGGER_LONGPRESS       = "custom_left_trigger_longpress"
        const val PREF_RIGHT_TRIGGER_LONGPRESS      = "custom_right_trigger_longpress"
        const val KEY_TRIGGER_HAPTIC_FEEDBACK       = "custom_trigger_haptic_feedback"
    }

    private val DLG_SHOW_ACTION_DIALOG = 0
    private val DLG_RESET_TO_DEFAULT   = 1
    private val MENU_RESET = Menu.FIRST

    private var mEnableCustomTrigger: SwitchPreference? = null
    private var mLeftTriggerDoubleClick:  Preference? = null
    private var mRightTriggerDoubleClick: Preference? = null
    private var mLeftTriggerLongpress:    Preference? = null
    private var mRightTriggerLongpress:   Preference? = null
    private var mHapticFeedback: SwitchPreference? = null

    private lateinit var mActionEntries: Array<String>
    private lateinit var mActionValues:  Array<String>
    private lateinit var mPicker: ShortcutPickerHelper
    private var mPendingkey: String? = null

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        mPicker = ShortcutPickerHelper(activity, this)
        mActionValues  = resources.getStringArray(R.array.action_screen_off_values)
        mActionEntries = resources.getStringArray(R.array.action_screen_off_entries)
        initPrefs()
        setHasOptionsMenu(true)
    }

    private fun initPrefs(): PreferenceScreen {
        preferenceScreen?.removeAll()
        addPreferencesFromResource(R.xml.custom_trigger)
        val prefs = preferenceScreen

        val enabled = Utils.getIntSystem(activity, PREF_CUSTOM_TRIGGER_ENABLE, 1) == 1
        mEnableCustomTrigger = findPreference(PREF_CUSTOM_TRIGGER_ENABLE) as? SwitchPreference
        mEnableCustomTrigger?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val isChecked = newValue as Boolean
            mEnableCustomTrigger?.isChecked = isChecked
            Utils.putIntSystem(activity, PREF_CUSTOM_TRIGGER_ENABLE, if (isChecked) 1 else 0)
            mHapticFeedback?.isEnabled = isChecked
            true
        }
        mEnableCustomTrigger?.isChecked = enabled

        mLeftTriggerDoubleClick  = prefs.findPreference(PREF_LEFT_TRIGGER_DOUBLE_CLICK)
        mRightTriggerDoubleClick = prefs.findPreference(PREF_RIGHT_TRIGGER_DOUBLE_CLICK)
        mLeftTriggerLongpress    = prefs.findPreference(PREF_LEFT_TRIGGER_LONGPRESS)
        mRightTriggerLongpress   = prefs.findPreference(PREF_RIGHT_TRIGGER_LONGPRESS)

        mHapticFeedback = findPreference(KEY_TRIGGER_HAPTIC_FEEDBACK) as? SwitchPreference
        mHapticFeedback?.isEnabled = enabled
        mHapticFeedback?.isChecked = Utils.getIntSystem(activity, KEY_TRIGGER_HAPTIC_FEEDBACK, 1) != 0
        mHapticFeedback?.onPreferenceChangeListener = this

        setPref(mLeftTriggerDoubleClick,  Utils.getStringSystem(activity, PREF_LEFT_TRIGGER_DOUBLE_CLICK,  Action.ACTION_NULL))
        setPref(mRightTriggerDoubleClick, Utils.getStringSystem(activity, PREF_RIGHT_TRIGGER_DOUBLE_CLICK, Action.ACTION_NULL))
        setPref(mLeftTriggerLongpress,    Utils.getStringSystem(activity, PREF_LEFT_TRIGGER_LONGPRESS,     Action.ACTION_NULL))
        setPref(mRightTriggerLongpress,   Utils.getStringSystem(activity, PREF_RIGHT_TRIGGER_LONGPRESS,    Action.ACTION_NULL))
        return prefs
    }

    private fun setPref(pref: Preference?, action: String) {
        pref ?: return
        pref.summary = mActionValues.indexOfFirst { it == action }.takeIf { it >= 0 }?.let { mActionEntries[it] }
        pref.onPreferenceClickListener = this
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val (key, title) = when (preference) {
            mLeftTriggerDoubleClick  -> PREF_LEFT_TRIGGER_DOUBLE_CLICK  to R.string.custom_left_trigger_double_click_title
            mRightTriggerDoubleClick -> PREF_RIGHT_TRIGGER_DOUBLE_CLICK to R.string.custom_right_trigger_double_click_title
            mLeftTriggerLongpress    -> PREF_LEFT_TRIGGER_LONGPRESS     to R.string.custom_left_trigger_longpress_title
            mRightTriggerLongpress   -> PREF_RIGHT_TRIGGER_LONGPRESS    to R.string.custom_right_trigger_longpress_title
            else -> return false
        }
        showDialogInner(DLG_SHOW_ACTION_DIALOG, key, title)
        return true
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (KEY_TRIGGER_HAPTIC_FEEDBACK == preference.key) {
            Utils.putIntSystem(activity, KEY_TRIGGER_HAPTIC_FEEDBACK, if (newValue as Boolean) 1 else 0)
            return true
        }
        return false
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        // SwitchPreference change handled via onPreferenceChangeListener above;
        // this callback is kept for API compatibility only.
    }

    private fun resetToDefault() {
        Utils.putIntSystem(activity, PREF_CUSTOM_TRIGGER_ENABLE, 1)
        Utils.putStringSystem(activity, PREF_LEFT_TRIGGER_DOUBLE_CLICK,  Action.ACTION_NULL)
        Utils.putStringSystem(activity, PREF_RIGHT_TRIGGER_DOUBLE_CLICK, Action.ACTION_NULL)
        Utils.putStringSystem(activity, PREF_LEFT_TRIGGER_LONGPRESS,     Action.ACTION_NULL)
        Utils.putStringSystem(activity, PREF_RIGHT_TRIGGER_LONGPRESS,    Action.ACTION_NULL)
        mHapticFeedback?.isChecked = true
        initPrefs()
    }

    override fun shortcutPicked(action: String?, description: String?, bmp: Bitmap?, isApplication: Boolean) {
        val key = mPendingkey ?: return; action ?: return
        Utils.putStringSystem(activity, key, action)
        initPrefs(); mPendingkey = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (android.app.Activity.RESULT_OK == resultCode &&
            requestCode in listOf(ShortcutPickerHelper.REQUEST_PICK_SHORTCUT,
                ShortcutPickerHelper.REQUEST_PICK_APPLICATION,
                ShortcutPickerHelper.REQUEST_CREATE_SHORTCUT)) {
            mPicker.onActivityResult(requestCode, resultCode, data)
        } else { mPendingkey = null }
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
        f.show(parentFragmentManager, "dialog $id")
    }

    class MyAlertDialogFragment : androidx.fragment.app.DialogFragment() {
        companion object {
            fun newInstance(id: Int, key: String?, title: Int) = MyAlertDialogFragment().also {
                it.arguments = Bundle().apply { putInt("id", id); putString("key", key); putInt("title", title) }
            }
        }
        private fun getOwner() = targetFragment as CustomTrigger
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val id = requireArguments().getInt("id")
            val key = requireArguments().getString("key")
            val title = requireArguments().getInt("title")
            return when (id) {
                0 -> AlertDialog.Builder(requireActivity()).setTitle(title)
                    .setNegativeButton(R.string.cancel, null as? android.content.DialogInterface.OnClickListener)
                    .setItems(getOwner().mActionEntries) { _: android.content.DialogInterface, item: Int ->
                        if (getOwner().mActionValues[item] == Action.ACTION_APP) {
                            getOwner().mPendingkey = key
                            getOwner().mPicker.pickShortcut(getOwner().id)
                        } else {
                            Utils.putStringSystem(getOwner().activity, key, getOwner().mActionValues[item])
                            getOwner().initPrefs()
                        }
                    }.create()
                1 -> AlertDialog.Builder(activity).setTitle(R.string.reset).setMessage(R.string.reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok) { _, _ -> getOwner().resetToDefault() }.create()
                else -> throw IllegalArgumentException("unknown id $id")
            }
        }
        override fun onCancel(dialog: DialogInterface) {}
    }
}

// ─── TriggerMapTile ───────────────────────────────────────────────
class TriggerMapTile : TileService() {
    override fun onStartListening() { super.onStartListening(); updateTileState() }
    override fun onClick() {
        super.onClick()
        val svc = TriggerService.getInstance(this)
        if (svc.isShowing()) svc.hide() else svc.show()
        updateTileState()
    }
    private fun updateTileState() {
        val tile = qsTile ?: return
        val svc = TriggerService.getInstance(this)
        val active = svc.isShowing()
        tile.state    = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label    = getString(R.string.qs_trigger_map_label)
        tile.subtitle = getString(if (active) R.string.switch_bar_on else R.string.switch_bar_off)
        tile.icon     = Icon.createWithResource(this, R.drawable.ic_qs_trigger_map)
        tile.updateTile()
    }
}

// ─── TriggerService ───────────────────────────────────────────────
class TriggerService private constructor(context: Context) : View.OnTouchListener, View.OnClickListener {
    private val DEBUG = Utils.DEBUG
    private val TAG   = "TriggerService"
    private var mPrefs: SharedPreferences? = null
    private var mView: View? = null
    private var image1: ImageView? = null; private var image2: ImageView? = null
    private var button: ImageView? = null
    private var windowManager: WindowManager? = null
    private val p = Point()
    private var layoutParams: WindowManager.LayoutParams? = null
    var X = 0f; var Y = 0f
    var mLX = 0f; var mLY = 0f; var mRX = 0f; var mRY = 0f
    var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f; var bx = 0f; var by = 0f
    var mBX = 200f; var mBY = 2000f
    var mHeight = 0; var mRotation = 0
    private val mContext: Context = context
    private var mInitialized = false
    private var mReceiverRegistered = false
    private var mShowing = false

    companion object {
        @JvmStatic private var mInstance: TriggerService? = null
        @JvmStatic fun getInstance(context: Context): TriggerService {
            if (mInstance == null) { Slog.d("TriggerService", "NEW INSTANCE"); mInstance = TriggerService(context) }
            return mInstance!!
        }
        @JvmStatic fun onBoot(context: Context) {
            val prefs = Utils.getSharedPreferences(context)
            Utils.writeValue("/proc/touchpanel/left_trigger_x",  prefs.getString("left_trigger_x",  "540")!!)
            Utils.writeValue("/proc/touchpanel/left_trigger_y",  prefs.getString("left_trigger_y",  "700")!!)
            Utils.writeValue("/proc/touchpanel/right_trigger_x", prefs.getString("right_trigger_x", "540")!!)
            Utils.writeValue("/proc/touchpanel/right_trigger_y", prefs.getString("right_trigger_y", "1700")!!)
            Utils.writeValue("/proc/touchpanel/left_trigger_enable",  "1")
            Utils.writeValue("/proc/touchpanel/right_trigger_enable", "1")
        }
    }

    fun isShowing() = mShowing

    private val mIntentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) updatePosition(false)
        }
    }

    fun init(context: Context) {
        if (mInitialized) return
        mInitialized = true
        mPrefs = Utils.getSharedPreferences(context)
        onBoot(context)
        val filter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        mContext.registerReceiver(mIntentReceiver, filter)
        mReceiverRegistered = true
        mHeight = context.resources.getDimensionPixelSize(R.dimen.image_height)
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context.resources.displayMetrics
        p.x = metrics.widthPixels; p.y = metrics.heightPixels
        mView = LayoutInflater.from(context).inflate(R.layout.view, null)
        image1 = mView!!.findViewById(R.id.image1)
        image2 = mView!!.findViewById(R.id.image2)
        image1!!.setOnTouchListener(this); image2!!.setOnTouchListener(this)
        mLX = Utils.getFileValue("/proc/touchpanel/left_trigger_x",  "0").toFloat()
        mLY = Utils.getFileValue("/proc/touchpanel/left_trigger_y",  "0").toFloat()
        mRX = Utils.getFileValue("/proc/touchpanel/right_trigger_x", "0").toFloat()
        mRY = Utils.getFileValue("/proc/touchpanel/right_trigger_y", "0").toFloat()
        image1!!.animate().x(mLX).y(mLY).setDuration(0).start()
        image2!!.animate().x(mRX).y(mRY).setDuration(0).start()
        button = mView!!.findViewById(R.id.button)
        button!!.setOnClickListener(this)
        button!!.setOnLongClickListener { Toast.makeText(mContext, "Resetted values", Toast.LENGTH_LONG).show(); reset(); true }
        button!!.animate().x(mBX).y(mBY).setDuration(0).start()
        mView!!.alpha = 0.5f
    }

    fun show() {
        if (mShowing) return
        init(mContext)
        if (DEBUG) Slog.d(TAG, "show")
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT).apply {
                gravity = Gravity.CENTER; x = 0; y = 0
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        try { windowManager!!.addView(mView, layoutParams) } catch (e: RuntimeException) {}
        mShowing = true
        mView!!.visibility = View.VISIBLE
        image1!!.visibility = View.VISIBLE; image2!!.visibility = View.VISIBLE
        updatePosition(true)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { X = v.x - event.rawX; Y = v.y - event.rawY }
            MotionEvent.ACTION_MOVE -> {
                v.animate().x(event.rawX + X).y(event.rawY + Y).setDuration(0).start()
                val x = v.x + mHeight / 2; val y = v.y + mHeight / 2
                if (v.id == R.id.image1) { mLX = x; mLY = y } else { mRX = x; mRY = y }
            }
        }
        return true
    }

    override fun onClick(v: View) {
        if (DEBUG) Slog.d(TAG, "wrote values")
        updatePosition(false, false)
        mPrefs!!.edit().putString("left_trigger_x", lx.toString()).putString("left_trigger_y", ly.toString())
            .putString("right_trigger_x", rx.toString()).putString("right_trigger_y", ry.toString()).apply()
        Utils.writeValue("/proc/touchpanel/left_trigger_x",  lx.toString())
        Utils.writeValue("/proc/touchpanel/left_trigger_y",  ly.toString())
        Utils.writeValue("/proc/touchpanel/right_trigger_x", rx.toString())
        Utils.writeValue("/proc/touchpanel/right_trigger_y", ry.toString())
        hide()
    }

    fun reset() {
        mLX = 540f; mLY = 700f; mRX = 540f; mRY = 1700f; mBX = 200f; mBY = 2000f
        mPrefs!!.edit().putString("left_trigger_x", mLX.toString()).putString("left_trigger_y", mLY.toString())
            .putString("right_trigger_x", mRX.toString()).putString("right_trigger_y", mRY.toString()).apply()
        Utils.writeValue("/proc/touchpanel/left_trigger_x",  mLX.toString())
        Utils.writeValue("/proc/touchpanel/left_trigger_y",  mLY.toString())
        Utils.writeValue("/proc/touchpanel/right_trigger_x", mRX.toString())
        Utils.writeValue("/proc/touchpanel/right_trigger_y", mRY.toString())
        updatePosition(false)
    }

    fun hide() {
        if (!mShowing) return
        mShowing = false
        if (DEBUG) Slog.d(TAG, "hide")
        try {
            if (mView != null && mView!!.isAttachedToWindow) windowManager!!.removeView(mView)
            if (mReceiverRegistered) { mContext.unregisterReceiver(mIntentReceiver); mReceiverRegistered = false }
        } catch (e: Exception) { Slog.e(TAG, "Error hiding trigger overlay", e) }
        mView = null; image1 = null; image2 = null; button = null; mInitialized = false
    }

    private fun updatePosition(def: Boolean) = updatePosition(def, true)

    private fun updatePosition(def: Boolean, update: Boolean) {
        val wm = windowManager ?: return
        val metrics = mContext.resources.displayMetrics
        val size = Point(metrics.widthPixels, metrics.heightPixels)
        val rot = wm.defaultDisplay.rotation
        if (def) mRotation = 0
        var LX = mLX; var LY = mLY; var RX = mRX; var RY = mRY; var BX = mBX; var BY = mBY
        val rotation = if (!update) 0 else rot
        when (rotation) {
            Surface.ROTATION_90 -> {
                if (mRotation == Surface.ROTATION_270) { LX = size.x - mLX; LY = size.y - mLY; RX = size.x - mRX; RY = size.y - mRY; BX = size.x - mBX; BY = size.y - mBY }
                else { LX = mLY; LY = size.y - mLX; RX = mRY; RY = size.y - mRX; BX = mBY; BY = size.y - mBX }
                image1?.rotation = 0f; image2?.rotation = 0f; button?.rotation = 0f
            }
            Surface.ROTATION_270 -> {
                if (mRotation == Surface.ROTATION_90) { LX = size.x - mLX; LY = size.y - mLY; RX = size.x - mRX; RY = size.y - mRY; BX = size.x - mBX; BY = size.y - mBY }
                else { LX = size.x - mLY; LY = mLX; RX = size.x - mRY; RY = mRX; BX = size.x - mBY; BY = mBX }
                image1?.rotation = 180f; image2?.rotation = 180f; button?.rotation = 180f
            }
            else -> {
                if (mRotation == Surface.ROTATION_90) { LX = (if (!update) 1080f else size.x.toFloat()) - mLY; LY = mLX; RX = (if (!update) 1080f else size.x.toFloat()) - mRY; RY = mRX; BX = (if (!update) 1080f else size.x.toFloat()) - mBY; BY = mBX }
                else if (mRotation == Surface.ROTATION_270) { LX = mLY; LY = (if (!update) 2400f else size.y.toFloat()) - mLX; RX = mRY; RY = (if (!update) 2400f else size.y.toFloat()) - mRX; BX = mBY; BY = (if (!update) 2400f else size.y.toFloat()) - mBX }
                image1?.rotation = 90f; image2?.rotation = 90f; button?.rotation = 90f
            }
        }
        if (update) {
            val h = mHeight / 2
            image1?.animate()?.x(LX - h)?.y(LY - h)?.setDuration(0)?.start()
            image2?.animate()?.x(RX - h)?.y(RY - h - (if (def) mHeight.toFloat() else 0f))?.setDuration(0)?.start()
            button?.animate()?.x(BX - h)?.y(BY - h - (if (def) 2f * mHeight else 0f))?.setDuration(0)?.start()
            mRotation = rotation; mLX = LX; mLY = LY; mRX = RX; mRY = RY; mBX = BX; mBY = BY
        }
        lx = LX; ly = LY; rx = RX; ry = RY; bx = BX; by = BY
    }
}

// ─── TriggerUtils ─────────────────────────────────────────────────
class TriggerUtils(context: Context) {
    private val loadedSoundIds = ArrayList<Int>()
    private val soundsMap = HashMap<String, Int>()
    private var mIsSoundPoolLoadComplete = false
    private var mSoundPool: SoundPool? = null
    private val mContext: Context = context
    private var mPrevEventTime: Long = 0
    private var mKeycode = 0; private var mEventAction = 0
    private var mCount = 0; private var mTapCount = 0

    companion object {
        @JvmStatic var sInstance: TriggerUtils? = null
        private val DEBUG = Utils.DEBUG
        private const val TAG = "TriggerUtils"
        @JvmStatic fun getInstance(context: Context): TriggerUtils {
            if (sInstance == null) { sInstance = TriggerUtils(context); if (DEBUG) Slog.d(TAG, "Creating new instance") }
            return sInstance!!
        }
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BOOT_COMPLETED == intent.action &&
                Settings.System.getInt(mContext.contentResolver, "trigger_sound", 0) == 1) loadSoundResource()
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(mBroadcastReceiver, filter)
        if (Settings.System.getInt(context.contentResolver, "trigger_sound", 0) == 1) loadSoundResource()
    }

    fun triggerAction(left: Boolean, open: Boolean) {
        if (DEBUG) Slog.d(TAG, "left=$left, open=$open")
        if (Settings.System.getInt(mContext.contentResolver, "trigger_sound", 0) != 1) return
        if (!mIsSoundPoolLoadComplete && loadedSoundIds.isEmpty()) loadSoundResource()
        val am = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val type = Settings.System.getString(mContext.contentResolver, "trigger_sound_type") ?: "classic"
        val key = "$type-${if (left) 0 else 1}-${if (open) 1 else 0}"
        if (DEBUG) Slog.d(TAG, "sound=$key")
        playSound(key, false)
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        mSoundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        mSoundPool!!.setOnLoadCompleteListener { _, n, status ->
            if (status == 0) { loadedSoundIds.add(n); if (loadedSoundIds.size == 16) mIsSoundPoolLoadComplete = true }
        }
    }

    private fun load(resId: Int): Int = mSoundPool?.load(mContext, resId, 1) ?: -1

    fun loadSoundResource() {
        releaseSoundResource(); initSoundPool()
        soundsMap["classic-0-0"] = load(R.raw.keys_kanata_close_l)
        soundsMap["classic-1-0"] = load(R.raw.keys_kanata_close_r)
        soundsMap["classic-0-1"] = load(R.raw.keys_kanata_open_l)
        soundsMap["classic-1-1"] = load(R.raw.keys_kanata_open_r)
        soundsMap["bullet-0-0"]  = load(R.raw.keys_mechanicals_close_l)
        soundsMap["bullet-1-0"]  = load(R.raw.keys_mechanicals_close_r)
        soundsMap["bullet-0-1"]  = load(R.raw.keys_mechanicals_open_l)
        soundsMap["bullet-1-1"]  = load(R.raw.keys_mechanicals_open_r)
        soundsMap["current-0-0"] = load(R.raw.keys_scifi_close_l)
        soundsMap["current-1-0"] = load(R.raw.keys_scifi_close_r)
        soundsMap["current-0-1"] = load(R.raw.keys_scifi_open_l)
        soundsMap["current-1-1"] = load(R.raw.keys_scifi_open_r)
        soundsMap["wind-0-0"]    = load(R.raw.keys_car_close_l)
        soundsMap["wind-1-0"]    = load(R.raw.keys_car_close_r)
        soundsMap["wind-0-1"]    = load(R.raw.keys_car_open_l)
        soundsMap["wind-1-1"]    = load(R.raw.keys_car_open_r)
    }

    fun playSound(s: String, loop: Boolean) {
        if (!mIsSoundPoolLoadComplete || !soundsMap.containsKey(s)) return
        val id = soundsMap[s]!!
        val parts = s.split("-")
        val lVol = if (parts.size >= 2 && parts[1].toIntOrNull() == 0) 1.0f else 0.1f
        val rVol = if (parts.size >= 2 && parts[1].toIntOrNull() == 1) 1.0f else 0.1f
        mSoundPool?.play(id, lVol, rVol, 1, if (loop) -1 else 0, 0.95f)
    }

    fun releaseSoundResource() {
        mSoundPool?.let { mIsSoundPoolLoadComplete = false; soundsMap.clear(); loadedSoundIds.clear(); it.release(); mSoundPool = null }
    }

    fun onEvent(event: KeyEvent) {
        if (isDoubleClick(event)) handleDoubleClick(event.keyCode == 59)
        if (isLongPress(event))   handleLongPress(event.keyCode == 59)
    }

    fun handleDoubleClick(left: Boolean) {
        val key = if (left) CustomTrigger.PREF_LEFT_TRIGGER_DOUBLE_CLICK else CustomTrigger.PREF_RIGHT_TRIGGER_DOUBLE_CLICK
        processAction(Utils.getStringSystem(mContext, key, Action.ACTION_NULL))
    }

    fun handleLongPress(left: Boolean) {
        val key = if (left) CustomTrigger.PREF_LEFT_TRIGGER_LONGPRESS else CustomTrigger.PREF_RIGHT_TRIGGER_LONGPRESS
        processAction(Utils.getStringSystem(mContext, key, Action.ACTION_NULL))
    }

    fun processAction(action: String?) {
        if (action == null || action == Action.ACTION_NULL || Utils.isGameApp(mContext) ||
            Utils.getIntSystem(mContext, CustomTrigger.PREF_CUSTOM_TRIGGER_ENABLE, 1) != 1) return
        doHapticFeedback()
        Action.processAction(mContext, action, false)
    }

    private fun doHapticFeedback() {
        val vib = mContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Utils.getIntSystem(mContext, CustomTrigger.KEY_TRIGGER_HAPTIC_FEEDBACK, 1) != 0 && vib.hasVibrator())
            vib.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun triggerVibration(durationMs: Int) {
        val vib = mContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (vib.hasVibrator()) vib.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun isDoubleClick(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) { mKeycode = event.keyCode; return false }
        val now = SystemClock.uptimeMillis()
        val time = now - mPrevEventTime
        val isDouble = time < 300 && mTapCount == 2
        if (time > 300) mTapCount = 0
        mTapCount++; mPrevEventTime = now
        if (isDouble) mTapCount = 0
        return isDouble
    }

    fun isLongPress(event: KeyEvent): Boolean {
        return if (mEventAction == event.action) {
            if (mCount > 12) { mCount = 0; true } else { mCount++; false }
        } else { mEventAction = event.action; mCount = 0; false }
    }
}
