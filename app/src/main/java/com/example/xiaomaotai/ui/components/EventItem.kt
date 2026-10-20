package com.example.xiaomaotai.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.Countdown
import com.example.xiaomaotai.DateParser
import com.example.xiaomaotai.Event
import com.example.xiaomaotai.LunarCalendarHelper
import com.example.xiaomaotai.ui.theme.LocalDarkTheme
import com.example.xiaomaotai.ui.theme.LocalUiStyle
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Sizes
import com.example.xiaomaotai.ui.theme.Space
import com.example.xiaomaotai.ui.theme.UiStyle

@Composable
fun EventItem(
    event: Event,
    isDragMode: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDragMove: ((Int, Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    dragIndex: Int = -1,
    modifier: Modifier = Modifier,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isCurrentlyDragging by remember { mutableStateOf(false) }
    val uiStyle = LocalUiStyle.current
    val isGlass = uiStyle == UiStyle.GlassCountdown
    val isDark = LocalDarkTheme.current

    val (label, days) = event.cachedDays?.let { cachedDays ->
        Countdown.labelFor(cachedDays) to cachedDays
    } ?: run {
        calculateDaysAfter(event.eventDate)
    }

    val dateDisplayText = remember(event.eventDate) {
        val parsedDate = DateParser.parse(event.eventDate)
        when {
            parsedDate == null -> event.eventDate
            parsedDate.type == DateParser.DateType.LUNAR -> {
                val monthName = LunarCalendarHelper.getLunarMonthName(parsedDate.month, parsedDate.isLeapMonth)
                val dayName = LunarCalendarHelper.getLunarDayName(parsedDate.day)
                "$monthName$dayName · ${parsedDate.year}"
            }
            parsedDate.type == DateParser.DateType.LUNAR_MONTH_DAY -> {
                val monthName = LunarCalendarHelper.getLunarMonthName(parsedDate.month, parsedDate.isLeapMonth)
                val dayName = LunarCalendarHelper.getLunarDayName(parsedDate.day)
                monthName + dayName
            }
            parsedDate.type == DateParser.DateType.MONTH_DAY -> {
                val monthStr = parsedDate.month.toString().padStart(2, '0')
                val dayStr = parsedDate.day.toString().padStart(2, '0')
                "$monthStr-$dayStr · 每年"
            }
            else -> {
                val monthStr = parsedDate.month.toString().padStart(2, '0')
                val dayStr = parsedDate.day.toString().padStart(2, '0')
                "$monthStr-$dayStr · ${parsedDate.year}"
            }
        }
    }

    val cardStyle = remember(event.backgroundId, days, uiStyle, isDark) {
        if (days == 0L) CardStyleManager.getTodayStyle(uiStyle, isDark)
        else CardStyleManager.getCardStyle(event.backgroundId, uiStyle, isDark)
    }

    if (isGlass) {
        GlassEventCard(
            event = event,
            cardStyle = cardStyle,
            label = label,
            days = days,
            dateDisplayText = dateDisplayText,
            isDragMode = isDragMode,
            isCurrentlyDragging = isCurrentlyDragging,
            onDraggingChange = { isCurrentlyDragging = it },
            onEdit = onEdit,
            onDeleteRequest = { showDeleteConfirm = true },
            dragIndex = dragIndex,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            onDrag = onDrag,
            modifier = modifier
        )
    } else {
        GradientEventCard(
            event = event,
            cardStyle = cardStyle,
            label = label,
            days = days,
            dateDisplayText = dateDisplayText,
            isDragMode = isDragMode,
            isCurrentlyDragging = isCurrentlyDragging,
            onDraggingChange = { isCurrentlyDragging = it },
            onEdit = onEdit,
            onDeleteRequest = { showDeleteConfirm = true },
            dragIndex = dragIndex,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            onDrag = onDrag,
            modifier = modifier
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = Shape.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("删除纪念日", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "确定要删除「${event.eventName}」吗？此操作无法撤销。",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

/**
 * A · 渐变卡片。
 *
 * 视觉层次（从后到前）：
 * 1. 主渐变底
 * 2. 右上角柔光（radialGradient，营造体积感）
 * 3. 顶部高光条（模拟光照）
 * 4. 内容层
 */
@Composable
private fun GradientEventCard(
    event: Event,
    cardStyle: CardStyleManager.CardStyle,
    label: String,
    days: Long,
    dateDisplayText: String,
    isDragMode: Boolean,
    isCurrentlyDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    dragIndex: Int,
    onDragStart: (() -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDrag: ((Offset) -> Unit)?,
    modifier: Modifier
) {
    val shape = Shape.medium
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.cardMinHeight)
            .scale(if (isCurrentlyDragging) 1.03f else 1f)
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentlyDragging) 12.dp else 2.dp
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = cardStyle.gradientBrush, shape = shape)
                .clip(shape)
        ) {
            // 右上角柔光：让纯渐变有体积感
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(120.dp)
                    .offset(x = 36.dp, y = (-36).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // 左下角暗角：加深层次
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(100.dp)
                    .offset(x = (-26).dp, y = 26.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.13f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // 顶部高光
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 单行布局：左侧名称+日期，右侧天数+操作按钮同一水平线
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs)
                ) {
                    Text(
                        text = event.eventName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cardStyle.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = dateDisplayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cardStyle.secondaryText,
                        letterSpacing = 0.1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(Space.sm))

                DaysBlock(label, days, cardStyle, gradient = true)

                if (isDragMode) {
                    Spacer(modifier = Modifier.width(Space.xs))
                    DragHandle(
                        tint = cardStyle.textColor.copy(alpha = 0.75f),
                        isDragMode = isDragMode,
                        dragIndex = dragIndex,
                        onDraggingChange = onDraggingChange,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onDrag = onDrag
                    )
                } else {
                    Spacer(modifier = Modifier.width(Space.xs))
                    // 操作按钮竖排在右侧，避免额外占一行高度
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        GlassActionButton(
                            icon = Icons.Default.Edit,
                            description = "编辑",
                            onClick = onEdit
                        )
                        GlassActionButton(
                            icon = Icons.Default.Delete,
                            description = "删除",
                            onClick = onDeleteRequest
                        )
                    }
                }
            }
        }
    }
}

/**
 * B · 玻璃卡片：白底 + 顶部色洗 + 左侧色条 + 右侧光晕。
 */
@Composable
private fun GlassEventCard(
    event: Event,
    cardStyle: CardStyleManager.CardStyle,
    label: String,
    days: Long,
    dateDisplayText: String,
    isDragMode: Boolean,
    isCurrentlyDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    dragIndex: Int,
    onDragStart: (() -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDrag: ((Offset) -> Unit)?,
    modifier: Modifier
) {
    val shape = Shape.medium
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val hairline = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE8ECF2)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.cardMinHeight)
            .scale(if (isCurrentlyDragging) 1.015f else 1f)
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentlyDragging) 10.dp else 2.dp
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardBg)
                .border(1.dp, hairline, shape)
        ) {
            // 1 · 顶部极淡色洗：只用 accent 的 6% 不透明度，克制但有冷暖倾向
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(cardStyle.accent.copy(alpha = 0.07f), Color.Transparent)
                        )
                    )
            )

            // 2 · 右侧柔光：accent 光晕，只在右边缘流转
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(120.dp)
                    .offset(x = 46.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(cardStyle.accent.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
            )

            // 3 · 左侧 3dp 渐隐色条：上浓下淡，比整条实色更轻
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = Space.md)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(cardStyle.accent, cardStyle.accent.copy(alpha = 0.25f))
                        )
                    )
            )

            // 4 · 内容：单行对齐，右侧天数 + 竖向操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.md, top = Space.md, bottom = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs)
                ) {
                    Text(
                        text = event.eventName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cardStyle.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = dateDisplayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cardStyle.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DaysBlock(label, days, cardStyle, gradient = false)

                if (isDragMode) {
                    Spacer(modifier = Modifier.width(Space.xs))
                    DragHandle(
                        tint = cardStyle.secondaryText,
                        isDragMode = isDragMode,
                        dragIndex = dragIndex,
                        onDraggingChange = onDraggingChange,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onDrag = onDrag
                    )
                } else {
                    Spacer(modifier = Modifier.width(Space.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        SoftActionButton(
                            icon = Icons.Default.Edit,
                            description = "编辑",
                            container = cardStyle.accent.copy(
                                alpha = if (isDark) 0.16f else 0.09f
                            ),
                            contentColor = cardStyle.accent,
                            onClick = onEdit
                        )
                        SoftActionButton(
                            icon = Icons.Default.Delete,
                            description = "删除",
                            container = MaterialTheme.colorScheme.error.copy(
                                alpha = if (isDark) 0.16f else 0.08f
                            ),
                            contentColor = MaterialTheme.colorScheme.error,
                            onClick = onDeleteRequest
                        )
                    }
                }
            }
        }
    }
}

