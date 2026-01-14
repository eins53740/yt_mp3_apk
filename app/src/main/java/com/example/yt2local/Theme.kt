package com.example.yt2local

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// YouTube-inspired colors
private val YouTubeRed = Color(0xFFFF0000)
private val YouTubeRedDark = Color(0xFFCC0000)
private val DarkBackground = Color(0xFF0F0F0F)
private val DarkSurface = Color(0xFF1F1F1F)
private val DarkSurfaceVariant = Color(0xFF282828)
private val LightBackground = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFF9F9F9)
private val LightSurfaceVariant = Color(0xFFF1F1F1)

private val DarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = YouTubeRedDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF3EA6FF), // YouTube blue
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF263850),
    onSecondaryContainer = Color(0xFF3EA6FF),
    tertiary = Color(0xFF00C853), // Success green
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF606060),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = YouTubeRedDark,
    secondary = Color(0xFF065FD4), // YouTube blue (light mode)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4FF),
    onSecondaryContainer = Color(0xFF065FD4),
    tertiary = Color(0xFF00C853),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF0F0F0F),
    surface = LightSurface,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFCCCCCC),
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun YT2LocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
