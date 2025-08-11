package com.example.xiaomaotai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.util.Calendar

private data class FullDate(val year: Int, val month: Int, val day: Int, val daysInMonth: Int, val daysList: List<Int>)

@Composable
fun NewEventDatePicker(
    initialDate: Calendar,
    onDateSelected: (Calendar, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0:公历, 1:农历, 2:忽略年份
    val selectedDate = remember { mutableStateOf(initialDate) }

    val years = (1901..2099).toList()
    val months = (1..12).toList()

    val fullDate = remember(selectedTab, selectedDate.value) {
        val cal = selectedDate.value
        when (selectedTab) {
            1 -> { // 农历
                try {
                    val solar = Solar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    val lunar = solar.lunar
                    val days = 30 // 农历月份最多30天，使用固定值避免API问题
                    FullDate(lunar.year, lunar.month, lunar.day, days, (1..days).toList())
                } catch (e: Exception) {
                    // 如果农历转换失败，使用公历
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    FullDate(year, month, day, days, (1..days).toList())
                }
            }
            else -> { // 公历或忽略年份
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                FullDate(year, month, day, days, (1..days).toList())
            }
        }
    }
    val days = fullDate.daysList


    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择日期", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("公历") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("农历") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("忽略年份") })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 固定的列标题，不随滚动变化
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedTab != 2) {
                        Text(
                            text = "年",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "日",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedTab != 2) {
                        WheelPicker(
                            items = years,
                            initialIndex = years.indexOf(fullDate.year).coerceAtLeast(0),
                            onIndexChanged = { index ->
                                val newYear = years[index]
                                val cal = selectedDate.value.clone() as Calendar
                                if (selectedTab == 1) {
                                    try {
                                        val solar = Solar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                                        val lunar = solar.lunar
                                        val newLunar = Lunar(newYear, lunar.month, lunar.day)
                                        val newSolar = newLunar.solar
                                        cal.set(Calendar.YEAR, newSolar.year)
                                        cal.set(Calendar.MONTH, newSolar.month - 1)
                                        cal.set(Calendar.DAY_OF_MONTH, newSolar.day)
                                    } catch (e: Exception) {
                                        cal.set(Calendar.YEAR, newYear)
                                    }
                                } else {
                                    cal.set(Calendar.YEAR, newYear)
                                }
                                selectedDate.value = cal
                            },
                            itemToString = { if (selectedTab == 1) LunarCalendarHelper.getYearName(it) else "${it}年" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    WheelPicker(
                        items = months,
                        initialIndex = months.indexOf(fullDate.month).coerceAtLeast(0),
                        onIndexChanged = { index ->
                            val newMonth = months[index]
                            val cal = selectedDate.value.clone() as Calendar
                            if (selectedTab == 1) {
                                try {
                                    val solar = Solar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                                    val lunar = solar.lunar
                                    val newLunar = Lunar(lunar.year, newMonth, lunar.day)
                                    val newSolar = newLunar.solar
                                    cal.set(Calendar.YEAR, newSolar.year)
                                    cal.set(Calendar.MONTH, newSolar.month - 1)
                                    cal.set(Calendar.DAY_OF_MONTH, newSolar.day)
                                } catch (e: Exception) {
                                    cal.set(Calendar.MONTH, newMonth - 1)
                                }
                            } else {
                                cal.set(Calendar.MONTH, newMonth - 1)
                            }
                            selectedDate.value = cal
                        },
                        itemToString = { if (selectedTab == 1) LunarCalendarHelper.getMonthName(it) else "${it}月" },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPicker(
                        items = days,
                        initialIndex = days.indexOf(fullDate.day).coerceAtLeast(0),
                        onIndexChanged = { index ->
                            val newDay = days[index]
                            val cal = selectedDate.value.clone() as Calendar
                            if (selectedTab == 1) {
                                try {
                                    val solar = Solar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                                    val lunar = solar.lunar
                                    val newLunar = Lunar(lunar.year, lunar.month, newDay)
                                    val newSolar = newLunar.solar
                                    cal.set(Calendar.YEAR, newSolar.year)
                                    cal.set(Calendar.MONTH, newSolar.month - 1)
                                    cal.set(Calendar.DAY_OF_MONTH, newSolar.day)
                                } catch (e: Exception) {
                                    cal.set(Calendar.DAY_OF_MONTH, newDay)
                                }
                            } else {
                                cal.set(Calendar.DAY_OF_MONTH, newDay)
                            }
                            selectedDate.value = cal
                        },
                         itemToString = { if (selectedTab == 1) LunarCalendarHelper.getDayName(it) else "${it}日" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onDateSelected(selectedDate.value, selectedTab) }) {
                        Text("完成")
                    }
                }
            }
        }
    }
}
