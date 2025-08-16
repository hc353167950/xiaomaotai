package com.example.xiaomaotai.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

object CardStyleManager {
    
    data class CardStyle(
        val id: Int,
        val name: String,
        val gradientBrush: Brush,
        val textColor: Color = Color.White // 渐变背景使用白色文字
    )
    
    private val cardStyles = listOf(
        CardStyle(1, "紫蓝渐变", Brush.horizontalGradient(listOf(Color(0xFF6A5ACD), Color(0xFF4169E1)))),
        CardStyle(2, "绿青渐变", Brush.horizontalGradient(listOf(Color(0xFF32CD32), Color(0xFF20B2AA)))),
        CardStyle(3, "粉红渐变", Brush.horizontalGradient(listOf(Color(0xFFFF69B4), Color(0xFFFF1493)))),
        CardStyle(4, "蓝紫渐变", Brush.horizontalGradient(listOf(Color(0xFF4169E1), Color(0xFF8A2BE2)))),
        CardStyle(5, "橙红渐变", Brush.horizontalGradient(listOf(Color(0xFFFF6347), Color(0xFFFF4500)))),
        CardStyle(6, "青蓝渐变", Brush.horizontalGradient(listOf(Color(0xFF00CED1), Color(0xFF1E90FF)))),
        CardStyle(7, "紫粉渐变", Brush.horizontalGradient(listOf(Color(0xFF9370DB), Color(0xFFDA70D6)))),
        CardStyle(8, "绿蓝渐变", Brush.horizontalGradient(listOf(Color(0xFF00FA9A), Color(0xFF00BFFF)))),
        CardStyle(9, "金橙渐变", Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00)))),
        CardStyle(10, "深蓝渐变", Brush.horizontalGradient(listOf(Color(0xFF191970), Color(0xFF4682B4))))
    )
    
    /**
     * 获取指定ID的卡片样式
     */
    fun getCardStyle(backgroundId: Int): CardStyle {
        return cardStyles.find { it.id == backgroundId } ?: cardStyles[0]
    }
    
    /**
     * 获取随机卡片样式ID（用于新建事件）
     */
    fun getRandomStyleId(): Int {
        return cardStyles.random().id
    }
    
    /**
     * 获取所有可用的样式ID列表
     */
    fun getAllStyleIds(): List<Int> {
        return cardStyles.map { it.id }
    }
    
    /**
     * 获取样式名称
     */
    fun getStyleName(backgroundId: Int): String {
        return getCardStyle(backgroundId).name
    }
}
