package com.example.xiaomaotai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App UI 视觉风格（不改业务逻辑，只改配色与组件语言）
 * 默认 A Soft Diary（彩色渐变卡片）
 */
enum class UiStyle(val id: String, val title: String, val subtitle: String) {
    SoftDiary(
        id = "soft_diary",
        title = "彩色渐变",
        subtitle = "彩色渐变卡片，饱和色块与白字"
    ),
    GlassCountdown(
        id = "glass_countdown",
        title = "玻璃倒计时",
        subtitle = "白卡细边，极淡色洗"
    );

    companion object {
        fun fromId(id: String?): UiStyle {
            return entries.find { it.id == id } ?: SoftDiary
        }
    }
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.SoftDiary }
