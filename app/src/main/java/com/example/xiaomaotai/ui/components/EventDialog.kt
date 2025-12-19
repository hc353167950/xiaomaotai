package com.example.xiaomaotai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DataManager
import com.example.xiaomaotai.DateParser
import com.example.xiaomaotai.Event
import com.example.xiaomaotai.LunarCalendarHelper
import com.example.xiaomaotai.ValidationUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (event == null) "添加纪念日" else "编辑纪念日",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 事件名称
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { newValue ->
                        if (newValue.length <= 10) {
                            eventName = newValue
                            if (errorMessage.contains("字数")) {
                                errorMessage = ""
                            }
                        }
                    },
                    label = { 
                        Text(
                            "事件名称",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isNameTooLong) {
                                Text(
                                    text = "事件名称最多10个字",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "",
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "${eventName.length}/10",
                                color = if (isNameTooLong) MaterialTheme.colorScheme.error 
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage.isNotEmpty() || isNameTooLong,
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 日期类型TAB
                Text(
                    text = "日期类型",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { },  // 移除下划线指示器
                    divider = { }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { 
                            // 切换到公历时，如果之前是忽略年份模式，使用当前年份
                            if (selectedTab == 2) {
                                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                selectedYear = currentYear
                            }
                            selectedTab = 0
                        },
                        text = { 
                            Text(
                                "公历",
                                fontSize = if (selectedTab == 0) 16.sp else 14.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { 
                            // 切换到农历时，如果之前是忽略年份模式，使用当前年份
                            if (selectedTab == 2) {
                                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                selectedYear = currentYear
                            }
                            selectedTab = 1
                        },
                        text = { 
                            Text(
                                "农历",
                                fontSize = if (selectedTab == 1) 16.sp else 14.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { 
                            selectedTab = 2
                        },
                        text = { 
                            Text(
                                "忽略年份",
                                fontSize = if (selectedTab == 2) 16.sp else 14.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 内嵌日期选择器
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
                
                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            }
        }
    }
}
