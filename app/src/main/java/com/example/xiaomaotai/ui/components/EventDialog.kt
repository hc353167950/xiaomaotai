package com.example.xiaomaotai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DateParser
import com.example.xiaomaotai.Event
import com.example.xiaomaotai.EventStore
import com.example.xiaomaotai.ValidationUtils
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Sizes
import com.example.xiaomaotai.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 添加 / 编辑纪念日弹窗。
 *
 * 版式：底部抽屉式弹窗，顶部拖拽条 + 标题 + 名称输入 + 类型分段器 + 日期滚轮 + 按钮。
 * 内容可滚动，键盘弹出时不遮挡输入框。
 */
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
    val scrollState = rememberScrollState()

    val parsedEventDate = remember(event?.eventDate) {
        event?.eventDate?.let { DateParser.parse(it) }
    }

    val isEditMode = event != null

    // 0: 公历, 1: 农历, 2: 忽略年份(公历), 3: 忽略年份(农历)
    var selectedTab by remember {
        mutableStateOf(
            if (isEditMode) {
                when (parsedEventDate?.type) {
                    DateParser.DateType.LUNAR -> 1
                    DateParser.DateType.MONTH_DAY -> 2
                    DateParser.DateType.LUNAR_MONTH_DAY -> 3
                    else -> 0
                }
            } else 0
        )
    }

    val todaySolar = remember {
        val today = java.time.LocalDate.now()
        Triple(today.year, today.monthValue, today.dayOfMonth)
    }

    val todayLunar = remember {
        try {
            val today = java.time.LocalDate.now()
            val solar = com.nlf.calendar.Solar(today.year, today.monthValue, today.dayOfMonth)
            val lunar = solar.lunar
            val isLeap = lunar.month < 0
            val actualMonth = if (isLeap) -lunar.month else lunar.month
            Triple(lunar.year, if (isLeap) -actualMonth else actualMonth, lunar.day)
        } catch (e: Exception) {
            todaySolar
        }
    }

    val (initSolarYear, initSolarMonth, initSolarDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.SOLAR) {
            Triple(parsedEventDate.year ?: todaySolar.first, parsedEventDate.month, parsedEventDate.day)
        } else todaySolar
    }

    val (initLunarYear, initLunarMonth, initLunarDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.LUNAR) {
            Triple(
                parsedEventDate.year ?: todayLunar.first,
                if (parsedEventDate.isLeapMonth) -parsedEventDate.month else parsedEventDate.month,
                parsedEventDate.day
            )
        } else todayLunar
    }

    val (initMonthDayMonth, initMonthDayDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.MONTH_DAY) {
            Pair(parsedEventDate.month, parsedEventDate.day)
        } else Pair(todaySolar.second, todaySolar.third)
    }

    val (initLunarMonthDayMonth, initLunarMonthDayDay) = remember(parsedEventDate) {
        if (isEditMode && parsedEventDate != null && parsedEventDate.type == DateParser.DateType.LUNAR_MONTH_DAY) {
            Pair(
                if (parsedEventDate.isLeapMonth) -parsedEventDate.month else parsedEventDate.month,
                parsedEventDate.day
            )
        } else Pair(todayLunar.second, todayLunar.third)
    }

    var solarYear by remember { mutableStateOf(initSolarYear) }
    var solarMonth by remember { mutableStateOf(initSolarMonth) }
    var solarDay by remember { mutableStateOf(initSolarDay) }

    var lunarYear by remember { mutableStateOf(initLunarYear) }
    var lunarMonth by remember { mutableStateOf(initLunarMonth) }
    var lunarDay by remember { mutableStateOf(initLunarDay) }

    var monthDayMonth by remember { mutableStateOf(initMonthDayMonth) }
    var monthDayDay by remember { mutableStateOf(initMonthDayDay) }

    var lunarMonthDayMonth by remember { mutableStateOf(initLunarMonthDayMonth) }
    var lunarMonthDayDay by remember { mutableStateOf(initLunarMonthDayDay) }

    val (selectedYear, selectedMonth, selectedDay) = when (selectedTab) {
        0 -> Triple(solarYear, solarMonth, solarDay)
        1 -> Triple(lunarYear, lunarMonth, lunarDay)
        2 -> Triple(todaySolar.first, monthDayMonth, monthDayDay)
        3 -> Triple(todayLunar.first, lunarMonthDayMonth, lunarMonthDayDay)
        else -> Triple(todaySolar.first, todaySolar.second, todaySolar.third)
    }

    BackHandler { onDismiss() }

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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = Space.xl)
                        .padding(bottom = Space.xxl)
                ) {
                    // 顶部拖拽指示条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
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
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.padding(bottom = Space.xl)
                    )

                    // 事件名称
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "名称",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.sm)
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
                                    "例如：妈妈的生日",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = Shape.small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            isError = errorMessage.isNotEmpty() &&
                                (errorMessage.contains("名称") || errorMessage.contains("字数")),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.xs),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${eventName.length}/10",
                                fontSize = 11.sp,
                                color = if (eventName.length > 10) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Space.lg))

                    // 日期类型
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "类型",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.sm)
                        )

                        SegmentedSelector(
                            options = listOf("公历", "农历", "每年(公)", "每年(农)"),
                            selectedIndex = selectedTab,
                            onSelectionChanged = { index -> selectedTab = index }
                        )
                    }

                    Spacer(modifier = Modifier.height(Space.lg))

                    // 日期选择
                    Text(
                        text = "日期",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm)
                    )

                    CustomDatePickerContent(
                        selectedTab = selectedTab,
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        selectedDay = selectedDay,
                        onDateChanged = { year: Int, month: Int, day: Int ->
                            when (selectedTab) {
                                0 -> { solarYear = year; solarMonth = month; solarDay = day }
                                1 -> { lunarYear = year; lunarMonth = month; lunarDay = day }
                                2 -> { monthDayMonth = month; monthDayDay = day }
                                3 -> { lunarMonthDayMonth = month; lunarMonthDayDay = day }
                            }
                        }
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Space.md))
                        Surface(
                            shape = Shape.small,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Space.xl))

                    // 按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(Sizes.searchHeight),
                            shape = Shape.small,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text("取消", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                errorMessage = ""
                                val nameResult = ValidationUtils.validateEventName(eventName)
                                if (!nameResult.isValid) {
                                    errorMessage = nameResult.message
                                    return@Button
                                }

                                val finalEventDate = when (selectedTab) {
                                    0 -> String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
                                    1 -> if (lunarMonth < 0) {
                                        val actualMonth = -lunarMonth
                                        "lunar:${lunarYear}-L${String.format("%02d", actualMonth)}-${String.format("%02d", lunarDay)}"
                                    } else {
                                        "lunar:${String.format("%04d-%02d-%02d", lunarYear, lunarMonth, lunarDay)}"
                                    }
                                    2 -> String.format("%02d-%02d", monthDayMonth, monthDayDay)
                                    3 -> if (lunarMonthDayMonth < 0) {
                                        val actualMonth = -lunarMonthDayMonth
                                        "lunar:L${String.format("%02d", actualMonth)}-${String.format("%02d", lunarMonthDayDay)}"
                                    } else {
                                        "lunar:${String.format("%02d-%02d", lunarMonthDayMonth, lunarMonthDayDay)}"
                                    }
                                    else -> String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
                                }

                                scope.launch {
                                    val dateResult = ValidationUtils.validateEventDate(finalEventDate)
                                    if (!dateResult.isValid) {
                                        errorMessage = dateResult.message
                                        return@launch
                                    }

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
                                .height(Sizes.searchHeight),
                            shape = Shape.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("保存", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分段选择器。
 *
 * 视觉：胶囊底 + 选中项白底浮起，类似 iOS 分段控件。
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
            .height(38.dp)
            .clip(Shape.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelectionChanged(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
