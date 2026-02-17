# YT2Local ProGuard Rules

# Keep YoutubeDL Android library
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# Keep Python executables
-keepclassmembers class * {
    native <methods>;
}

# Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep data classes
-keepclassmembers class com.example.yt2local.DownloadProgress { *; }
-keepclassmembers class com.example.yt2local.DownloadResult { *; }
-keepclassmembers class com.example.yt2local.DownloadHistoryItem { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Suppress warnings for missing classes in dependencies
-dontwarn org.python.**
-dontwarn org.apache.**
-dontwarn javax.**
