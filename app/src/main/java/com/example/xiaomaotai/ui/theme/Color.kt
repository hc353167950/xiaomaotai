package com.example.xiaomaotai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色系统。
 *
 * 设计方向：温暖的纸感底色 + 高饱和强调色 + 柔和渐变卡片。
 * - 底色不再是纯灰，而带一点点暖调，长时间看不累
 * - 卡片用"双色渐变 + 柔和阴影"，比纯色更有质感
 * - 强调色取自自然色系（海、苔、珊瑚、暮紫、琥珀），避免廉价的高饱和原色
 */

// ---------- 品牌强调色 ----------
val BrandBlue = Color(0xFF2E6BE6)
val BrandBlueLight = Color(0xFF5B8DEF)
val BrandBlueDark = Color(0xFF1B4FC4)

val BrandCoral = Color(0xFFFF6B5B)
val BrandAmber = Color(0xFFFFA726)
val BrandMint = Color(0xFF2DC98B)
val BrandViolet = Color(0xFF8B5CF6)
val BrandRose = Color(0xFFF45B8E)
val BrandTeal = Color(0xFF14B8A6)
val BrandIndigo = Color(0xFF6366F1)
val BrandRed = Color(0xFFEF4444)

// ---------- 浅色主题（暖纸感） ----------
/** 页面底色：极浅暖灰，比纯白柔和 */
val LightBackground = Color(0xFFF6F5F3)

/** 卡片表面：纯白，与底色拉开层次 */
val LightSurface = Color(0xFFFFFFFF)

/** 次级表面：用于搜索框、分组底 */
val LightSurfaceSecondary = Color(0xFFF0EFEC)

/** 三级表面：更浅的填充 */
val LightSurfaceTertiary = Color(0xFFFAF9F7)

val LightSeparator = Color(0xFFE5E3DE)
val LightOutline = Color(0xFFD8D5CF)

val LightLabel = Color(0xFF1A1A1C)
val LightSecondaryLabel = Color(0xFF6B6B70)
val LightTertiaryLabel = Color(0xFF9A9AA0)

/** 顶栏 / 状态栏 */
val LightChrome = Color(0xFFF6F5F3)

// ---------- 深色主题（真黑底 + 微亮卡片） ----------
val DarkBackground = Color(0xFF0E0E10)
val DarkSurface = Color(0xFF1C1C1F)
val DarkSurfaceSecondary = Color(0xFF26262A)
val DarkSurfaceTertiary = Color(0xFF303035)

val DarkSeparator = Color(0xFF35353A)
val DarkOutline = Color(0xFF42424A)

val DarkLabel = Color(0xFFF5F5F7)
val DarkSecondaryLabel = Color(0xFFA8A8B0)
val DarkTertiaryLabel = Color(0xFF6E6E78)

val DarkChrome = Color(0xFF0E0E10)

// ---------- 渐变对（卡片主视觉） ----------
/**
 * 一组渐变配色：用于事件卡片的背景。
 * 每对都是「浅色端 → 深色端」，保证白字在上面的对比度。
 */
data class GradientPair(
    val start: Color,
    val end: Color,
    /** 光晕色，用于卡片右侧柔光 */
    val glow: Color,
    /** 深色主题下的降饱和版本 */
    val darkStart: Color,
    val darkEnd: Color
)

val GradientPalette = listOf(
    GradientPair(Color(0xFF5B8DEF), Color(0xFF2E6BE6), Color(0xFF7BA5FF),
        Color(0xFF3A5FA8), Color(0xFF1B3F8F)),
    GradientPair(Color(0xFF4FD1A5), Color(0xFF12A67C), Color(0xFF6FE3BC),
        Color(0xFF2E8A6C), Color(0xFF0F6B51)),
    GradientPair(Color(0xFFFF9A8B), Color(0xFFFF6B5B), Color(0xFFFFB4A8),
        Color(0xFFB8574B), Color(0xFF9A3F36)),
    GradientPair(Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFFC4B0FF),
        Color(0xFF6D4BC0), Color(0xFF533A94)),
    GradientPair(Color(0xFFFFC46B), Color(0xFFFFA726), Color(0xFFFFD89B),
        Color(0xFFB87A1E), Color(0xFF8F5D12)),
    GradientPair(Color(0xFFF472B6), Color(0xFFF43F8E), Color(0xFFFBA3CE),
        Color(0xFFB03571), Color(0xFF8A2858)),
    GradientPair(Color(0xFF2DD4BF), Color(0xFF14B8A6), Color(0xFF5EEAD4),
        Color(0xFF1E8A7E), Color(0xFF136B61)),
    GradientPair(Color(0xFF818CF8), Color(0xFF6366F1), Color(0xFFA5AEFF),
        Color(0xFF4F52B8), Color(0xFF3A3D8F)),
    GradientPair(Color(0xFF94A3B8), Color(0xFF475569), Color(0xFFB8C4D4),
        Color(0xFF5A6577), Color(0xFF2E3745)),
    GradientPair(Color(0xFF6EE7B7), Color(0xFF10B981), Color(0xFF9BF6D4),
        Color(0xFF2E8F6B), Color(0xFF0E7052))
)

/** 今日卡片：金橙渐变，全应用唯一，用于强调 */
val TodayGradientStart = Color(0xFFFFC46B)
val TodayGradientEnd = Color(0xFFFF8A3D)
val TodayGlow = Color(0xFFFFD89B)

// ---------- 语义色 ----------
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
val InfoBlue = Color(0xFF3B82F6)

// ---------- 兼容旧引用 ----------
val AppleBlue = BrandBlue
val AppleBlueDark = BrandBlueLight
val AppleRed = BrandRed
val ApplePurple = BrandViolet
val LightSurfaceElevated = LightSurface
val LightGroupedBackground = LightBackground
val DarkGroupedBackground = DarkBackground
val LightFill = Color(0xFF787880).copy(alpha = 0.12f)
val DarkFill = Color(0xFF787880).copy(alpha = 0.24f)
val Purple80 = Color(0xFFB0C4FF)
val PurpleGrey80 = Color(0xFFC5C5D0)
val Pink80 = Color(0xFFE8B4C8)
val Purple40 = BrandBlue
val PurpleGrey40 = Color(0xFF8E8E93)
val Pink40 = BrandRose
