package com.example.xiaomaotai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.EventStore
import com.example.xiaomaotai.DateParser
import com.example.xiaomaotai.Event
import com.example.xiaomaotai.ValidationUtils
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    event: Event?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    events: EventStore
) {
    var eventName by remember { mutableStateOf(event?.eventName ?: "") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 字数限制检查
    val isNameTooLong = eventName.length > 10

    // 使用DateParser解析事件日期
    val parsedEventDate = remember(event?.eventDate) {
        event?.eventDate?.let { DateParser.parse(it) }
    }
    
    // 是否为编辑模式
    val isEditMode = event != null

    // 根据事件类型选择对应的Tab
    // 0: 公历, 1: 农历, 2: 忽略年份(公历), 3: 忽略年份(农历)
    var selectedTab by remember {
        mutableStateOf(
            if (isEditMode) {
                when (parsedEventDate?.type) {
                    DateParser.DateType.LUNAR -> 1 // 农历
                    DateParser.DateType.MONTH_DAY -> 2 // 忽略年份(公历)
                    DateParser.DateType.LUNAR_MONTH_DAY -> 3 // 忽略年份(农历)
                    else -> 0 // 公历
                }
            } else {
                0 // 新建时默认公历
            }
        )
    }

    // 获取当前公历日期
    val todaySolar = remember {
        val cal = Calendar.getInstance()
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    
    // 获取当前公历日期对应的农历日期
    val todayLunar = remember {
        try {
            val today = java.time.LocalDate.now()
            val solar = com.nlf.calendar.Solar(today.year, today.monthValue, today.dayOfMonth)
            val lunar = solar.lunar
            val isLeap = lunar.month < 0
            val actualMonth = if (isLeap) -lunar.month else lunar.month
            android.util.Log.d("EventDialog", "当前公历 ${today} 对应农历 ${lunar.year}年${if (isLeap) "闰" else ""}${actualMonth}月${lunar.day}日")
            Triple(lunar.year, if (isLeap) -actualMonth else actualMonth, lunar.day)
        } catch (e: Exception) {
            android.util.Log.e("EventDialog", "获取当前农历日期失败", e)
            todaySolar
        }
    }

    // 计算编辑模式下各Tab的初始值
    val (initSolarYear, initSolarMonth, initSolarDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.SOLAR) {
            Triple(
                parsedEventDate.year ?: todaySolar.first,
                parsedEventDate.month,
                parsedEventDate.day
            )
        } else {
            todaySolar
        }
    }
    
    val (initLunarYear, initLunarMonth, initLunarDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.LUNAR) {
            Triple(
                parsedEventDate.year ?: todayLunar.first,
                if (parsedEventDate.isLeapMonth) -parsedEventDate.month else parsedEventDate.month,
                parsedEventDate.day
            )
        } else {
            todayLunar
        }
    }
    
    val (initMonthDayMonth, initMonthDayDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.MONTH_DAY) {
            Pair(parsedEventDate.month, parsedEventDate.day)
        } else {
            Pair(todaySolar.second, todaySolar.third)
        }
    }
    
    val (initLunarMonthDayMonth, initLunarMonthDayDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.LUNAR_MONTH_DAY) {
            Pair(
                if (parsedEventDate.isLeapMonth) -parsedEventDate.month else parsedEventDate.month,
                parsedEventDate.day
            )
        } else {
            Pair(todayLunar.second, todayLunar.third)
        }
    }

    // 每个Tab独立存储日期状态
    // Tab 0: 公历
    var solarYear by remember { mutableStateOf(initSolarYear) }
    var solarMonth by remember { mutableStateOf(initSolarMonth) }
    var solarDay by remember { mutableStateOf(initSolarDay) }
    
    // Tab 1: 农历
    var lunarYear by remember { mutableStateOf(initLunarYear) }
    var lunarMonth by remember { mutableStateOf(initLunarMonth) }
    var lunarDay by remember { mutableStateOf(initLunarDay) }
    
    // Tab 2: 忽略年份(公历)
    var monthDayMonth by remember { mutableStateOf(initMonthDayMonth) }
    var monthDayDay by remember { mutableStateOf(initMonthDayDay) }
    
    // Tab 3: 忽略年份(农历)
    var lunarMonthDayMonth by remember { mutableStateOf(initLunarMonthDayMonth) }
    var lunarMonthDayDay by remember { mutableStateOf(initLunarMonthDayDay) }
    
    // 记录用户是否在当前Tab手动选择过日期
    var userHasSelectedDate by remember { mutableStateOf(false) }
    
    // 编辑模式日志
    LaunchedEffect(parsedEventDate) {
        if (isEditMode && parsedEventDate != null) {
            android.util.Log.d("EventDialog", "编辑模式初始化: 类型=${parsedEventDate.type}, month=${parsedEventDate.month}, isLeap=${parsedEventDate.isLeapMonth}")
        }
    }
    
    // 获取当前Tab对应的日期值
    val (selectedYear, selectedMonth, selectedDay) = when (selectedTab) {
        0 -> Triple(solarYear, solarMonth, solarDay)
        1 -> Triple(lunarYear, lunarMonth, lunarDay)
        2 -> Triple(todaySolar.first, monthDayMonth, monthDayDay) // 忽略年份(公历)模式，使用公历年份计算天数
        3 -> Triple(todayLunar.first, lunarMonthDayMonth, lunarMonthDayDay) // 忽略年份(农历)模式，使用农历年份计算天数和闰月
        else -> Triple(todaySolar.first, todaySolar.second, todaySolar.third)
    }

    // 手势返回处理
    BackHandler {
        onDismiss()
    }

    // 底部弹窗
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // 顶部拖拽指示条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    // 标题
                    Text(
                        text = if (event == null) "添加纪念日" else "编辑纪念日",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // 事件名称输入框
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "事件名称",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { newValue ->
                                if (newValue.length <= 10) {
                                    eventName = newValue
                                    if (errorMessage.contains("字数") || errorMessage.contains("名称")) {
                                        errorMessage = ""
                                    }
                                }
                            },
                            placeholder = {
                                Text(
                                    "请输入事件名称",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            isError = errorMessage.isNotEmpty() && (errorMessage.contains("名称") || errorMessage.contains("字数")),
                            singleLine = true
                        )

                        // 字数统计
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${eventName.length}/10",
                                fontSize = 12.sp,
                                color = if (isNameTooLong) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 日期类型选择器
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "日期类型",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // 分段选择器 - 4个选项（简化文案）
                        SegmentedSelector(
                            options = listOf("公历", "农历", "忽略年（公）", "忽略年（农）"),
                            selectedIndex = selectedTab,
                            onSelectionChanged = { index ->
                                val previousTab = selectedTab
                                selectedTab = index
                                // 切换Tab时重置用户选择标记
                                userHasSelectedDate = false
                                android.util.Log.d("EventDialog", "切换Tab: $previousTab -> $index, 不进行日期转换")
                                // 不再进行日期转换，每个Tab保持独立的日期状态
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 日期选择器
                    Text(
                        text = "选择日期",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    CustomDatePickerContent(
                        selectedTab = selectedTab,
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        selectedDay = selectedDay,
                        onDateChanged = { year: Int, month: Int, day: Int ->
                            userHasSelectedDate = true
                            // 根据当前Tab更新对应的日期状态
                            when (selectedTab) {
                                0 -> {
                                    solarYear = year
                                    solarMonth = month
                                    solarDay = day
                                }
                                1 -> {
                                    lunarYear = year
                                    lunarMonth = month
                                    lunarDay = day
                                }
                                2 -> {
                                    monthDayMonth = month
                                    monthDayDay = day
                                }
                                3 -> {
                                    lunarMonthDayMonth = month
                                    lunarMonthDayDay = day
                                }
                            }
                        }
                    )

                    // 错误信息
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 按钮区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 取消按钮
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                "取消",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 保存按钮
                        Button(
                            onClick = {
                                errorMessage = ""
                                val nameResult = ValidationUtils.validateEventName(eventName)
                                if (!nameResult.isValid) {
                                    errorMessage = nameResult.message
                                    return@Button
                                }

                                // 确保使用最新的eventDate格式
                                val finalEventDate = when (selectedTab) {
                                    0 -> {
                                        // 公历格式
                                        String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
                                    }
                                    1 -> {
                                        // 农历格式，检查是否为闰月
                                        if (lunarMonth < 0) {
                                            // 负数表示闰月
                                            val actualMonth = -lunarMonth
                                            "lunar:${lunarYear}-L${String.format("%02d", actualMonth)}-${String.format("%02d", lunarDay)}"
                                        } else {
                                            // 正数表示普通月份
                                            "lunar:${String.format("%04d-%02d-%02d", lunarYear, lunarMonth, lunarDay)}"
                                        }
                                    }
                                    2 -> {
                                        // 忽略年份(公历)格式
                                        String.format("%02d-%02d", monthDayMonth, monthDayDay)
                                    }
                                    3 -> {
                                        // 忽略年份(农历)格式
                                        if (lunarMonthDayMonth < 0) {
                                            // 负数表示闰月
                                            val actualMonth = -lunarMonthDayMonth
                                            "lunar:L${String.format("%02d", actualMonth)}-${String.format("%02d", lunarMonthDayDay)}"
                                        } else {
                                            "lunar:${String.format("%02d-%02d", lunarMonthDayMonth, lunarMonthDayDay)}"
                                        }
                                    }
                                    else -> String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
                                }

                                // 检查重复事件（编辑时排除自己）
                                scope.launch {
                                    val existingEvents = events.currentEvents()
                                    val isDuplicate = existingEvents.any { existingEvent ->
                                        existingEvent.eventName == eventName &&
                                        existingEvent.eventDate == finalEventDate &&
                                        existingEvent.id != (event?.id ?: "")
                                    }

                                    if (isDuplicate) {
                                        errorMessage = "已存在相同名称和日期的事件"
                                        return@launch
                                    }

                                    onSave(eventName, finalEventDate)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "保存",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分段选择器组件
 */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface
                        else Color.Transparent
                    )
                    .clickable { onSelectionChanged(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
