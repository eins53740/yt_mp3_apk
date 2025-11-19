# Product Requirement Document (PRD): YT2Local Android App

## 1. Overview
**Product Name:** YT2Local
**Goal:** A simple, efficient Android application to convert YouTube (and other platform) videos into high-quality audio or video files stored locally on the device.
**Target OS:** Android 16 (Targeting latest API Level)

## 2. Functional Requirements

### 2.1 Core Features
1.  **Input Mechanism:**
    -   User can manually paste a video link (YouTube, etc.) into a text field.
    -   *Suggested:* Support for Android "Share" intent to receive URLs directly from the YouTube app or browsers.

2.  **Format Selection:**
    -   User must choose between two output modes before downloading:
        -   **Audio:** MP3 format, 192kbps quality.
        -   **Video:** Maximum available resolution (original format, typically MP4/MKV).

3.  **Conversion & Download:**
    -   Application processes the URL, extracts the stream, converts if necessary (e.g., video to mp3), and saves the file.
    -   *Constraint:* Must handle network interruptions gracefully or at least notify the user.

4.  **File Storage:**
    -   **Path:** Standard Android Download directory.
    -   **Sub-directory:** `/yt2local/`
    -   **Naming Convention:** `yt_<datetime>.<extension>`
        -   Format: `yt_YYYYMMDD_HHmmss.mp3` or `yt_YYYYMMDD_HHmmss.mp4`
    -   *Note:* Using `MediaStore` API is required for modern Android scoped storage compliance, but we will aim to organize it logically as requested.

### 2.2 User Interface (UI)
-   **Main Screen:**
    -   URL Input Field.
    -   "Paste" button (optional, for convenience).
    -   Radio Buttons or Toggle for "Audio (MP3)" vs "Video (Max)".
    -   "Convert & Download" Button.
    -   Status Indicator (Idle, Downloading, Converting, Done, Error).

## 3. Suggested Features (Agent Proposed)
To enhance the user experience, the following features are recommended:

1.  **Share Intent Support:**
    -   Allows users to click "Share" on a video in the YouTube app and select "YT2Local" to instantly populate the URL field.
2.  **Download History:**
    -   A simple list showing recently downloaded files with options to "Open" or "Delete".
3.  **Notifications:**
    -   Progress notification in the status bar during download/conversion.
    -   Completion notification with a "Tap to Open" action.
4.  **Clipboard Auto-Paste:**
    -   Automatically detect if a YouTube link is in the clipboard when the app opens.

## 4. Technical Constraints & Considerations
-   **Android 16 / API Level 35+:** Strict enforcement of Scoped Storage. Direct filesystem paths like `/sdcard/Download` are deprecated; `MediaStore` or `Storage Access Framework` must be used. We will ensure the files appear in the user's "Downloads" folder in the requested subfolder.
-   **Dependencies:**
    -   Likely requires a wrapper around `yt-dlp` (e.g., `youtubedl-android`) and `ffmpeg` for conversion.
    -   Permissions: `INTERNET`, `POST_NOTIFICATIONS`.

## 5. Success Metrics
-   Successful download and conversion of a test YouTube video to MP3 (192k).
-   Successful download of a test video in Max Resolution.
-   File verified in the specified directory structure.
