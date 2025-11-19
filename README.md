# YT2Local

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
