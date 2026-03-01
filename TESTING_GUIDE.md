# Testing Guide for YT2Local App

## What Was Fixed

The "Error: instance not initialized" was caused by **missing FFmpeg initialization**. 

When using audio extraction features (`-x`, `--extract-audio`), the youtubedl-android library requires **both** YoutubeDL and FFmpeg to be initialized:

```kotlin
YoutubeDL.getInstance().init(this)
FFmpeg.getInstance().init(this)  // ← This was missing!
```

## Changes Made

1. **Created `YT2LocalApplication.kt`** - Custom Application class that initializes both YoutubeDL and FFmpeg at app startup
2. **Updated `AndroidManifest.xml`** - Registered the custom Application class
3. **Enhanced initialization tracking** - Added StateFlow to monitor when initialization completes
4. **Improved UI feedback** - Button shows "Initializing..." until ready
5. **Added comprehensive logging** - Track initialization progress and errors

## How to Test

### 1. Install the APK

The APK is located at:
```
c:\Users\bfsd\Documents\GitHub\yt_mp3_apk\app\build\outputs\apk\debug\app-debug.apk
```

Install it on your Android device or emulator:
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 2. View Logs (Important!)

To see what's happening during initialization, view the logcat:

```powershell
# View all app logs
adb logcat -s YT2LocalApp:D YoutubeRepository:D

# Or view everything
adb logcat | Select-String "YT2Local|YoutubeDL|FFmpeg"
```

### 3. Test the App

1. **Launch the app** - You should see "Initializing..." on the button
2. **Wait 5-10 seconds** - First launch takes time to extract libraries
3. **Button becomes active** - Shows "Convert & Download" when ready
4. **Paste a YouTube URL** - Try: `https://youtu.be/dQw4w9WgXcQ`
5. **Select format** - Audio (MP3) or Video (Max)
6. **Click "Convert & Download"**
7. **Check Downloads folder** - Look in `Downloads/yt2local/`

### 4. Expected Logs

**Successful initialization:**
```
D/YT2LocalApp: Application onCreate() called
D/YT2LocalApp: Starting YoutubeDL initialization...
D/YT2LocalApp: YoutubeDL initialized successfully!
D/YT2LocalApp: Starting FFmpeg initialization...
D/YT2LocalApp: FFmpeg initialized successfully!
```

**During download:**
```
D/YoutubeRepository: Starting download for URL: https://youtu.be/..., isAudio: true
D/YoutubeRepository: Calling YoutubeDL.getInstance().execute()...
```

## Troubleshooting

### If you still see "instance not initialized":

1. **Check logcat** for error messages:
   ```powershell
   adb logcat -s YT2LocalApp:E YoutubeRepository:E
   ```

2. **Clear app data** and reinstall:
   ```powershell
   adb uninstall com.example.yt2local
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```

3. **Check permissions** - Make sure the app has storage permissions

### If initialization fails:

- Check if you have enough storage space (needs ~100MB for first init)
- Try on a different device/emulator
- Check the error message in logcat

## File Locations

After successful download, files are saved to:
```
/storage/emulated/0/Download/yt2local/yt_YYYYMMDD_HHMMSS.mp3
```

## Next Steps

Once the app works:
- Test with different YouTube URLs
- Try both audio and video downloads
- Check file quality and naming
- Test the share functionality (share a YouTube link to the app)

## Need Help?

If you're still experiencing issues, please share:
1. The logcat output (especially errors)
2. What happens when you click the button
3. Any error messages shown in the UI
