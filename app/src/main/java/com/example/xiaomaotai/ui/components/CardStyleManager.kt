package com.example.xiaomaotai.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.xiaomaotai.ui.theme.GradientPalette
import com.example.xiaomaotai.ui.theme.TodayGlow
import com.example.xiaomaotai.ui.theme.TodayGradientEnd
import com.example.xiaomaotai.ui.theme.TodayGradientStart
import com.example.xiaomaotai.ui.theme.UiStyle

object CardStyleManager {

    data class CardStyle(
        val id: Int,
        val name: String,
        /** 强调色：用于小色条、徽标、次要文字 */
        val accent: Color,
        /** 极淡色洗：用于白卡顶部渐变 */
        val wash: Color,
        /** 光晕色 */
        val glow: Color,
        /** 卡片主渐变（深色模式下会自动换成降饱和版本） */
        val gradientBrush: Brush,
        val textColor: Color = Color.White,
        val secondaryText: Color = Color.White.copy(alpha = 0.86f)
    )

    /**
     * 渐变卡片样式。
     *
     * 数据源统一为 [GradientPalette]，每项自带深色版本。
     * 通过 [forDark] 决定取哪一组，保证深色模式下不刺眼。
     */
    private fun gradientStyles(forDark: Boolean): List<CardStyle> {
        return GradientPalette.mapIndexed { index, pair ->
            val start = if (forDark) pair.darkStart else pair.start
            val end = if (forDark) pair.darkEnd else pair.end
            CardStyle(
                id = index + 1,
                name = gradientName(index),
                accent = pair.glow,
                wash = start.copy(alpha = 0.16f),
                glow = pair.glow,
                gradientBrush = Brush.linearGradient(
                    colors = listOf(start, end)
                ),
                textColor = Color.White,
                secondaryText = Color.White.copy(alpha = 0.86f)
            )
        }
    }

    private fun gradientName(index: Int): String = when (index) {
        0 -> "深海蓝"
        1 -> "苔原绿"
        2 -> "珊瑚橙"
        3 -> "暮光紫"
        4 -> "琥珀金"
        5 -> "玫瑰粉"
        6 -> "青碧"
        7 -> "靛蓝"
        8 -> "石墨灰"
        9 -> "翡翠绿"
        else -> "渐变"
    }

    /**
     * 玻璃卡片样式：白底 + 极淡色洗 + 细边。
     * 强调色沿用同一套自然色系，但降饱和，避免与白底冲突。
     */
    private val glassStyles = listOf(
        CardStyle(1, "深海蓝", Color(0xFF3D6DB5), Color(0xFFF1F5FC), Color(0xFFD3E2F7),
            Brush.linearGradient(listOf(Color(0xFFF1F5FC), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(2, "苔原绿", Color(0xFF2E8B6C), Color(0xFFF0F9F5), Color(0xFFCFEADE),
            Brush.linearGradient(listOf(Color(0xFFF0F9F5), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(3, "珊瑚橙", Color(0xFFC96A5C), Color(0xFFFDF3F1), Color(0xFFF4DAD5),
            Brush.linearGradient(listOf(Color(0xFFFDF3F1), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(4, "暮光紫", Color(0xFF6D5AC0), Color(0xFFF5F3FD), Color(0xFFE0DAF5),
            Brush.linearGradient(listOf(Color(0xFFF5F3FD), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(5, "琥珀金", Color(0xFFB5832A), Color(0xFFFDF8EE), Color(0xFFF1E3C4),
            Brush.linearGradient(listOf(Color(0xFFFDF8EE), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(6, "玫瑰粉", Color(0xFFB84B77), Color(0xFFFDF3F7), Color(0xFFF4D9E4),
            Brush.linearGradient(listOf(Color(0xFFFDF3F7), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(7, "青碧", Color(0xFF2A8A80), Color(0xFFF0FAF8), Color(0xFFCFEBE7),
            Brush.linearGradient(listOf(Color(0xFFF0FAF8), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(8, "靛蓝", Color(0xFF4A4DB0), Color(0xFFF2F3FD), Color(0xFFD8DAF5),
            Brush.linearGradient(listOf(Color(0xFFF2F3FD), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(9, "石墨灰", Color(0xFF525A68), Color(0xFFF4F5F7), Color(0xFFDDE0E5),
            Brush.linearGradient(listOf(Color(0xFFF4F5F7), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A)),
        CardStyle(10, "翡翠绿", Color(0xFF24895C), Color(0xFFF0FAF4), Color(0xFFCFEAD9),
            Brush.linearGradient(listOf(Color(0xFFF0FAF4), Color.White)),
            textColor = Color(0xFF141416), secondaryText = Color(0xFF82828A))
    )

    private fun stylesFor(uiStyle: UiStyle, forDark: Boolean): List<CardStyle> {
        return when (uiStyle) {
            UiStyle.SoftDiary -> gradientStyles(forDark)
            UiStyle.GlassCountdown -> glassStyles
        }
    }

    fun getCardStyle(
        backgroundId: Int,
        uiStyle: UiStyle = UiStyle.SoftDiary,
        forDark: Boolean = false
    ): CardStyle {
        val styles = stylesFor(uiStyle, forDark)
        return styles.find { it.id == backgroundId } ?: styles[0]
    }

    /**
     * 今日卡片：金橙渐变，全应用唯一的强调色，用于"就是今天"。
     */
    fun getTodayStyle(uiStyle: UiStyle, forDark: Boolean = false): CardStyle {
        return when (uiStyle) {
            UiStyle.SoftDiary -> CardStyle(
                id = 0,
                name = "今天",
                accent = TodayGlow,
                wash = TodayGradientStart.copy(alpha = 0.18f),
                glow = TodayGlow,
                gradientBrush = Brush.linearGradient(
                    listOf(
                        if (forDark) Color(0xFFC98428) else TodayGradientStart,
                        if (forDark) Color(0xFFA35E12) else TodayGradientEnd
                    )
                ),
                textColor = Color.White,
                secondaryText = Color.White.copy(alpha = 0.90f)
            )
            UiStyle.GlassCountdown -> CardStyle(
                id = 0,
                name = "今天",
                accent = Color(0xFFB5832A),
                wash = Color(0xFFFFFBF2),
                glow = Color(0xFFF7E7C2),
                gradientBrush = Brush.linearGradient(listOf(Color(0xFFFFFBF2), Color.White)),
                textColor = Color(0xFF141416),
                secondaryText = Color(0xFF82828A)
            )
        }
    }

    fun getRandomStyleId(): Int = (1..10).random()

    /** 优先选没用过的样式，避免相邻卡片同色 */
    fun getSmartRandomStyleId(usedBackgroundIds: List<Int>): Int {
        val allIds = (1..10).toList()
        val unusedIds = allIds.filter { it !in usedBackgroundIds }
        return if (unusedIds.isNotEmpty()) unusedIds.random() else allIds.random()
    }

    fun getAllStyleIds(): List<Int> = (1..10).toList()

    fun getStyleName(
        backgroundId: Int,
        uiStyle: UiStyle = UiStyle.SoftDiary,
        forDark: Boolean = false
    ): String = getCardStyle(backgroundId, uiStyle, forDark).name
}
