/*
 * Copyright (C) 2019 The LineageOS Project
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

package org.lineageos.device.triggers;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.lineageos.device.R;
import org.lineageos.device.util.Utils;

public class TriggerService implements View.OnTouchListener, View.OnClickListener {
    private static final boolean DEBUG = Utils.DEBUG;
    private static final String TAG = "TriggerService";

    private SharedPreferences mPrefs;
    private Context mContext;
    private WindowManager mWindowManager;
    private static TriggerService mInstance;

    private View mView;
    private ImageView mLeftTriggerImage;
    private ImageView mRightTriggerImage;
    private ImageView mSaveButton;
    private ImageView mResetButton;
    private LinearLayout mInfoBanner;
    private TextView mAppNameText;

    private WindowManager.LayoutParams mLayoutParams;
    private Point mScreenSize = new Point();

    // Touch offset and temporary coordinates
    private float mTouchOffsetX, mTouchOffsetY;
    
    // Persistent normalized coordinates
    private float mNormLeftX, mNormLeftY;
    private float mNormRightX, mNormRightY;
    
    // Current screen coordinates
    private float mScreenLeftX, mScreenLeftY;
    private float mScreenRightX, mScreenRightY;
    
    // Save button coordinates
    private float mButtonX = 200;
    private float mButtonY = 2000;

    // Reset button coordinates
    private float mResetX = 200;
    private float mResetY = 1800;
    
    private int mMarkerHeight;
    private int mCurrentRotation;

    private boolean mInitialized = false;
    private boolean mReceiverRegistered = false;
    private boolean mShowing = false;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                updatePosition(false);
            }
        }
    };

    private TriggerService(Context context) {
        mContext = context;
    }

    public static TriggerService getInstance(Context context) {
        if (mInstance == null) {
            if (DEBUG) Slog.d(TAG, "NEW INSTANCE");
            mInstance = new TriggerService(context.getApplicationContext());
        }
        return mInstance;
    }

    public boolean isShowing() {
        return mShowing;
    }

    private String getPrefix() {
        String pkg = Utils.getForegroundApp(mContext);
        return pkg != null && !pkg.isEmpty() && Utils.isGameApp(mContext) ? "_" + pkg : "";
    }

    public void init(Context context) {
        if (mInitialized) return;
        mInitialized = true;

        mPrefs = Utils.getSharedPreferences(context);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter);
        mReceiverRegistered = true;

        mMarkerHeight = context.getResources().getDimensionPixelSize(R.dimen.image_height);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mScreenSize.x = metrics.widthPixels;
        mScreenSize.y = metrics.heightPixels;

        mView = LayoutInflater.from(context).inflate(R.layout.view, null);
        mLeftTriggerImage = mView.findViewById(R.id.image1);
        mRightTriggerImage = mView.findViewById(R.id.image2);
        mSaveButton = mView.findViewById(R.id.button);
        mResetButton = mView.findViewById(R.id.reset_button);
        mInfoBanner = mView.findViewById(R.id.info_banner);
        mAppNameText = mView.findViewById(R.id.app_name_text);

        setupAppProfileUI();

        mLeftTriggerImage.setOnTouchListener(this);
        mRightTriggerImage.setOnTouchListener(this);
        mSaveButton.setOnClickListener(this);
        mResetButton.setOnClickListener(v -> reset());

        String suffix = getPrefix();
        mNormLeftX = Float.parseFloat(mPrefs.getString("left_trigger_x" + suffix, mPrefs.getString("left_trigger_x", "540")));
        mNormLeftY = Float.parseFloat(mPrefs.getString("left_trigger_y" + suffix, mPrefs.getString("left_trigger_y", "700")));
        mNormRightX = Float.parseFloat(mPrefs.getString("right_trigger_x" + suffix, mPrefs.getString("right_trigger_x", "540")));
        mNormRightY = Float.parseFloat(mPrefs.getString("right_trigger_y" + suffix, mPrefs.getString("right_trigger_y", "1700")));

        mButtonX = 200;
        mButtonY = 2000;
        mResetX = 200;
        mResetY = 1800;

        mLeftTriggerImage.animate().x(mNormLeftX).y(mNormLeftY).setDuration(0).start();
        mRightTriggerImage.animate().x(mNormRightX).y(mNormRightY).setDuration(0).start();
        mSaveButton.animate().x(mButtonX).y(mButtonY).setDuration(0).start();
        mResetButton.animate().x(mResetX).y(mResetY).setDuration(0).start();

        mView.setAlpha(0.6f);
    }

    private void setupAppProfileUI() {
        String pkg = Utils.getForegroundApp(mContext);
        String appName = "Global Profile";
        
        if (pkg != null && !pkg.isEmpty()) {
            try {
                PackageManager pm = mContext.getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                appName = "Profile: " + pm.getApplicationLabel(info).toString();
            } catch (Exception e) {
                appName = "Profile: " + pkg;
            }
        }
        
        if (mAppNameText != null) {
            mAppNameText.setText(appName);
        }
    }

    public void show() {
        if (mShowing) return;
        init(mContext);
        
        if (DEBUG) Slog.d(TAG, "show");
        
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSPARENT);
                
        mLayoutParams.gravity = Gravity.CENTER;
        mLayoutParams.x = 0;
        mLayoutParams.y = 0;
        mLayoutParams.setFitInsetsTypes(0);
        mLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        
        try {
            mWindowManager.addView(mView, mLayoutParams);
            mShowing = true;
            mView.setVisibility(View.VISIBLE);
            mLeftTriggerImage.setVisibility(View.VISIBLE);
            mRightTriggerImage.setVisibility(View.VISIBLE);
            updatePosition(true);
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to add overlay view", e);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchOffsetX = v.getX() - event.getRawX();
            mTouchOffsetY = v.getY() - event.getRawY();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            v.animate()
                    .x(event.getRawX() + mTouchOffsetX)
                    .y(event.getRawY() + mTouchOffsetY)
                    .setDuration(0)
                    .start();
                    
            float x = v.getX() + mMarkerHeight / 2f;
            float y = v.getY() + mMarkerHeight / 2f;
            
            if (v.getId() == R.id.image1) {
                mNormLeftX = x;
                mNormLeftY = y;
            } else if (v.getId() == R.id.image2) {
                mNormRightX = x;
                mNormRightY = y;
            }
            
            if (DEBUG) Slog.d(TAG, "action move x,y : " + x + " " + y);
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if (DEBUG) Slog.d(TAG, "saving values");
        Toast.makeText(mContext, R.string.trigger_saved_toast, Toast.LENGTH_SHORT).show();
        updatePosition(false, false);
        
        String suffix = getPrefix();
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString("left_trigger_x" + suffix, String.valueOf(mScreenLeftX));
        editor.putString("left_trigger_y" + suffix, String.valueOf(mScreenLeftY));
        editor.putString("right_trigger_x" + suffix, String.valueOf(mScreenRightX));
        editor.putString("right_trigger_y" + suffix, String.valueOf(mScreenRightY));
        editor.apply();

        hide();
    }

    public void reset() {
        if (DEBUG) Slog.d(TAG, "reset values");
        mNormLeftX = 540;
        mNormLeftY = 700;
        mNormRightX = 540;
        mNormRightY = 1700;
        mButtonX = 200;
        mButtonY = 2000;
        mResetX = 200;
        mResetY = 1800;
        
        String suffix = getPrefix();
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.remove("left_trigger_x" + suffix);
        editor.remove("left_trigger_y" + suffix);
        editor.remove("right_trigger_x" + suffix);
        editor.remove("right_trigger_y" + suffix);
        editor.apply();
        
        Toast.makeText(mContext, R.string.trigger_mapping_reset_toast, Toast.LENGTH_SHORT).show();
        updatePosition(false);
    }

    public void hide() {
        if (!mShowing) return;
        mShowing = false;
        
        if (DEBUG) Slog.d(TAG, "hide");
        try {
            if (mView != null && mView.isAttachedToWindow()) {
                mWindowManager.removeView(mView);
            }
            if (mReceiverRegistered) {
                mContext.unregisterReceiver(mIntentReceiver);
                mReceiverRegistered = false;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error hiding trigger overlay", e);
        }
        
        mView = null;
        mLeftTriggerImage = null;
        mRightTriggerImage = null;
        mSaveButton = null;
        mResetButton = null;
        mInfoBanner = null;
        mAppNameText = null;
        mInitialized = false;
    }

    private void updatePosition(boolean def) {
        updatePosition(def, true);
    }

    private void updatePosition(boolean isDefaultRotation, boolean animateUpdate) {
        if (DEBUG) Slog.d(TAG, "updatePosition");
        
        Display defaultDisplay = mWindowManager.getDefaultDisplay();
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        Point size = new Point(metrics.widthPixels, metrics.heightPixels);

        int currentRot = defaultDisplay.getRotation();
        if (isDefaultRotation) {
            mCurrentRotation = 0;
        }
        
        float lx = mNormLeftX;
        float ly = mNormLeftY;
        float rx = mNormRightX;
        float ry = mNormRightY;
        float bx = mButtonX;
        float by = mButtonY;
        float resetX = mResetX;
        float resetY = mResetY;
        
        int rotationToApply = animateUpdate ? currentRot : 0;
        float bannerRot = 0f;
        float bannerX = size.x / 2f;
        float bannerY = 150f;

        switch (rotationToApply) {
            case Surface.ROTATION_90:
                if (mCurrentRotation == Surface.ROTATION_270) {
                    lx = size.x - mNormLeftX;
                    ly = size.y - mNormLeftY;
                    rx = size.x - mNormRightX;
                    ry = size.y - mNormRightY;
                    bx = size.x - mButtonX;
                    by = size.y - mButtonY;
                    resetX = size.x - mResetX;
                    resetY = size.y - mResetY;
                } else {
                    lx = mNormLeftY;
                    ly = size.y - mNormLeftX;
                    rx = mNormRightY;
                    ry = size.y - mNormRightX;
                    bx = mButtonY;
                    by = size.y - mButtonX;
                    resetX = mResetY;
                    resetY = size.y - mResetX;
                }
                mLeftTriggerImage.setRotation(0f);
                mRightTriggerImage.setRotation(0f);
                mSaveButton.setRotation(0f);
                mResetButton.setRotation(0f);
                bannerRot = 0f;
                bannerX = size.x / 2f;
                bannerY = 150f;
                break;
                
            case Surface.ROTATION_270:
                if (mCurrentRotation == Surface.ROTATION_90) {
                    lx = size.x - mNormLeftX;
                    ly = size.y - mNormLeftY;
                    rx = size.x - mNormRightX;
                    ry = size.y - mNormRightY;
                    bx = size.x - mButtonX;
                    by = size.y - mButtonY;
                    resetX = size.x - mResetX;
                    resetY = size.y - mResetY;
                } else {
                    lx = size.x - mNormLeftY;
                    ly = mNormLeftX;
                    rx = size.x - mNormRightY;
                    ry = mNormRightX;
                    bx = size.x - mButtonY;
                    by = mButtonX;
                    resetX = size.x - mResetY;
                    resetY = mResetX;
                }
                mLeftTriggerImage.setRotation(180f);
                mRightTriggerImage.setRotation(180f);
                mSaveButton.setRotation(180f);
                mResetButton.setRotation(180f);
                bannerRot = 180f;
                bannerX = size.x / 2f;
                bannerY = size.y - 150f;
                break;
                
            default:
                if (mCurrentRotation == Surface.ROTATION_90) {
                    lx = (!animateUpdate ? 1080 : size.x) - mNormLeftY;
                    ly = mNormLeftX;
                    rx = (!animateUpdate ? 1080 : size.x) - mNormRightY;
                    ry = mNormRightX;
                    bx = (!animateUpdate ? 1080 : size.x) - mButtonY;
                    by = mButtonX;
                    resetX = (!animateUpdate ? 1080 : size.x) - mResetY;
                    resetY = mResetX;
                } else if (mCurrentRotation == Surface.ROTATION_270) {
                    lx = mNormLeftY;
                    ly = (!animateUpdate ? 2400 : size.y) - mNormLeftX;
                    rx = mNormRightY;
                    ry = (!animateUpdate ? 2400 : size.y) - mNormRightX;
                    bx = mButtonY;
                    by = (!animateUpdate ? 2400 : size.y) - mButtonX;
                    resetX = mResetY;
                    resetY = (!animateUpdate ? 2400 : size.y) - mResetX;
                }
                mLeftTriggerImage.setRotation(90f);
                mRightTriggerImage.setRotation(90f);
                mSaveButton.setRotation(90f);
                mResetButton.setRotation(90f);
                bannerRot = 90f;
                bannerX = 150f;
                bannerY = size.y / 2f;
        }

        if (animateUpdate) {
            mLeftTriggerImage.animate()
                    .x(lx - mMarkerHeight / 2f)
                    .y(ly - mMarkerHeight / 2f)
                    .setDuration(0)
                    .start();

            mRightTriggerImage.animate()
                    .x(rx - mMarkerHeight / 2f)
                    .y(ry - mMarkerHeight / 2f - (isDefaultRotation ? mMarkerHeight : 0))
                    .setDuration(0)
                    .start();

            mSaveButton.animate()
                    .x(bx - mMarkerHeight / 2f)
                    .y(by - mMarkerHeight / 2f - (isDefaultRotation ? 2 * mMarkerHeight : 0))
                    .setDuration(0)
                    .start();

            mResetButton.animate()
                    .x(resetX - mMarkerHeight / 2f)
                    .y(resetY - mMarkerHeight / 2f - (isDefaultRotation ? 3 * mMarkerHeight : 0))
                    .setDuration(0)
                    .start();

            if (mInfoBanner != null) {
                // Determine width of banner to center it
                mInfoBanner.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int bannerWidth = mInfoBanner.getMeasuredWidth();
                int bannerHeight = mInfoBanner.getMeasuredHeight();
                
                mInfoBanner.animate()
                        .x(bannerX - bannerWidth / 2f)
                        .y(bannerY - bannerHeight / 2f)
                        .setDuration(0)
                        .start();
                mInfoBanner.setRotation(bannerRot);
            }

            mCurrentRotation = rotationToApply;

            mNormLeftX = lx;
            mNormLeftY = ly;
            mNormRightX = rx;
            mNormRightY = ry;
            mButtonX = bx;
            mButtonY = by;
            mResetX = resetX;
            mResetY = resetY;
        }

        mScreenLeftX = lx;
        mScreenLeftY = ly;
        mScreenRightX = rx;
        mScreenRightY = ry;
    }
}