/** 渐变卡上的半透明操作按钮 */
@Composable
private fun GlassActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(Shape.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = Shape.tiny,
            color = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier
                    .padding(horizontal = Space.sm, vertical = Space.xs)
                    .size(14.dp)
            )
        }
    }
}

/** 白卡上的浅底操作按钮 */
@Composable
private fun SoftActionButton(
    icon: ImageVector,
    description: String,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(Shape.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = Shape.tiny, color = container, contentColor = contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier
                    .padding(horizontal = Space.sm, vertical = Space.xs)
                    .size(14.dp)
            )
        }
    }
}

/**
 * 右侧倒计时块。
 *
 * 大数字用负字距收紧，视觉更精致；单位小字压低对比。
 */
@Composable
private fun DaysBlock(
    label: String,
    days: Long,
    cardStyle: CardStyleManager.CardStyle,
    gradient: Boolean
) {
    if (label == "日期无效") return
    when {
        label == "就是今天" -> {
            if (gradient) {
                Text(
                    text = "今天",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardStyle.textColor,
                    letterSpacing = (-0.6).sp
                )
            } else {
                Surface(
                    shape = Shape.small,
                    color = cardStyle.wash,
                    border = BorderStroke(1.dp, cardStyle.accent.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "今天",
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardStyle.accent
                    )
                }
            }
        }
        label.contains("明天") || label.contains("昨天") -> {
            Text(
                text = label,
                fontSize = if (gradient) 19.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (gradient) cardStyle.textColor else cardStyle.accent,
                letterSpacing = (-0.3).sp
            )
        }
        label.contains("年") -> {
            Text(
                text = label,
                fontSize = if (gradient) 14.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = cardStyle.textColor,
                textAlign = TextAlign.End
            )
        }
        else -> {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = kotlin.math.abs(days).toString(),
                    fontSize = if (gradient) 30.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardStyle.textColor,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = if (label.startsWith("还有")) "天后" else "天前",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = cardStyle.secondaryText,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun DragHandle(
    tint: Color,
    isDragMode: Boolean,
    dragIndex: Int,
    onDraggingChange: (Boolean) -> Unit,
    onDragStart: (() -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDrag: ((Offset) -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "拖拽",
            tint = tint,
            modifier = Modifier
                .size(Space.iconMd)
                .pointerInput(isDragMode, dragIndex) {
                    if (isDragMode) {
                        detectDragGestures(
                            onDragStart = {
                                onDraggingChange(true)
                                onDragStart?.invoke()
                            },
                            onDragEnd = {
                                onDraggingChange(false)
                                onDragEnd?.invoke()
                            },
                            onDragCancel = {
                                onDraggingChange(false)
                                onDragEnd?.invoke()
                            },
                            onDrag = { change, _ ->
                                onDrag?.invoke(Offset(0f, change.position.y))
                            }
                        )
                    }
                }
        )
    }
}

/**
 * 倒计时统一走 [Countdown] 深模块，此函数仅保留旧签名作为 UI 层入口。
 */
fun calculateDaysAfter(eventDate: String): Pair<String, Long> {
    val countdown = Countdown.of(eventDate)
    if (countdown == null) {
        Log.e("EventItem", "无法解析日期: $eventDate")
        return "日期无效" to 0L
    }
    return countdown.label to countdown.days
}
