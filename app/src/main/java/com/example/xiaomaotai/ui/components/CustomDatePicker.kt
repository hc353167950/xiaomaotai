package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.LunarCalendarHelper
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun CustomDatePickerContent(
    selectedTab: Int,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onDateChanged: (Int, Int, Int) -> Unit
) {
    val years = (1901..2099).toList()
    
    // 农历月份列表（包含闰月）
    val months = remember(selectedYear, selectedTab) {
        if (selectedTab == 1) {
            // 农历模式，获取包含闰月的月份列表
            android.util.Log.d("CustomDatePicker", "Getting lunar months for year: $selectedYear")
            val lunarMonthsList = LunarCalendarHelper.getLunarMonthsInYear(selectedYear)
            android.util.Log.d("CustomDatePicker", "Lunar months list: $lunarMonthsList")
            lunarMonthsList.map { (month, isLeap) ->
                if (isLeap) {
                    // 闰月使用负数表示，例如闰六月使用-6
                    -month
                } else {
                    month
                }
            }
        } else {
            // 公历模式，使用正常月份
            (1..12).toList()
        }
    }
    
    // 计算当前月份的天数
    val daysInMonth = remember(selectedYear, selectedMonth, selectedTab) {
        if (selectedTab == 1) {
            // 农历月份固定30天
            30
        } else {
            // 公历月份天数
            val cal = Calendar.getInstance()
            cal.set(selectedYear, selectedMonth - 1, 1)
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }
    val days = (1..daysInMonth).toList()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedTab != 2) { // 不是"忽略年份"模式才显示年份
                WheelPicker(
                    items = years,
                    initialIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                    onIndexChanged = { index ->
                        val newYear = years[index]
                        onDateChanged(newYear, selectedMonth, selectedDay)
                    },
                    itemToString = { if (selectedTab == 1) LunarCalendarHelper.getYearName(it) else it.toString() },
                    modifier = Modifier.weight(1f)
                )
            }
            
            WheelPicker(
                items = months,
                initialIndex = months.indexOf(selectedMonth).let { index ->
                    if (index >= 0) {
                        index
                    } else {
                        // 如果找不到，可能是闰月，尝试找对应的闰月索引
                        if (selectedTab == 1 && selectedMonth < 0) {
                            // 闰月情况，找对应的负数索引
                            months.indexOf(selectedMonth)
                        } else {
                            0
                        }
                    }
                }.coerceAtLeast(0),
                onIndexChanged = { index ->
                    val newMonth = months[index]
                    onDateChanged(selectedYear, newMonth, selectedDay)
                },
                itemToString = { 
                    if (selectedTab == 1) {
                        if (it < 0) {
                            // 闰月显示
                            LunarCalendarHelper.getLunarMonthName(-it, true)
                        } else {
                            // 正常月份显示
                            LunarCalendarHelper.getLunarMonthName(it, false)
                        }
                    } else {
                        it.toString()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            
            WheelPicker(
                items = days,
                initialIndex = days.indexOf(selectedDay.coerceIn(1, daysInMonth)).coerceAtLeast(0),
                onIndexChanged = { index ->
                    val newDay = days[index]
                    onDateChanged(selectedYear, selectedMonth, newDay)
                },
                itemToString = { if (selectedTab == 1) LunarCalendarHelper.getDayName(it) else it.toString() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun WheelPicker(
    items: List<Int>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    itemToString: (Int) -> String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val itemHeight = 40.dp
    val visibleItemsCount = 3
    val totalHeight = itemHeight * visibleItemsCount

    // 添加空白项以便选中项居中，但限制滑动范围
    val paddedItems = listOf(-1) + items + listOf(-1)

    val listState = rememberLazyListState()
    var selectedIndex by remember(items, initialIndex) { mutableStateOf(initialIndex) }

    // 当initialIndex改变时，更新选中状态并滚动到正确位置
    LaunchedEffect(initialIndex) {
        selectedIndex = initialIndex
        // 滚动到正确位置使选中项居中
        listState.scrollToItem(initialIndex)
    }

    // 监听滑动状态，当滑动停止时自动居中
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 获取当前可见的中心项
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val centerVisibleIndex = firstVisibleIndex + 1

            // 计算实际的索引（减去填充项）
            val actualIndex = centerVisibleIndex - 1

            // 确保索引在有效范围内
            val clampedIndex = actualIndex.coerceIn(0, items.size - 1)

            if (clampedIndex != selectedIndex) {
                selectedIndex = clampedIndex
                onIndexChanged(clampedIndex)
            }

            // 自动滚动到正确位置，确保选中项居中
            val targetScrollIndex = clampedIndex + 1
            if (listState.firstVisibleItemIndex != targetScrollIndex - 1) {
                scope.launch {
                    listState.animateScrollToItem(targetScrollIndex - 1)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 0.dp),
            userScrollEnabled = true
        ) {
            items(paddedItems.size) { index ->
                val item = paddedItems[index]
                val actualIndex = index - 1
                val isSelected = actualIndex == selectedIndex
                val isVisible = item != -1

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable(enabled = isVisible) {
                            if (isVisible && actualIndex in items.indices) {
                                selectedIndex = actualIndex
                                onIndexChanged(actualIndex)
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVisible) {
                        Text(
                            text = itemToString(item),
                            fontSize = if (isSelected) 18.sp else 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
