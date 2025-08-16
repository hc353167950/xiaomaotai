package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.xiaomaotai.Event
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import android.util.Log
import com.example.xiaomaotai.LunarCalendarHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

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
    
    // 拖拽相关状态
    var isCurrentlyDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    
    // 计算天数逻辑 - 只显示多少天之后
    val (label, days) = remember(event.eventDate) { 
        calculateDaysAfter(event.eventDate)
    }
    
    // 获取显示用的日期文本
    val dateDisplayText = remember(event.eventDate) {
        when {
            event.eventDate.startsWith("lunar:") -> {
                // 农历事件显示格式：腊月初一 2025
                val lunarDatePart = event.eventDate.removePrefix("lunar:")
                val parts = lunarDatePart.split("-")
                if (parts.size >= 3) {
                    val year = parts[0]
                    val month = parts[1].toIntOrNull() ?: 1
                    val day = parts[2].toIntOrNull() ?: 1
                    "${getLunarMonthName(month)}${getLunarDayName(day)} $year"
                } else {
                    event.eventDate.removePrefix("lunar:")
                }
            }
            event.eventDate.matches(Regex("\\d{2}-\\d{2}")) -> {
                // 忽略年份事件显示格式：08-10 · 每年
                "${event.eventDate}"
            }
            else -> {
                // 公历事件显示格式：08-10 · 2025
                val parts = event.eventDate.split("-")
                if (parts.size >= 3) {
                    "${parts[1]}-${parts[2]} · ${parts[0]}"
                } else {
                    event.eventDate
                }
            }
        }
    }

    // 预设的4种渐变背景
    // 优化后的卡片样式系统 - 使用渐变背景
    val cardStyle = remember(event.backgroundId, days) {
        if (days == 0L) {
            // 只有今天的事件使用特殊的金色高亮样式
            CardStyleManager.CardStyle(
                id = 0,
                name = "今日",
                gradientBrush = Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))),
                textColor = Color.White
            )
        } else {
            // 其他所有日期（包括过期和未来）都使用事件创建时的原始样式
            CardStyleManager.getCardStyle(event.backgroundId)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .scale(if (isCurrentlyDragging) 1.05f else 1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = cardStyle.gradientBrush,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section: Event Info and Days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left: Event Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.eventName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardStyle.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 单行显示，年份用小字体内联显示
                        val parts = dateDisplayText.split(" · ")
                        if (parts.size == 2) {
                            Text(
                                text = "${parts[0]} ${parts[1]}", // 月日 + 年份在同一行
                                fontSize = 13.sp,
                                color = cardStyle.textColor.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = dateDisplayText,
                                fontSize = 13.sp,
                                color = cardStyle.textColor.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Right: Days count and drag handle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                        if (label != "日期无效") {
                            if (label == "就是今天") {
                                Text(
                                    text = "今天",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardStyle.textColor
                                )
                            } else if (label.contains("明天") || label.contains("昨天")) {
                                // 显示明天/昨天
                                Text(
                                    text = label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardStyle.textColor
                                )
                            } else if (label.contains("年")) {
                                // 显示年份格式的文本
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardStyle.textColor,
                                    textAlign = TextAlign.End
                                )
                            } else {
                                // 显示天数
                                Text(
                                    text = days.toString(),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cardStyle.textColor
                                )
                                Text(
                                    text = if (label.startsWith("还有")) "天后" else "天前",
                                    fontSize = 11.sp,
                                    color = cardStyle.textColor.copy(alpha = 0.8f),
                                )
                            }
                        }
                        }
                        
                        // 拖拽图标 - 只在拖拽模式下显示
                        if (isDragMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "拖拽",
                                tint = cardStyle.textColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerInput(isDragMode, dragIndex) {
                                        if (isDragMode) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    isCurrentlyDragging = true
                                                    onDragStart?.invoke()
                                                },
                                                onDragEnd = {
                                                    isCurrentlyDragging = false
                                                    onDragEnd?.invoke()
                                                },
                                                onDragCancel = {
                                                    isCurrentlyDragging = false
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
                }
                
                // Bottom section: Action buttons - 在拖拽模式下隐藏编辑和删除按钮
                if (!isDragMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit button
                        Button(
                            onClick = onEdit,
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "编辑",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Delete button
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.3f),
                                contentColor = Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "删除",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框 (unchanged)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除纪念日\"${event.eventName}\"吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// 仅计算"从今天起还有多少天到下一个纪念日"，不显示已过天数
fun calculateDaysAfter(eventDate: String): Pair<String, Long> {
    return try {
        val today = LocalDate.now()
        val isLunarEvent = eventDate.startsWith("lunar:")
        val isMonthDayFormat = eventDate.matches(Regex("\\d{2}-\\d{2}"))

        val targetDate = when {
            isLunarEvent -> {
                val lunarDatePart = eventDate.removePrefix("lunar:")
                val parts = lunarDatePart.split("-")
                if (parts.size >= 3) {
                    val lunarMonth = parts[1].toInt()
                    val lunarDay = parts[2].toInt()
                    // 先尝试当年对应的阳历日期
                    var target: LocalDate = try {
                        val lunarResult = LunarCalendarHelper.lunarToSolar(today.year, lunarMonth, lunarDay)
                        LocalDate.parse(lunarResult)
                    } catch (e: Exception) {
                        Log.w("EventItem", "农历转换失败: $eventDate", e)
                        today.plusDays(1) // 默认明天
                    }
                    
                    // 如果当年的日期已过，计算明年的日期
                    if (target.isBefore(today) || target.isEqual(today.minusDays(1))) {
                        target = try {
                            val nextYearResult = LunarCalendarHelper.lunarToSolar(today.year + 1, lunarMonth, lunarDay)
                            LocalDate.parse(nextYearResult)
                        } catch (e: Exception) {
                            Log.w("EventItem", "明年农历转换失败: $eventDate", e)
                            target.plusYears(1)
                        }
                    }
                    target
                } else {
                    today.plusDays(1) // 默认明天
                }
            }
            isMonthDayFormat -> {
                val parts = eventDate.split("-")
                if (parts.size >= 2) {
                    val month = parts[0].toInt()
                    val day = parts[1].toInt()
                    var target = LocalDate.of(today.year, month, day)
                    // 如果今年的日期已过，使用明年的日期
                    if (target.isBefore(today)) {
                        target = target.plusYears(1)
                    }
                    target
                } else {
                    today.plusDays(1) // 默认明天
                }
            }
            else -> {
                // 标准格式 yyyy-MM-dd，视为每年重复的纪念日
                val parts = eventDate.split("-")
                if (parts.size >= 3) {
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    var target = LocalDate.of(today.year, month, day)
                    // 如果今年的日期已过，使用明年的日期
                    if (target.isBefore(today)) {
                        target = target.plusYears(1)
                    }
                    target
                } else {
                    today.plusDays(1) // 默认明天
                }
            }
        }

        val daysBetween = ChronoUnit.DAYS.between(today, targetDate)
        
        when {
            daysBetween == 0L -> "就是今天" to 0L
            daysBetween == 1L -> "明天" to 1L
            daysBetween == -1L -> "昨天" to -1L
            daysBetween > 0 -> {
                if (daysBetween >= 365) {
                    val years = daysBetween / 365
                    val remainingDays = daysBetween % 365
                    if (remainingDays == 0L) {
                        "${years}年后" to daysBetween
                    } else {
                        "${years}年${remainingDays}天后" to daysBetween
                    }
                } else {
                    "还有${daysBetween}天" to daysBetween
                }
            }
            else -> {
                val absDays = kotlin.math.abs(daysBetween)
                if (absDays >= 365) {
                    val years = absDays / 365
                    val remainingDays = absDays % 365
                    if (remainingDays == 0L) {
                        "${years}年前" to daysBetween
                    } else {
                        "${years}年${remainingDays}天前" to daysBetween
                    }
                } else {
                    "${absDays}天前" to daysBetween
                }
            }
        }
    } catch (e: Exception) {
        Log.e("EventItem", "日期计算错误: $eventDate", e)
        "日期无效" to 0L
    }
}

// 农历月份名称转换
private fun getLunarMonthName(month: Int): String {
    return when (month) {
        1 -> "正月"
        2 -> "二月"
        3 -> "三月"
        4 -> "四月"
        5 -> "五月"
        6 -> "六月"
        7 -> "七月"
        8 -> "八月"
        9 -> "九月"
        10 -> "十月"
        11 -> "冬月"
        12 -> "腊月"
        else -> "${month}月"
    }
}

// 农历日期名称转换
private fun getLunarDayName(day: Int): String {
    return when (day) {
        1 -> "初一"
        2 -> "初二"
        3 -> "初三"
        4 -> "初四"
        5 -> "初五"
        6 -> "初六"
        7 -> "初七"
        8 -> "初八"
        9 -> "初九"
        10 -> "初十"
        11 -> "十一"
        12 -> "十二"
        13 -> "十三"
        14 -> "十四"
        15 -> "十五"
        16 -> "十六"
        17 -> "十七"
        18 -> "十八"
        19 -> "十九"
        20 -> "二十"
        21 -> "廿一"
        22 -> "廿二"
        23 -> "廿三"
        24 -> "廿四"
        25 -> "廿五"
        26 -> "廿六"
        27 -> "廿七"
        28 -> "廿八"
        29 -> "廿九"
        30 -> "三十"
        else -> "${day}日"
    }
}
