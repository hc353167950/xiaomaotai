package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.ui.theme.GradientPalette
import com.example.xiaomaotai.ui.theme.LocalDarkTheme
import com.example.xiaomaotai.ui.theme.Space
import com.example.xiaomaotai.ui.theme.UiStyle

/**
 * 界面风格二级页。
 *
 * 每个选项都是一个真实缩略预览（渐变卡 or 白卡），
 * 而不是干巴巴的两个文字按钮——选之前就能看到效果。
 */
@Composable
fun UiStyleScreen(
    uiStyleId: String,
    onUiStyleChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val current = UiStyle.fromId(uiStyleId)

    SettingsSubPage(title = "界面风格", onBack = onBack) {
        SettingsGroupLabel("选择风格")
        SettingsCard {
            UiStyle.entries.forEachIndexed { index, style ->
                StyleOptionRow(
                    style = style,
                    selected = current == style,
                    onSelect = { onUiStyleChange(style.id) }
                )
                if (index != UiStyle.entries.lastIndex) SettingsDivider()
            }
        }

        SettingsGroupLabel("说明")
        SettingsCard {
            Column(modifier = Modifier.padding(Space.lg)) {
                Text(
                    text = current.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Space.xs))
                Text(
                    text = current.subtitle,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 风格选项：左缩略预览 + 右标题说明 + 选中勾 */
@Composable
private fun StyleOptionRow(
    style: UiStyle,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleThumbnail(style)
        Spacer(modifier = Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Space.xxs))
            Text(
                text = style.subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(Space.sm))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Space.iconMd)
            )
        }
    }
}

/**
 * 缩略预览：60×44 的小卡，模拟真实卡片的样子。
 * 渐变风格取调色板第一组，白卡风格画白底 + 左色条。
 */
@Composable
private fun StyleThumbnail(style: UiStyle) {
    val thumbShape = RoundedCornerShape(8.dp)
    val isDark = com.example.xiaomaotai.ui.theme.LocalDarkTheme.current

    when (style) {
        UiStyle.SoftDiary -> {
            val pair = GradientPalette.first()
            val start = if (isDark) pair.darkStart else pair.start
            val end = if (isDark) pair.darkEnd else pair.end
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 42.dp)
                    .clip(thumbShape)
                    .background(Brush.linearGradient(listOf(start, end)))
            ) {
                // 顶部高光，和真实渐变卡一致
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 右侧天数占位
                Text(
                    text = "12",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Space.sm),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Text(
                    text = "生日",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Space.sm),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        UiStyle.GlassCountdown -> {
            val accentPair = GradientPalette.first()
            val thumbShapeLight = RoundedCornerShape(8.dp)
            val bgColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
            val lineColor = if (isDark) MaterialTheme.colorScheme.outline else Color(0xFFE3E7EE)
            val textColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1A1A1C)
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 42.dp)
                    .clip(thumbShapeLight)
                    .background(bgColor)
                    .border(1.dp, lineColor, thumbShapeLight)
            ) {
                // 左侧色条
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = Space.sm)
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentPair.start)
                )
                Text(
                    text = "生日",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Space.sm + Space.xs),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Text(
                    text = "12",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Space.sm),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}
