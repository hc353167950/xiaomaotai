package com.example.xiaomaotai.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.xiaomaotai.ui.theme.UiStyle

object CardStyleManager {

    data class CardStyle(
        val id: Int,
        val name: String,
        val accent: Color,
        val wash: Color,
        val glow: Color,
        val gradientBrush: Brush,
        val textColor: Color = Color.White,
        val secondaryText: Color = Color.White.copy(alpha = 0.85f)
    ) {
        val tint: Color get() = wash
    }

    // A · 最初彩色渐变卡片
    private val softDiaryStyles = listOf(
        CardStyle(
            1, "雾蓝",
            accent = Color(0xFF007AFF),
            wash = Color(0xFF5AC8FA),
            glow = Color(0xFF007AFF),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)))
        ),
        CardStyle(
            2, "薄荷绿",
            accent = Color(0xFF30D158),
            wash = Color(0xFF64D2FF),
            glow = Color(0xFF30D158),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF64D2FF), Color(0xFF30D158)))
        ),
        CardStyle(
            3, "柔粉",
            accent = Color(0xFFFF375F),
            wash = Color(0xFFFF9F0A),
            glow = Color(0xFFFF375F),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFFFF9F0A), Color(0xFFFF375F)))
        ),
        CardStyle(
            4, "靛紫",
            accent = Color(0xFFBF5AF2),
            wash = Color(0xFF5E5CE6),
            glow = Color(0xFFBF5AF2),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2)))
        ),
        CardStyle(
            5, "珊瑚",
            accent = Color(0xFFFF453A),
            wash = Color(0xFFFF9F0A),
            glow = Color(0xFFFF453A),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFFFF9F0A), Color(0xFFFF453A)))
        ),
        CardStyle(
            6, "青空",
            accent = Color(0xFF0A84FF),
            wash = Color(0xFF64D2FF),
            glow = Color(0xFF0A84FF),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF64D2FF), Color(0xFF0A84FF)))
        ),
        CardStyle(
            7, "葡萄",
            accent = Color(0xFFFF375F),
            wash = Color(0xFFBF5AF2),
            glow = Color(0xFFFF375F),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFFBF5AF2), Color(0xFFFF375F)))
        ),
        CardStyle(
            8, "湖绿",
            accent = Color(0xFF64D2FF),
            wash = Color(0xFF30D158),
            glow = Color(0xFF64D2FF),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF30D158), Color(0xFF64D2FF)))
        ),
        CardStyle(
            9, "琥珀",
            accent = Color(0xFFFF9F0A),
            wash = Color(0xFFFFD60A),
            glow = Color(0xFFFF9F0A),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A)))
        ),
        CardStyle(
            10, "深空",
            accent = Color(0xFF1C1C1E),
            wash = Color(0xFF5856D6),
            glow = Color(0xFF1C1C1E),
            gradientBrush = Brush.linearGradient(listOf(Color(0xFF5856D6), Color(0xFF1C1C1E)))
        )
    )

    // B · 玻璃：低饱和 + 极淡 wash
    private val glassStyles = listOf(
        CardStyle(
            1, "雾蓝", Color(0xFF5B8FD9), Color(0xFFF3F7FC), Color(0xFFD6E6F8),
            Brush.linearGradient(listOf(Color(0xFFF3F7FC), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            2, "薄荷绿", Color(0xFF4FAE86), Color(0xFFF2FAF6), Color(0xFFD4EFE3),
            Brush.linearGradient(listOf(Color(0xFFF2FAF6), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            3, "柔粉", Color(0xFFD47890), Color(0xFFFCF4F6), Color(0xFFF3DCE3),
            Brush.linearGradient(listOf(Color(0xFFFCF4F6), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            4, "靛紫", Color(0xFF7B74C8), Color(0xFFF5F4FB), Color(0xFFE0DEF3),
            Brush.linearGradient(listOf(Color(0xFFF5F4FB), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            5, "珊瑚", Color(0xFFD4846A), Color(0xFFFCF5F2), Color(0xFFF2DDD4),
            Brush.linearGradient(listOf(Color(0xFFFCF5F2), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            6, "青空", Color(0xFF5AA8D4), Color(0xFFF2F8FC), Color(0xFFD4EAF6),
            Brush.linearGradient(listOf(Color(0xFFF2F8FC), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            7, "葡萄", Color(0xFFB07AAF), Color(0xFFFAF4FA), Color(0xFFEBDCEC),
            Brush.linearGradient(listOf(Color(0xFFFAF4FA), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            8, "湖绿", Color(0xFF4FABA0), Color(0xFFF1F9F7), Color(0xFFD3ECE8),
            Brush.linearGradient(listOf(Color(0xFFF1F9F7), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            9, "琥珀", Color(0xFFD0A04A), Color(0xFFFBF7EF), Color(0xFFF0E4C4),
            Brush.linearGradient(listOf(Color(0xFFFBF7EF), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        ),
        CardStyle(
            10, "深空", Color(0xFF7A8090), Color(0xFFF5F6F8), Color(0xFFDEE1E7),
            Brush.linearGradient(listOf(Color(0xFFF5F6F8), Color.White)),
            textColor = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF8A8A8E)
        )
    )

    private fun stylesFor(uiStyle: UiStyle): List<CardStyle> {
        return when (uiStyle) {
            UiStyle.SoftDiary -> softDiaryStyles
            UiStyle.GlassCountdown -> glassStyles
        }
    }

    fun getCardStyle(backgroundId: Int, uiStyle: UiStyle = UiStyle.SoftDiary): CardStyle {
        val styles = stylesFor(uiStyle)
        return styles.find { it.id == backgroundId } ?: styles[0]
    }

    fun getTodayStyle(uiStyle: UiStyle): CardStyle {
        return when (uiStyle) {
            UiStyle.SoftDiary -> CardStyle(
                0, "今日",
                accent = Color(0xFFFF9F0A),
                wash = Color(0xFFFFD60A),
                glow = Color(0xFFFF9F0A),
                gradientBrush = Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A))),
                textColor = Color.White,
                secondaryText = Color.White.copy(alpha = 0.88f)
            )
            UiStyle.GlassCountdown -> CardStyle(
                0, "今日",
                accent = Color(0xFFD0A04A),
                wash = Color(0xFFFFFBF3),
                glow = Color(0xFFF5E6C0),
                gradientBrush = Brush.linearGradient(listOf(Color(0xFFFFFBF3), Color.White)),
                textColor = Color(0xFF1C1C1E),
                secondaryText = Color(0xFF8A8A8E)
            )
        }
    }

    fun getRandomStyleId(): Int = (1..10).random()

    fun getSmartRandomStyleId(usedBackgroundIds: List<Int>): Int {
        val allIds = (1..10).toList()
        val unusedIds = allIds.filter { it !in usedBackgroundIds }
        return if (unusedIds.isNotEmpty()) unusedIds.random() else allIds.random()
    }

    fun getAllStyleIds(): List<Int> = (1..10).toList()

    fun getStyleName(backgroundId: Int, uiStyle: UiStyle = UiStyle.SoftDiary): String {
        return getCardStyle(backgroundId, uiStyle).name
    }
}
