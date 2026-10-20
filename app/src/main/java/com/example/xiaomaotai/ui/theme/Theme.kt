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

// A · 暖纸 + 高饱和强调
private val SoftDiaryLight = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3ECFF),
    onPrimaryContainer = Color(0xFF0A2E6E),
    secondary = BrandViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE9FE),
    onSecondaryContainer = Color(0xFF2E1B5C),
    tertiary = BrandCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8E5),
    onTertiaryContainer = Color(0xFF5C1A14),
    background = LightBackground,
    onBackground = LightLabel,
    surface = LightSurface,
    onSurface = LightLabel,
    surfaceVariant = LightSurfaceSecondary,
    onSurfaceVariant = LightSecondaryLabel,
    outline = LightOutline,
    outlineVariant = LightSeparator,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E2),
    onErrorContainer = Color(0xFF5C1210),
    inverseSurface = Color(0xFF2A2A2E),
    inverseOnSurface = Color(0xFFF5F5F7),
    inversePrimary = BrandBlueLight,
    surfaceTint = Color.Transparent
)

// B · 玻璃：冷白 + 低饱和
private val GlassLight = lightColorScheme(
    primary = Color(0xFF3A7BD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDF3FC),
    onPrimaryContainer = Color(0xFF16375C),
    secondary = Color(0xFF7B74C8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F0FA),
    onSecondaryContainer = Color(0xFF2A2650),
    tertiary = Color(0xFF4FAE86),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDF8F3),
    onTertiaryContainer = Color(0xFF14402F),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFF65656B),
    outline = Color(0xFFDDE1E8),
    outlineVariant = Color(0xFFEAEDF2),
    error = Color(0xFFDC5D58),
    onError = Color.White,
    errorContainer = Color(0xFFFDEFEE),
    onErrorContainer = Color(0xFF571B18),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF5F5F7),
    inversePrimary = Color(0xFF8AB6F0),
    surfaceTint = Color.Transparent
)

// 深色主题（两套风格共用，强调色保持品牌蓝）
private val SharedDark = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3F7A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3D2E66),
    onSecondaryContainer = Color(0xFFEDE6FF),
    tertiary = Color(0xFFFF9A8B),
    onTertiary = Color(0xFF4A1510),
    tertiaryContainer = Color(0xFF5C221B),
    onTertiaryContainer = Color(0xFFFFDDD7),
    background = DarkBackground,
    onBackground = DarkLabel,
    surface = DarkSurface,
    onSurface = DarkLabel,
    surfaceVariant = DarkSurfaceSecondary,
    onSurfaceVariant = DarkSecondaryLabel,
    outline = DarkOutline,
    outlineVariant = DarkSeparator,
    error = Color(0xFFFF6B62),
    onError = Color.White,
    errorContainer = Color(0xFF8A1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1A1A1C),
    inversePrimary = BrandBlue,
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
        uiStyle == UiStyle.GlassCountdown -> Color(0xFFF7F8FB)
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

    CompositionLocalProvider(
        LocalUiStyle provides uiStyle,
        LocalDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** 供卡片等组件判断深色模式（决定用哪套渐变） */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }
