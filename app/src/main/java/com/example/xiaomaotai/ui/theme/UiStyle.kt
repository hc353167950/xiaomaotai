package com.example.xiaomaotai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App 视觉风格（不改业务逻辑，只改配色与组件语言）。
 *
 * 两套风格共用同一套间距/圆角/字号令牌，只切换卡片的表现形式。
 */
enum class UiStyle(val id: String, val title: String, val subtitle: String) {
    SoftDiary(
        id = "soft_diary",
        title = "流光渐变",
        subtitle = "高饱和双色渐变，柔光与白字"
    ),
    GlassCountdown(
        id = "glass_countdown",
        title = "清透白卡",
        subtitle = "白底细描边，色条与淡光晕"
    );

    companion object {
        fun fromId(id: String?): UiStyle {
            return entries.find { it.id == id } ?: SoftDiary
        }
    }
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.SoftDiary }
