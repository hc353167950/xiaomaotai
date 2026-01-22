package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    
    // 判断是否为农历模式（Tab 1 或 Tab 3）
    val isLunarMode = selectedTab == 1 || selectedTab == 3
    // 判断是否为忽略年份模式（Tab 2 或 Tab 3）
    val isIgnoreYearMode = selectedTab == 2 || selectedTab == 3

    // 农历月份列表（包含闰月）
    val months = remember(selectedYear, selectedTab) {
        if (isLunarMode) {
            // 农历模式，获取包含闰月的月份列表
            val lunarMonthsList = LunarCalendarHelper.getLunarMonthsInYear(selectedYear)
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
        if (isLunarMode) {
            // 农历月份：动态获取实际天数（大月30天，小月29天）
            val isLeap = selectedMonth < 0
            val actualMonth = kotlin.math.abs(selectedMonth)
            LunarCalendarHelper.getLunarMonthDays(selectedYear, actualMonth, isLeap)
        } else {
            // 公历月份天数
            val cal = Calendar.getInstance()
            cal.set(selectedYear, selectedMonth - 1, 1)
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }
    val days = (1..daysInMonth).toList()

    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isIgnoreYearMode) { // 不是"忽略年份"模式才显示年份
                WheelPicker(
                    items = years,
                    initialIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                    onIndexChanged = { index ->
                        val newYear = years[index]
                        onDateChanged(newYear, selectedMonth, selectedDay)
                    },
                    itemToString = { if (isLunarMode) LunarCalendarHelper.getYearName(it) else "${it}年" },
                    modifier = Modifier.weight(1f),
                    surfaceColor = surfaceColor
                )
            }

            WheelPicker(
                items = months,
                initialIndex = run {
                    val index = months.indexOf(selectedMonth)
                    if (index >= 0) {
                        index
                    } else {
                        // 如果找不到，可能是因为：
                        // 1. 闰月情况（selectedMonth < 0）
                        // 2. 月份超出当前年份的月份列表范围
                        if (isLunarMode) {
                            if (selectedMonth < 0) {
                                // 闰月情况，找对应的负数索引
                                val leapIndex = months.indexOf(selectedMonth)
                                if (leapIndex >= 0) leapIndex else months.size - 1
                            } else {
                                // 普通月份找不到，可能是月份值超出范围，使用最接近的月份
                                val closestIndex = months.indexOfFirst { it == selectedMonth || it == -selectedMonth }
                                if (closestIndex >= 0) {
                                    closestIndex
                                } else {
                                    // 如果还是找不到，使用最后一个月份（通常是腊月）
                                    (months.size - 1).coerceAtLeast(0)
                                }
                            }
                        } else {
                            // 公历模式，直接使用月份值-1作为索引
                            (selectedMonth - 1).coerceIn(0, months.size - 1)
                        }
                    }
                },
                onIndexChanged = { index ->
                    val newMonth = months[index]
                    onDateChanged(selectedYear, newMonth, selectedDay)
                },
                itemToString = {
                    if (isLunarMode) {
                        if (it < 0) {
                            // 闰月显示
                            LunarCalendarHelper.getLunarMonthName(-it, true)
                        } else {
                            // 正常月份显示
                            LunarCalendarHelper.getLunarMonthName(it, false)
                        }
                    } else {
                        "${it}月"
                    }
                },
                modifier = Modifier.weight(1f),
                surfaceColor = surfaceColor
            )

            WheelPicker(
                items = days,
                initialIndex = days.indexOf(selectedDay.coerceIn(1, daysInMonth)).coerceAtLeast(0),
                onIndexChanged = { index ->
                    val newDay = days[index]
                    onDateChanged(selectedYear, selectedMonth, newDay)
                },
                itemToString = { if (isLunarMode) LunarCalendarHelper.getDayName(it) else "${it}日" },
                modifier = Modifier.weight(1f),
                surfaceColor = surfaceColor
            )
        }

        // 选中项高亮背景
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )

        // 顶部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<Int>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    itemToString: (Int) -> String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") surfaceColor: Color = MaterialTheme.colorScheme.surface
) {
    val scope = rememberCoroutineScope()
    val itemHeight = 44.dp
    val visibleItemsCount = 3
    val totalHeight = itemHeight * visibleItemsCount

    // 添加空白项以便选中项居中，但限制滑动范围
    val paddedItems = remember(items) { listOf(-1) + items + listOf(-1) }

    val safeInitialIndex = initialIndex.coerceIn(0, items.size - 1)
    
    // 保持 LazyListState 不变，避免频繁重建
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
    
    // 使用 snapping 行为，让滑动自动吸附到最近的项
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // 选中索引状态
    var selectedIndex by remember { mutableStateOf(safeInitialIndex) }
    
    // 记录上一次通知的索引，避免重复通知
    var lastNotifiedIndex by remember { mutableStateOf(safeInitialIndex) }
    
    // 记录上一次的 items，用于检测 items 是否改变
    var previousItems by remember { mutableStateOf(items) }
    
    // 实时计算当前滑动位置对应的索引（用于UI高亮显示）
    val currentScrollIndex by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            // 计算中心项（考虑偏移量）
            val itemHeightPx = 132 // 44dp * 3 density
            val shouldSelectNext = firstVisibleOffset > itemHeightPx / 2
            val centerVisibleIndex = if (shouldSelectNext) firstVisibleIndex + 2 else firstVisibleIndex + 1
            // 减去填充项得到实际索引
            (centerVisibleIndex - 1).coerceIn(0, items.size - 1)
        }
    }

    // 当 items 改变时（切换 Tab），立即定位到正确位置，不使用动画
    LaunchedEffect(items) {
        if (items != previousItems) {
            val targetIndex = initialIndex.coerceIn(0, items.size - 1)
            selectedIndex = targetIndex
            lastNotifiedIndex = targetIndex
            // 使用 scrollToItem 而不是 animateScrollToItem，避免滚动动画
            listState.scrollToItem(targetIndex)
            previousItems = items
        }
    }
    
    // 当 initialIndex 改变时（items 不变的情况下），需要更新位置
    LaunchedEffect(initialIndex) {
        if (items == previousItems) {
            val targetIndex = initialIndex.coerceIn(0, items.size - 1)
            if (targetIndex != selectedIndex) {
                selectedIndex = targetIndex
                lastNotifiedIndex = targetIndex
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // 监听滑动状态，当滑动停止时自动更新选中项并通知父组件
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 获取当前可见的第一项索引和偏移量
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            
            // 计算应该选中的项（考虑偏移量，超过一半就选下一个）
            val itemHeightPx = 132 // 44dp * 3 density
            val shouldSelectNext = firstVisibleOffset > itemHeightPx / 2
            val centerVisibleIndex = if (shouldSelectNext) firstVisibleIndex + 2 else firstVisibleIndex + 1

            // 计算实际的索引（减去填充项）
            val actualIndex = centerVisibleIndex - 1

            // 确保索引在有效范围内
            val clampedIndex = actualIndex.coerceIn(0, items.size - 1)

            // 更新选中状态
            selectedIndex = clampedIndex
            
            // 只有当索引真正改变时才通知父组件，避免重复回调
            if (clampedIndex != lastNotifiedIndex) {
                lastNotifiedIndex = clampedIndex
                onIndexChanged(clampedIndex)
            }

            // 只有当偏移量不为0时才需要滚动对齐，使用 scrollToItem 避免动画
            if (firstVisibleOffset != 0) {
                scope.launch {
                    listState.scrollToItem(clampedIndex)
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
            userScrollEnabled = true,
            flingBehavior = flingBehavior // 使用 snapping 行为
        ) {
            items(paddedItems.size) { index ->
                val item = paddedItems[index]
                val actualIndex = index - 1
                val isVisible = item != -1

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable(enabled = isVisible) {
                            if (isVisible && actualIndex in items.indices && actualIndex != selectedIndex) {
                                // 立即更新选中状态
                                selectedIndex = actualIndex
                                
                                // 通知父组件
                                if (actualIndex != lastNotifiedIndex) {
                                    lastNotifiedIndex = actualIndex
                                    onIndexChanged(actualIndex)
                                }
                                
                                // 滚动到点击的位置
                                scope.launch {
                                    listState.animateScrollToItem(actualIndex)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVisible) {
                        // 始终使用 currentScrollIndex 来显示高亮
                        // 这样无论滑动中还是停止后，高亮都会跟随当前滚动位置
                        // 避免滑动停止时出现"弹回"的视觉效果
                        val shouldHighlight = actualIndex == currentScrollIndex
                        
                        Text(
                            text = itemToString(item),
                            fontSize = if (shouldHighlight) 17.sp else 14.sp,
                            fontWeight = if (shouldHighlight) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (shouldHighlight)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}
