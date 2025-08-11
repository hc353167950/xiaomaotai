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
import com.example.xiaomaotai.Event
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
    var eventDate by remember { mutableStateOf(event?.eventDate ?: "") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // 字数限制检查
    val isNameTooLong = eventName.length > 10
    
    // 根据事件类型选择对应的Tab
    var selectedTab by remember { 
        mutableStateOf(
            when {
                event?.eventDate?.startsWith("lunar:") == true -> 1 // 农历
                event?.eventDate?.matches(Regex("\\d{2}-\\d{2}")) == true -> 2 // 忽略年份
                else -> 0 // 公历
            }
        )
    }
    
    // Initialize calendar state - 编辑时回显事件日期，新建时默认当天
    val initialCalendar = remember {
        val cal = Calendar.getInstance()
        if (event?.eventDate?.isNotEmpty() == true) {
            try {
                val dateToparse = when {
                    event.eventDate.startsWith("lunar:") -> {
                        // 农历日期：lunar:2025-08-10 -> 2025-08-10
                        event.eventDate.removePrefix("lunar:")
                    }
                    event.eventDate.matches(Regex("\\d{2}-\\d{2}")) -> {
                        // 忽略年份：08-10 -> 2025-08-10（使用当前年份）
                        "${Calendar.getInstance().get(Calendar.YEAR)}-${event.eventDate}"
                    }
                    else -> {
                        // 公历日期：直接使用
                        event.eventDate
                    }
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                cal.time = sdf.parse(dateToparse)!!
            } catch (e: Exception) {
                // Keep today's date on parsing error
                cal.time = Date()
            }
        } else {
            // 新建事件时默认选择今天
            cal.time = Date()
        }
        cal
    }
    
    var selectedYear by remember { mutableStateOf(initialCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(initialCalendar.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    // Set initial date string if empty
    LaunchedEffect(Unit) {
        if (eventDate.isEmpty()) {
            eventDate = dateFormatter.format(initialCalendar.time)
        }
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
                            // 切换到公历时更新eventDate格式
                            eventDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
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
                            // 切换到农历时更新eventDate格式
                            eventDate = "lunar:${String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)}"
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
                            // 切换到忽略年份时更新eventDate格式
                            eventDate = String.format("%02d-%02d", selectedMonth, selectedDay)
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
                        
                        // 根据当前选中的Tab更新eventDate格式
                        eventDate = when (selectedTab) {
                            1 -> "lunar:${String.format("%04d-%02d-%02d", year, month, day)}" // 农历格式
                            2 -> String.format("%02d-%02d", month, day) // 忽略年份格式
                            else -> String.format("%04d-%02d-%02d", year, month, day) // 公历格式
                        }
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
                        
                        // 检查重复事件（编辑时排除自己）
                        scope.launch {
                            val existingEvents = dataManager.getEvents()
                            val isDuplicate = existingEvents.any { existingEvent ->
                                existingEvent.eventName == eventName && 
                                existingEvent.eventDate == eventDate &&
                                existingEvent.id != (event?.id ?: "")
                            }
                            
                            if (isDuplicate) {
                                errorMessage = "已存在相同名称和日期的事件"
                                return@launch
                            }
                            
                            // 确保使用最新的eventDate格式
                            val finalEventDate = when (selectedTab) {
                                1 -> "lunar:${String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)}" // 农历格式
                                2 -> String.format("%02d-%02d", selectedMonth, selectedDay) // 忽略年份格式
                                else -> String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay) // 公历格式
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
