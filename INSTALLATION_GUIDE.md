# YT2Local APK Installation Guide

## The Problem
You're experiencing a "Broken pipe (32)" error when trying to install via ADB. This is a common issue with Android emulators.

## Solution 1: Drag and Drop (EASIEST) ⭐

1. **Locate the APK file**:
   ```
   C:\Users\bfsd\Documents\GitHub\yt_mp3_apk\app\build\outputs\apk\debug\app-debug.apk
   ```

2. **Open your Android Emulator** (make sure it's running)

3. **Drag the APK file** and drop it onto the emulator window

4. **Wait for installation** - You'll see a notification when it's installed

5. **Open the app** - Look for "YT2Local" in the app drawer

---

## Solution 2: Install via Android Studio

1. **Open Android Studio**

2. **Go to**: Run → Select Device → [Your Emulator]

3. **Drag and drop** the APK file into Android Studio

4. **Or use**: Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Then click "locate" and install from there

---

## Solution 3: Use Emulator's APK Installer

1. **Open the emulator**

2. **Click the "..." (More) button** on the emulator toolbar

3. **Go to**: Settings → System → Advanced → Developer options

4. **Enable**: "Install via USB" or "USB debugging"

5. **Try ADB again** or use the Files app to navigate to Downloads and install

---

## Solution 4: Fix ADB Connection (Advanced)

If you want to fix the ADB issue:

### Step 1: Restart Everything
```powershell
# Kill ADB
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" kill-server

# Close the emulator completely

# Start ADB
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" start-server

# Start emulator again
```

### Step 2: Cold Boot Emulator
1. Close the emulator
2. Open Android Studio → AVD Manager
3. Click the dropdown next to your emulator
4. Select "Cold Boot Now"
5. Wait for emulator to fully start
6. Try installing again

### Step 3: Increase ADB Timeout
```powershell
# Set environment variable for longer timeout
$env:ADB_INSTALL_TIMEOUT = "10"

# Try install again
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

### Step 4: Use WiFi ADB (if USB fails)
```powershell
# Connect via TCP/IP
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" tcpip 5555
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" connect 127.0.0.1:5555

# Try install
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 127.0.0.1:5555 install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## Solution 5: Install on Physical Device

If you have an Android phone:

1. **Enable Developer Options**:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back → Developer Options
   - Enable "USB Debugging"

2. **Connect your phone** via USB

3. **Copy APK to phone**:
   - Copy `app-debug.apk` to your phone's Downloads folder

4. **Install**:
   - Open Files app on phone
   - Navigate to Downloads
   - Tap `app-debug.apk`
   - Allow installation from unknown sources if prompted
   - Install

---

## Recommended: Use Drag and Drop! 🎯

The **easiest and most reliable method** is to simply:
1. Open your emulator
2. Drag `app-debug.apk` from File Explorer
3. Drop it onto the emulator window
4. Done!

---

## After Installation

Once installed, you can test the app:

1. **Open YT2Local** from the app drawer
2. **Wait for initialization** (5-10 seconds on first launch)
3. **Paste a YouTube URL**
4. **Select Audio or Video**
5. **Click "Convert & Download"**

To view logs:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s YT2LocalApp:D YoutubeRepository:D
```

---

## Still Having Issues?

If none of these work:
1. Try a different emulator (create a new AVD)
2. Update Android SDK Platform Tools
3. Restart your computer
4. Check if antivirus is blocking ADB
