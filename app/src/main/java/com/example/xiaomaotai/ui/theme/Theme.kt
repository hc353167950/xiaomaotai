package com.example.xiaomaotai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// A · 最初 Apple 简约（灰底 + 系统色）
private val SoftDiaryLight = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = LightFill,
    onSecondaryContainer = LightLabel,
    tertiary = ApplePurple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2E6FF),
    onTertiaryContainer = Color(0xFF2A0054),
    background = LightBackground,
    onBackground = LightLabel,
    surface = LightSurface,
    onSurface = LightLabel,
    surfaceVariant = LightSurfaceSecondary,
    onSurfaceVariant = Color(0xFF3C3C43).copy(alpha = 0.65f),
    outline = LightSeparator,
    outlineVariant = Color(0xFFD1D1D6),
    error = AppleRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    inversePrimary = AppleBlueDark,
    surfaceTint = Color.Transparent
)

// B · Glass Countdown
private val GlassLight = lightColorScheme(
    primary = Color(0xFF3A7BD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF4FC),
    onPrimaryContainer = Color(0xFF1A3A5C),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F6),
    onSecondaryContainer = Color(0xFF3A3A3C),
    tertiary = Color(0xFF7B74C8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2F1FA),
    onTertiaryContainer = Color(0xFF2E2A55),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF7F8FA),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFE2E4E9),
    outlineVariant = Color(0xFFEEEFF2),
    error = Color(0xFFE35D5B),
    onError = Color.White,
    errorContainer = Color(0xFFFDF0EF),
    onErrorContainer = Color(0xFF5C1A18),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF5F5F7),
    inversePrimary = Color(0xFF8AB6F0),
    surfaceTint = Color.Transparent
)

private val SharedDark = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A8F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF98989D),
    onSecondary = Color.Black,
    secondaryContainer = DarkFill,
    onSecondaryContainer = DarkLabel,
    tertiary = Color(0xFFBF5AF2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A1A6B),
    onTertiaryContainer = Color(0xFFF2E6FF),
    background = DarkBackground,
    onBackground = DarkLabel,
    surface = DarkSurface,
    onSurface = DarkLabel,
    surfaceVariant = DarkSurfaceSecondary,
    onSurfaceVariant = DarkSecondaryLabel,
    outline = DarkSeparator,
    outlineVariant = Color(0xFF48484A),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = AppleBlue,
    surfaceTint = Color.Transparent
)

@Composable
fun XiaoMaoTaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiStyle: UiStyle = UiStyle.SoftDiary,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> SharedDark
        uiStyle == UiStyle.GlassCountdown -> GlassLight
        else -> SoftDiaryLight
    }

    val chrome = when {
        darkTheme -> DarkChrome
        uiStyle == UiStyle.GlassCountdown -> Color(0xFFF7F8FA)
        else -> LightChrome
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = chrome.toArgb()
            window.navigationBarColor = chrome.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalUiStyle provides uiStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
