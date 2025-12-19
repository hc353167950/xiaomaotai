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
    modifier: Modifier = Modifier, // 保留但标记为可能未使用
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // 拖拽相关状态
    var isCurrentlyDragging by remember { mutableStateOf(false) }
    // 注释掉未使用的变量
    // var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // val density = LocalDensity.current

    // 计算天数逻辑 - 优先使用缓存的天数
    val (label, days) = event.cachedDays?.let { cachedDays ->
        // 使用缓存的天数，生成对应的label
        val labelText = when {
            cachedDays == 0L -> "就是今天"
            cachedDays == 1L -> "明天"
            cachedDays == -1L -> "昨天"
            cachedDays > 0 -> "还有${cachedDays}天"
            else -> "${kotlin.math.abs(cachedDays)}天前"
        }
        android.util.Log.d("EventItem", "使用缓存天数: ${event.eventName} = $cachedDays 天, label=$labelText")
        labelText to cachedDays
    } ?: run {
        // 没有缓存，重新计算（兼容旧逻辑）
        android.util.Log.w("EventItem", "没有缓存天数，重新计算: ${event.eventName}")
        calculateDaysAfter(event.eventDate)
    }

    // 获取显示用的日期文本
    val dateDisplayText = remember(event.eventDate) {
        when {
            event.eventDate.startsWith("lunar:") -> {
                // 农历事件显示格式：腊月初一 2025 或 闰六月初一 2025
                val lunarDatePart = event.eventDate.removePrefix("lunar:")
                android.util.Log.d("EventItem", "Displaying lunar date: $lunarDatePart")
                
                when {
                    lunarDatePart.contains("-L") -> {
                        // 正确的闰月格式：2025-L06-10
                        val parts = lunarDatePart.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0]
                            val monthPart = parts[1] // L06
                            val day = parts[2].toIntOrNull() ?: 1
                            val actualMonth = monthPart.substring(1).toIntOrNull() ?: 1
                            android.util.Log.d("EventItem", "Correct leap month format: year=$year, month=$actualMonth, day=$day")
                            "${LunarCalendarHelper.getLunarMonthName(actualMonth, true)}${getLunarDayName(day)} $year"
                        } else {
                            event.eventDate.removePrefix("lunar:")
                        }
                    }
                    lunarDatePart.contains("--") -> {
                        // 错误的闰月格式：2025--6-10
                        val corrected = lunarDatePart.replace("--", "-")
                        val parts = corrected.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0]
                            val month = parts[1].toIntOrNull() ?: 1
                            val day = parts[2].toIntOrNull() ?: 1
                            android.util.Log.d("EventItem", "Incorrect leap month format corrected: year=$year, month=$month, day=$day")
                            "${LunarCalendarHelper.getLunarMonthName(month, true)}${getLunarDayName(day)} $year"
                        } else {
                            event.eventDate.removePrefix("lunar:")
                        }
                    }
                    else -> {
                        // 正常农历格式：2025-08-10
                        val parts = lunarDatePart.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0]
                            val month = parts[1].toIntOrNull() ?: 1
                            val day = parts[2].toIntOrNull() ?: 1
                            "${LunarCalendarHelper.getLunarMonthName(month, false)}${getLunarDayName(day)} $year"
                        } else {
                            event.eventDate.removePrefix("lunar:")
                        }
                    }
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
                                                onDragStart = { _ ->
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

        // 对于农历事件，使用专门的倒计时计算函数
        val daysBetween = when {
            isLunarEvent -> {
                val lunarDatePart = eventDate.removePrefix("lunar:")
                android.util.Log.d("EventItem", "Processing lunar date: $lunarDatePart")
                
                when {
                    lunarDatePart.contains("-L") -> {
                        // 正确的闰月格式：2025-L06-10
                        val parts = lunarDatePart.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0].toIntOrNull() ?: java.time.LocalDate.now().year
                            val monthPart = parts[1] // L06
                            val lunarDay = parts[2].toIntOrNull() ?: 1
                            val actualMonth = monthPart.substring(1).toIntOrNull() ?: 1

                            android.util.Log.d("EventItem", "闰月倒计时计算: year=$year, month=$actualMonth, day=$lunarDay, isLeap=true")
                            val result = LunarCalendarHelper.calculateLunarCountdown(
                                year, actualMonth, lunarDay, true, today
                            )
                            android.util.Log.d("EventItem", "闰月倒计时结果: $result 天")
                            result
                        } else {
                            1L
                        }
                    }
                    lunarDatePart.contains("--") -> {
                        // 错误的闰月格式：2025--6-10
                        val corrected = lunarDatePart.replace("--", "-")
                        val parts = corrected.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0].toIntOrNull() ?: java.time.LocalDate.now().year
                            val lunarMonth = parts[1].toIntOrNull() ?: 1
                            val lunarDay = parts[2].toIntOrNull() ?: 1

                            LunarCalendarHelper.calculateLunarCountdown(
                                year, lunarMonth, lunarDay, true, today
                            )
                        } else {
                            1L
                        }
                    }
                    else -> {
                        // 正常农历格式：2025-08-10
                        val parts = lunarDatePart.split("-")
                        if (parts.size >= 3) {
                            val year = parts[0].toIntOrNull() ?: java.time.LocalDate.now().year
                            val lunarMonth = parts[1].toIntOrNull() ?: 1
                            val lunarDay = parts[2].toIntOrNull() ?: 1

                            android.util.Log.d("EventItem", "普通农历倒计时计算: year=$year, month=$lunarMonth, day=$lunarDay, isLeap=false")
                            val result = LunarCalendarHelper.calculateLunarCountdown(
                                year, lunarMonth, lunarDay, false, today
                            )
                            android.util.Log.d("EventItem", "普通农历倒计时结果: $result 天")
                            result
                        } else {
                            1L
                        }
                    }
                }
            }
            isMonthDayFormat -> {
                val parts = eventDate.split("-")
                if (parts.size >= 2) {
                    val month = parts[0].toInt()
                    val day = parts[1].toInt()
                    // 处理2月29日边界情况：在非闰年使用2月28日
                    val safeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(today.year.toLong())) 28 else day
                    var target = LocalDate.of(today.year, month, safeDay)
                    // 如果今年的日期已过，使用明年的日期
                    if (target.isBefore(today)) {
                        val nextYear = today.year + 1
                        val nextYearSafeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(nextYear.toLong())) 28 else day
                        target = LocalDate.of(nextYear, month, nextYearSafeDay)
                    }
                    ChronoUnit.DAYS.between(today, target)
                } else {
                    1L // 默认明天
                }
            }
            else -> {
                // 标准格式 yyyy-MM-dd，视为每年重复的纪念日
                val parts = eventDate.split("-")
                if (parts.size >= 3) {
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    // 处理2月29日边界情况：在非闰年使用2月28日
                    val safeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(today.year.toLong())) 28 else day
                    var target = LocalDate.of(today.year, month, safeDay)
                    // 如果今年的日期已过，使用明年的日期
                    if (target.isBefore(today)) {
                        val nextYear = today.year + 1
                        val nextYearSafeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(nextYear.toLong())) 28 else day
                        target = LocalDate.of(nextYear, month, nextYearSafeDay)
                    }
                    ChronoUnit.DAYS.between(today, target)
                } else {
                    1L // 默认明天
                }
            }
        }
        
        android.util.Log.d("EventItem", "Calculated days between: $daysBetween for event: $eventDate")
        
        when {
            daysBetween == 0L -> "就是今天" to 0L
            daysBetween == 1L -> "明天" to 1L
            daysBetween == -1L -> "昨天" to -1L
            daysBetween > 0 -> {
                // 直接显示具体天数，不显示“年后”
                "还有${daysBetween}天" to daysBetween
            }
            else -> {
                val absDays = kotlin.math.abs(daysBetween)
                // 直接显示具体天数，不显示“年前”
                "${absDays}天前" to daysBetween
            }
        }
    } catch (e: Exception) {
        Log.e("EventItem", "日期计算错误: $eventDate", e)
        "日期无效" to 0L
    }
}

// 农历日期名称转换 - 使用LunarCalendarHelper统一的方法
private fun getLunarDayName(day: Int): String {
    return com.example.xiaomaotai.LunarCalendarHelper.getLunarDayName(day)
}
