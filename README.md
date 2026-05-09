# XiaomiParts

## Features

### RGB LED Management
- Customize LED behavior and color settings
- Configure LED notifications
- Control LED patterns and effects

### Game Mode
- Game key customization
- Touch injection support
- Custom trigger management for gaming scenarios

### Vibration Control
- Adjust vibrator strength
- Customize haptic feedback
- Configure vibration patterns

### Advanced Features
- Trigger System: Create custom triggers for specific scenarios
- Ambient Display: Wake screen on notifications with custom gestures
- Sensor Features: Tilt, pickup, proximity, and hand wave gestures
- Custom Action Mapping: Bind gestures to apps, functions, and system actions
- Quick Settings Integration: Direct access to Settings app

### System Actions
Map gestures to any of these actions:
- Launch apps or custom applications
- Home, Back, Menu buttons
- IME Switcher
- Power menu and Screen Off
- Recents
- Search
- Camera
- Media controls (play/pause, next, previous)
- Screenshot
- Last app
- Notifications
- Quick Settings panel
- Flashlight/Torch
- Ring/Vibrate/Silent cycling

## Building

### Prerequisites
- Android SDK 36 or higher
- Kotlin compiler
- Apache License 2.0 compatible build environment
- AOSP Build Environment

### Build Commands

Build the APK:
```bash
m XiaomiParts
```

Build as system app:
```bash
mm
```

### Build Files
- Android.bp: Build configuration using Android Soong
- proguard.flags: ProGuard obfuscation rules
- AndroidManifest.xml: App manifest with system permissions

## Project Structure

```
src/org/lineageos/xiaomiparts/
├── XiaomiPartsActivity.kt          - Main application activity
├── XiaomiPartsFragment.kt          - Main settings fragment
├── SystemServices.kt               - System service interfaces
├── gamekey/                        - Game key and trigger mapping
│   ├── GamekeyService.kt
│   ├── GamekeyTouchInjector.kt
│   └── TriggersReader.kt
├── gestures/                       - Screen-off gesture handling
│   └── TouchGestures.kt
├── led/                            - LED management
│   ├── LedManager.kt
│   └── LedSettingsFragment.kt
├── triggers/                       - Trigger management system
│   ├── TriggerManager.kt
│   └── TriggerSettingsFragment.kt
├── ui/                             - User interface components
│   ├── components/
│   ├── screens/
│   └── theme/
├── util/                           - Utility classes
│   ├── AppList.kt
│   └── Extensions.kt
└── vibration/                      - Vibration control
    └── VibratorStrengthPreference.kt

res/
├── drawable/                       - UI icons and shapes
├── layout/                         - Activity and fragment layouts
├── menu/                           - Menu resources
├── mipmap/                         - App launcher icons
├── values/                         - Strings, colors, dimensions
└── xml/                            - Preferences and configurations
```

## Permissions

The app requires the following system permissions:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
<uses-permission android:name="android.permission.DEVICE_POWER" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INJECT_EVENTS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
<uses-permission android:name="android.permission.FORCE_STOP_PACKAGES" />
<uses-permission android:name="android.permission.STATUS_BAR" />
<uses-permission android:name="android.permission.READ_WALLPAPER_INTERNAL" />
```

Note: This is a system application (uses `android:sharedUserId="android.uid.system"`) and requires system-level permissions to function.

## Configuration

### Main Settings Files

- xiaomiparts_main.xml: Main preference hierarchy
- led_settings.xml: LED customization options
- screen_off_gesture.xml: Gesture configuration
- trigger_settings.xml: Trigger management
- custom_trigger.xml: Custom trigger definitions
- arrays.xml: String arrays for drop-down options
- config.xml: Feature configuration flags


### Init Scripts

- init.xiaomiparts.rc: Xiaomi Parts initialization

## Contributing

Contributions are welcome! Please ensure your code:
- Follows Kotlin best practices
- Maintains compatibility with Android SDK 36+
- Respects the Apache License 2.0
- Includes proper documentation

## License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.

Copyright (C) 2025 XiaomiParts Project

## Credits

- Based on LineageOS system applications
- Inspired by AospExtended Project
- Special thanks to the Xiaomi-MT6893-dev

## Support

For issues, feature requests, and discussions, please use the GitHub Issues page.

## Installation Requirements

This is a system-level application designed for POCO F3 GT / REDMI K40 GAMING. Installation requires:
- ADB access or system-level app installation
- Compatibility with Android SDK 36 and higher
- System permissions grant
