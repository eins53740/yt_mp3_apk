t# YT2Local

Android application to convert YouTube videos to MP3 (192kbps) or Video (Max Resolution).

## Features
- **Format Selection**: Choose between Audio (MP3) or Video.
- **High Quality**: Downloads the best available quality.
- **History**: Keeps track of recent downloads.
- **Share Support**: Share URLs directly from the YouTube app to YT2Local.
- **Scoped Storage**: Saves files to `Downloads/yt2local/` correctly on Android 10+.

## How to Build
1. Open this folder in **Android Studio**.
2. Wait for Gradle to sync (it will download dependencies including `youtubedl-android`).
3. Connect a device or start an emulator.
4. Run the `app` configuration.

## Requirements
- Android SDK 34 (or higher)
- JDK 17+

## Note
The first time you run the app and try to download, the `youtubedl-android` library might need to update its internal python/ffmpeg binaries. This happens automatically but requires an internet connection.

## How to Install on Phone
You likely mean **USB Debugging** (Android Auto is for cars).
1. **Enable Developer Options**: Settings > About Phone > Tap "Build Number" 7 times.
2. **Enable USB Debugging**: Settings > System > Developer Options > Enable USB Debugging.
3. **Connect via USB**: Connect your phone to your PC.
4. **Run from Android Studio**: Click the green "Run" arrow in the toolbar. Select your connected device.
   - This will build and install the app automatically.
5. **Manual APK Transfer**:
   - In Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   - Find the APK in `app/build/outputs/apk/debug/app-debug.apk`.
   - Copy this file to your phone's storage.
   - Tap the file on your phone to install.
