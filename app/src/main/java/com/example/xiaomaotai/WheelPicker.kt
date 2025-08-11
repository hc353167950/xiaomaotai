package com.example.xiaomaotai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    items: List<T>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    visibleItemCount: Int = 3,
    itemHeight: Dp = 40.dp,
    itemToString: (T) -> String = { it.toString() }
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    val centeredItemIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                -1
            } else {
                val viewportCenter = layoutInfo.viewportEndOffset / 2
                val centerItem = visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }
                centerItem?.index ?: -1
            }
        }
    }

    LaunchedEffect(centeredItemIndex) {
        if (centeredItemIndex != -1) {
            val adjustedIndex = centeredItemIndex - (visibleItemCount / 2)
            if (adjustedIndex in items.indices) {
                onIndexChanged(adjustedIndex)
            }
        }
    }
    
    // Auto-scroll to initial index after composition
    LaunchedEffect(items) {
        val targetIndex = initialIndex
        if (targetIndex >= 0) {
            lazyListState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemCount)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = lazyListState,
            flingBehavior = snapBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            val topBottomPadding = (visibleItemCount / 2)
            items(items.size + topBottomPadding * 2) { index ->
                val itemIndex = index - topBottomPadding
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (itemIndex in items.indices) {
                        val isCentered = (centeredItemIndex == index)
                        Text(
                            text = itemToString(items[itemIndex]),
                            style = textStyle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(if (isCentered) 1.0f else 0.4f)
                        )
                    }
                }
            }
        }
        // Center line indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .alpha(0.2f)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )
    }
}
