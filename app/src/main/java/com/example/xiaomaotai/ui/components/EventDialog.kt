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
import com.example.xiaomaotai.DataManager
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
    dataManager: DataManager
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

    // 根据事件类型选择对应的Tab
    var selectedTab by remember {
        mutableStateOf(
            when (parsedEventDate?.type) {
                DateParser.DateType.LUNAR -> 1 // 农历
                DateParser.DateType.MONTH_DAY -> 2 // 忽略年份
                else -> 0 // 公历
            }
        )
    }

    // Initialize calendar state - 编辑时回显事件日期，新建时默认当天
    val initialCalendar = remember {
        val cal = Calendar.getInstance()
        if (parsedEventDate != null) {
            try {
                // 使用解析后的日期设置calendar
                cal.set(Calendar.YEAR, parsedEventDate.year ?: cal.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, parsedEventDate.month - 1)
                cal.set(Calendar.DAY_OF_MONTH, parsedEventDate.day)
            } catch (e: Exception) {
                cal.time = Date()
            }
        } else {
            cal.time = Date()
        }
        cal
    }

    // 初始化日期选择器的值，特别处理闰月
    val (initYear, initMonth, initDay) = remember {
        if (parsedEventDate != null) {
            val year = parsedEventDate.year ?: initialCalendar.get(Calendar.YEAR)
            val month = if (parsedEventDate.isLeapMonth) -parsedEventDate.month else parsedEventDate.month
            val day = parsedEventDate.day
            Triple(year, month, day)
        } else {
            Triple(
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH) + 1,
                initialCalendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    var selectedYear by remember { mutableStateOf(initYear) }
    var selectedMonth by remember { mutableStateOf(initMonth) }
    var selectedDay by remember { mutableStateOf(initDay) }

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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                errorBorderColor = MaterialTheme.colorScheme.error
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

                        // 分段选择器
                        SegmentedSelector(
                            options = listOf("公历", "农历", "忽略年份"),
                            selectedIndex = selectedTab,
                            onSelectionChanged = { index ->
                                // 切换时处理年份
                                if (selectedTab == 2 && index != 2) {
                                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                    selectedYear = currentYear
                                }
                                selectedTab = index
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
                            selectedYear = year
                            selectedMonth = month
                            selectedDay = day
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
                                    1 -> {
                                        // 农历格式，检查是否为闰月
                                        if (selectedMonth < 0) {
                                            // 负数表示闰月
                                            val actualMonth = -selectedMonth
                                            "lunar:${selectedYear}-L${String.format("%02d", actualMonth)}-${String.format("%02d", selectedDay)}" // 闰月格式
                                        } else {
                                            // 正数表示普通月份
                                            "lunar:${String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)}" // 普通农历格式
                                        }
                                    }
                                    2 -> String.format("%02d-%02d", selectedMonth, selectedDay) // 忽略年份格式
                                    else -> String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay) // 公历格式
                                }

                                // 检查重复事件（编辑时排除自己）
                                scope.launch {
                                    val existingEvents = dataManager.getEvents()
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
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
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
                    .then(
                        if (isSelected) Modifier.border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .clickable { onSelectionChanged(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
