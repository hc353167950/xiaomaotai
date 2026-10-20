package com.example.xiaomaotai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.xiaomaotai.ReminderSchedule
import androidx.compose.foundation.rememberScrollState
import com.example.xiaomaotai.EventMode
import com.example.xiaomaotai.ReminderTone
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
    onSave: (Event) -> Unit,
    events: EventStore
) {
    var eventName by remember { mutableStateOf(event?.eventName ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    // ---- 新增字段状态 ----
    var note by remember { mutableStateOf(event?.note ?: "") }
    var mode by remember { mutableStateOf(event?.mode ?: EventMode.COUNTDOWN) }
    var reminderEnabled by remember { mutableStateOf(event?.reminderEnabled ?: true) }
    var reminderTone by remember { mutableStateOf(event?.reminderTone ?: ReminderTone.NEUTRAL) }
    // null = 用全局默认；否则为自定义档位集合
    var customOffsets by remember { mutableStateOf(event?.reminderDaysBefore) }
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
                        .wrapContentHeight()
                        .imePadding()
                ) {
                    // ---------- 可滚动区域 ----------
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .padding(horizontal = Space.xl)
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

                    Spacer(modifier = Modifier.height(Space.lg))

                    // ---------- 计数方向 ----------
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "计数方式",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.sm)
                        )
                        SegmentedSelector(
                            options = listOf("倒数（还有几天）", "正数（已经几天）"),
                            selectedIndex = mode.ordinal,
                            onSelectionChanged = { index ->
                                mode = if (index == 1) EventMode.COUNT_UP else EventMode.COUNTDOWN
                            }
                        )
                        if (mode == EventMode.COUNT_UP) {
                            Text(
                                text = "从起始日算起，「今天」记为第 1 天",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = Space.xs, start = Space.xs)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Space.lg))

                    // ---------- 备注 ----------
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "备注（可选）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.sm)
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= 200) note = it },
                            placeholder = {
                                Text(
                                    "记下这个日子背后的故事…",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp),
                            shape = Shape.small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            maxLines = 4,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.xs),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${note.length}/200",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Space.lg))

                    // ---------- 提醒 ----------
                    // 正数模式不参与提醒：它是持续累积的计数，没有「到期」这件事。
                    // 隐藏整块设置，避免用户配了却不生效。
                    if (mode == EventMode.COUNTDOWN) {
                        ReminderSection(
                            enabled = reminderEnabled,
                            onEnabledChange = { reminderEnabled = it },
                            tone = reminderTone,
                            onToneChange = { reminderTone = it },
                            customOffsets = customOffsets,
                            onOffsetsChange = { customOffsets = it }
                        )
                    } else {
                        Text(
                            text = "正数模式不发送提醒",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = Space.xs)
                        )
                    }

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

                    }   // ← 结束可滚动内容

                    // ---------- 固定在底部的按钮区 ----------
                    // 与滚动内容分离：无论内容多长、键盘是否弹出，取消/保存始终可见可点
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = Space.xl)
                                .padding(top = Space.md, bottom = Space.lg)
                        ) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(Space.md))
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

                                    // 把编辑后的完整字段交给上层，由它决定是新增还是更新
                                    onSave(
                                        (event ?: Event(eventName = eventName, eventDate = finalEventDate))
                                            .copy(
                                                eventName = eventName,
                                                eventDate = finalEventDate,
                                                note = note.trim(),
                                                mode = mode,
                                                reminderEnabled = reminderEnabled,
                                                reminderTone = reminderTone,
                                                reminderDaysBefore = customOffsets
                                            )
                                    )
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
                        }        // 结束 Button
                            }    // 结束 Row(按钮行)
                        }        // 结束 Column(底部按钮容器)
                    }            // 结束 Surface(固定底部区)
                }                // 结束 Column(整体)
            }                    // 结束 Surface(弹窗)
        }                        // 结束 Box
    }                            // 结束 Dialog
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

/**
 * 提醒设置区：开关 + 语气 + 提前档位。
 *
 * 单独抽成组件是因为这块逻辑有三层联动（关闭提醒→语气和档位都失效），
 * 摊在 EventDialog 里会让主流程读不下去。
 */
@Composable
private fun ReminderSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    tone: ReminderTone,
    onToneChange: (ReminderTone) -> Unit,
    customOffsets: List<Int>?,
    onOffsetsChange: (List<Int>?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnabledChange(!enabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开启提醒",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (enabled) "到期会推送通知" else "不推送任何提醒",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (!enabled) return@Column

        Spacer(modifier = Modifier.height(Space.md))

        // 语气：决定通知文案的情感色彩
        Text(
            text = "提醒语气",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.sm)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ReminderTone.entries.forEach { option ->
                val selected = tone == option
                Surface(
                    onClick = { onToneChange(option) },
                    modifier = Modifier.weight(1f),
                    shape = Shape.small,
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (selected)
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, Color.Transparent)
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.sm),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = when (tone) {
                ReminderTone.NEUTRAL -> "只陈述事实，不预设场合"
                ReminderTone.BLESSING -> "适合生日、纪念日等喜事"
                ReminderTone.MEMORIAL -> "适合忌日、离别等需要安静的日子"
                ReminderTone.REMIND -> "像日程一样直接提醒"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = Space.xs, start = Space.xs)
        )

        Spacer(modifier = Modifier.height(Space.md))

        // 提前档位
        Text(
            text = "提前提醒",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.sm)
        )
        val effective = customOffsets ?: ReminderSchedule.OFFSETS
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf(7, 1, 0).forEach { days ->
                val selected = days in effective
                Surface(
                    onClick = {
                        val next = if (selected) effective - days else effective + days
                        // 与默认值一致时回归 null，保持「跟随全局」语义
                        onOffsetsChange(
                            if (next.sortedDescending() == ReminderSchedule.OFFSETS) null
                            else next.sortedDescending()
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = Shape.small,
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (selected)
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, Color.Transparent)
                ) {
                    Text(
                        text = if (days == 0) "当天" else "提前${days}天",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.sm),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = if (effective.isEmpty()) "未选择任何档位，将不会提醒"
                   else if (customOffsets == null) "默认：提前 7 天、1 天、当天"
                   else "已自定义",
            fontSize = 11.sp,
            color = if (effective.isEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = Space.xs, start = Space.xs)
        )

        // 具体推送时刻：从设置页搬到这里，用户配置提醒时一眼就能看到
        Text(
            text = "提前 7 天 / 1 天 08:00　·　当天 00:00 / 08:00 / 12:00",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = Space.sm, start = Space.xs)
        )
        Text(
            text = "当天新增的纪念日会在 30 秒后立即提醒",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp, start = Space.xs)
        )
    }
}
