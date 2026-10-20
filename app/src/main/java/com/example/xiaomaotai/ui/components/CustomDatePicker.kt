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
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 滚轮日期选择器。
 *
 * 视觉：三个滚轮 + 中间高亮带 + 上下渐隐遮罩。
 * 高亮带用主色淡底 + 细边，让"当前选中"一眼可见。
 */
@Composable
fun CustomDatePickerContent(
    selectedTab: Int,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onDateChanged: (Int, Int, Int) -> Unit
) {
    val years = (1901..2099).toList()

    val isLunarMode = selectedTab == 1 || selectedTab == 3
    val isIgnoreYearMode = selectedTab == 2 || selectedTab == 3

    val months = remember(selectedYear, selectedTab) {
        if (isLunarMode) {
            LunarCalendarHelper.getLunarMonthsInYear(selectedYear)
                .map { (month, isLeap) -> if (isLeap) -month else month }
        } else {
            (1..12).toList()
        }
    }

    val daysInMonth = remember(selectedYear, selectedMonth, selectedTab) {
        if (isLunarMode) {
            val isLeap = selectedMonth < 0
            val actualMonth = kotlin.math.abs(selectedMonth)
            LunarCalendarHelper.getLunarMonthDays(selectedYear, actualMonth, isLeap)
        } else {
            java.time.YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
        }
    }
    val days = (1..daysInMonth).toList()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val rowHeight = 44.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * 3)
            .clip(Shape.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isIgnoreYearMode) {
                WheelPicker(
                    items = years,
                    initialIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                    onIndexChanged = { index ->
                        onDateChanged(years[index], selectedMonth, selectedDay)
                    },
                    itemToString = { if (isLunarMode) LunarCalendarHelper.getYearName(it) else "${it}" },
                    modifier = Modifier.weight(1.1f),
                    surfaceColor = surfaceColor
                )
            }

            WheelPicker(
                items = months,
                initialIndex = run {
                    val index = months.indexOf(selectedMonth)
                    if (index >= 0) index
                    else if (isLunarMode) {
                        if (selectedMonth < 0) {
                            months.indexOf(selectedMonth).takeIf { it >= 0 } ?: (months.size - 1)
                        } else {
                            months.indexOfFirst { it == selectedMonth || it == -selectedMonth }
                                .takeIf { it >= 0 } ?: (months.size - 1).coerceAtLeast(0)
                        }
                    } else {
                        (selectedMonth - 1).coerceIn(0, months.size - 1)
                    }
                },
                onIndexChanged = { index ->
                    onDateChanged(selectedYear, months[index], selectedDay)
                },
                itemToString = {
                    if (isLunarMode) {
                        if (it < 0) LunarCalendarHelper.getLunarMonthName(-it, true)
                        else LunarCalendarHelper.getLunarMonthName(it, false)
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
                    onDateChanged(selectedYear, selectedMonth, days[index])
                },
                itemToString = {
                    if (isLunarMode) LunarCalendarHelper.getDayName(it) else "${it}日"
                },
                modifier = Modifier.weight(1f),
                surfaceColor = surfaceColor
            )
        }

        // 选中高亮带：淡主色 + 细边，跨过三个滚轮
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = Space.xs)
                .clip(Shape.small)
                .background(primary.copy(alpha = 0.08f))
        )

        // 顶部渐隐
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(rowHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )
        // 底部渐隐
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(rowHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                    )
                )
        )
    }
}

/**
 * 单列滚轮。
 *
 * 用 3 项可视窗口 + snap 吸附；选中项放大加粗，其余降透明。
 */
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

    val paddedItems = remember(items) { listOf(-1) + items + listOf(-1) }

    val safeInitialIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    var selectedIndex by remember { mutableStateOf(safeInitialIndex) }
    var lastNotifiedIndex by remember { mutableStateOf(safeInitialIndex) }
    var previousItems by remember { mutableStateOf(items) }

    val itemsSize = items.size
    val currentScrollIndex by remember(itemsSize) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            val itemHeightPx = 132
            val shouldSelectNext = firstVisibleOffset > itemHeightPx / 2
            val centerVisibleIndex = if (shouldSelectNext) firstVisibleIndex + 2 else firstVisibleIndex + 1
            (centerVisibleIndex - 1).coerceIn(0, (itemsSize - 1).coerceAtLeast(0))
        }
    }

    // items 变化时（切 Tab）立即定位
    LaunchedEffect(items) {
        if (items != previousItems) {
            val targetIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            selectedIndex = targetIndex
            lastNotifiedIndex = targetIndex
            listState.scrollToItem(targetIndex)
            previousItems = items
        }
    }

    // initialIndex 变化时更新位置
    LaunchedEffect(initialIndex) {
        if (items == previousItems) {
            val targetIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            if (targetIndex != selectedIndex) {
                selectedIndex = targetIndex
                lastNotifiedIndex = targetIndex
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // 滑动停止后吸附并回调
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            val itemHeightPx = 132
            val shouldSelectNext = firstVisibleOffset > itemHeightPx / 2
            val centerVisibleIndex = if (shouldSelectNext) firstVisibleIndex + 2 else firstVisibleIndex + 1
            val actualIndex = centerVisibleIndex - 1
            val clampedIndex = actualIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

            selectedIndex = clampedIndex
            if (clampedIndex != lastNotifiedIndex) {
                lastNotifiedIndex = clampedIndex
                onIndexChanged(clampedIndex)
            }

            if (firstVisibleOffset != 0) {
                scope.launch { listState.scrollToItem(clampedIndex) }
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * 3)
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 0.dp),
            userScrollEnabled = true,
            flingBehavior = flingBehavior
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
                                selectedIndex = actualIndex
                                if (actualIndex != lastNotifiedIndex) {
                                    lastNotifiedIndex = actualIndex
                                    onIndexChanged(actualIndex)
                                }
                                scope.launch { listState.animateScrollToItem(actualIndex) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVisible) {
                        val shouldHighlight = actualIndex == currentScrollIndex
                        Text(
                            text = itemToString(item),
                            fontSize = if (shouldHighlight) 18.sp else 14.sp,
                            fontWeight = if (shouldHighlight) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (shouldHighlight)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                            letterSpacing = if (shouldHighlight) (-0.3).sp else 0.sp
                        )
                    }
                }
            }
        }
    }
}
