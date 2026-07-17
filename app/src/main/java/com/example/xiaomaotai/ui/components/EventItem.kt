package com.example.xiaomaotai.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DateParser
import com.example.xiaomaotai.Event
import com.example.xiaomaotai.LunarCalendarHelper
import com.example.xiaomaotai.ui.theme.LocalUiStyle
import com.example.xiaomaotai.ui.theme.UiStyle
import java.time.LocalDate

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

    val (label, days) = event.cachedDays?.let { cachedDays ->
        val labelText = when {
            cachedDays == 0L -> "就是今天"
            cachedDays == 1L -> "明天"
            cachedDays == -1L -> "昨天"
            cachedDays > 0 -> "还有${cachedDays}天"
            else -> "${kotlin.math.abs(cachedDays)}天前"
        }
        labelText to cachedDays
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
                "$monthName$dayName ${parsedDate.year}"
            }
            parsedDate.type == DateParser.DateType.LUNAR_MONTH_DAY -> {
                val monthName = LunarCalendarHelper.getLunarMonthName(parsedDate.month, parsedDate.isLeapMonth)
                val dayName = LunarCalendarHelper.getLunarDayName(parsedDate.day)
                "$monthName$dayName"
            }
            parsedDate.type == DateParser.DateType.MONTH_DAY -> event.eventDate
            else -> {
                val monthStr = parsedDate.month.toString().padStart(2, '0')
                val dayStr = parsedDate.day.toString().padStart(2, '0')
                "$monthStr-$dayStr · ${parsedDate.year}"
            }
        }
    }

    val cardStyle = remember(event.backgroundId, days, uiStyle) {
        if (days == 0L) CardStyleManager.getTodayStyle(uiStyle)
        else CardStyleManager.getCardStyle(event.backgroundId, uiStyle)
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
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("确认删除", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "确定要删除纪念日「${event.eventName}」吗？",
                    fontSize = 15.sp,
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

/** A · 最初方案：满幅彩色渐变 + 白字 */
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
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .scale(if (isCurrentlyDragging) 1.03f else 1f)
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentlyDragging) 10.dp else 0.dp
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = cardStyle.gradientBrush, shape = shape)
                .clip(shape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.06f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.eventName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cardStyle.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val parts = dateDisplayText.split(" · ")
                        Text(
                            text = if (parts.size == 2) "${parts[0]} ${parts[1]}" else dateDisplayText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = cardStyle.secondaryText,
                            letterSpacing = (-0.08).sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DaysBlock(label, days, cardStyle, gradient = true)
                        if (isDragMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DragHandle(
                                tint = cardStyle.textColor.copy(alpha = 0.75f),
                                isDragMode = isDragMode,
                                dragIndex = dragIndex,
                                onDraggingChange = onDraggingChange,
                                onDragStart = onDragStart,
                                onDragEnd = onDragEnd,
                                onDrag = onDrag
                            )
                        }
                    }
                }
                if (!isDragMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = onEdit,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            contentColor = Color.White
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                "编辑",
                                modifier = Modifier.padding(8.dp).size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = onDeleteRequest,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.18f),
                            contentColor = Color.White
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                modifier = Modifier.padding(8.dp).size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** B · 玻璃：白卡 + 细边 + 极淡色洗 */
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
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .scale(if (isCurrentlyDragging) 1.015f else 1f)
            .then(modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE8EAEE), shape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(listOf(cardStyle.wash, Color.White))
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(96.dp)
                    .offset(x = 36.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(cardStyle.glow.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cardStyle.accent.copy(alpha = 0.75f))
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = event.eventName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cardStyle.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val parts = dateDisplayText.split(" · ")
                    Text(
                        text = if (parts.size == 2) "${parts[0]} ${parts[1]}" else dateDisplayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cardStyle.secondaryText
                    )
                    if (!isDragMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = onEdit,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF2F3F6),
                                contentColor = Color(0xFF5C5C60)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    "编辑",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp)
                                )
                            }
                            Surface(
                                onClick = onDeleteRequest,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFDF2F2),
                                contentColor = Color(0xFFD0605E)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "删除",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp)
                                )
                            }
                        }
                    }
                }
                DaysBlock(label, days, cardStyle, gradient = false)
                if (isDragMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DragHandle(
                        tint = cardStyle.secondaryText,
                        isDragMode = isDragMode,
                        dragIndex = dragIndex,
                        onDraggingChange = onDraggingChange,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onDrag = onDrag
                    )
                }
            }
        }
    }
}

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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardStyle.textColor
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardStyle.wash,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        cardStyle.accent.copy(alpha = 0.22f)
                    )
                ) {
                    Text(
                        text = "今天",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                fontSize = if (gradient) 18.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (gradient) cardStyle.textColor else cardStyle.accent
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
                    fontSize = if (gradient) 28.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gradient) cardStyle.textColor else cardStyle.textColor,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = if (label.startsWith("还有")) "天后" else "天前",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = cardStyle.secondaryText
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
    Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = "拖拽",
        tint = tint,
        modifier = Modifier
            .size(22.dp)
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

fun calculateDaysAfter(eventDate: String): Pair<String, Long> {
    return try {
        val today = LocalDate.now()
        val parsedDate = DateParser.parse(eventDate)
        if (parsedDate == null) {
            Log.e("EventItem", "无法解析日期: $eventDate")
            return "日期无效" to 0L
        }
        val daysBetween = when (parsedDate.type) {
            DateParser.DateType.LUNAR -> DateParser.calculateLunarDaysUntil(parsedDate, today)
            DateParser.DateType.LUNAR_MONTH_DAY -> DateParser.calculateLunarMonthDayDaysUntil(parsedDate, today)
            DateParser.DateType.MONTH_DAY, DateParser.DateType.SOLAR ->
                DateParser.calculateSolarDaysUntil(parsedDate, today)
        }
        when {
            daysBetween == 0L -> "就是今天" to 0L
            daysBetween == 1L -> "明天" to 1L
            daysBetween == -1L -> "昨天" to -1L
            daysBetween > 0 -> "还有${daysBetween}天" to daysBetween
            else -> "${kotlin.math.abs(daysBetween)}天前" to daysBetween
        }
    } catch (e: Exception) {
        Log.e("EventItem", "日期计算错误: $eventDate", e)
        "日期无效" to 0L
    }
}
