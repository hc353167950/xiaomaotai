package com.example.xiaomaotai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.example.xiaomaotai.DataManager
import com.example.xiaomaotai.Event
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortScreen(
    dataManager: DataManager,
    initialEvents: List<Event>, // 接收首页传过来的事件列表
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(initialEvents) } // 直接使用传入的初始事件
    var homePageEvents by remember { mutableStateOf(initialEvents) } // 保存首页传过来的顺序
    var isSaving by remember { mutableStateOf(false) }
    
    // 排序状态：0=默认，1=正序，2=倒序
    var currentSortType by remember { mutableStateOf(0) }
    
    // 最简化的拖拽状态
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedTargetIndex by remember { mutableStateOf<Int?>(null) }
    
    // 添加列表状态管理，确保排序后显示顶部
    val listState = rememberLazyListState()

    // 每次进入都重置为默认状态，显示首页传过来的顺序
    LaunchedEffect(initialEvents) {
        events = initialEvents
        homePageEvents = initialEvents // 保存首页传过来的顺序
        currentSortType = 0 // 每次进入都重置为默认状态
    }

    // 保存函数 - 提取公共保存逻辑
    fun saveAndExit() {
        scope.launch {
            isSaving = true
            try {
                // 使用较大的基数确保排序后的事件仍在前面
                val baseSortOrder = 1000
                val updated = events.mapIndexed { index, e -> 
                    e.copy(sortOrder = baseSortOrder - index) 
                }
                updated.forEach { event ->
                    dataManager.updateEvent(event)
                }
                onDone()
            } finally {
                isSaving = false
            }
        }
    }

    // 手势返回处理 - 与保存按钮效果一致
    BackHandler {
        if (!isSaving) {
            saveAndExit()
        }
    }

    // 排序函数 - 保持当前视图位置
    fun sortDefault() {
        val currentFirstVisibleIndex = listState.firstVisibleItemIndex
        val currentScrollOffset = listState.firstVisibleItemScrollOffset
        
        // 恢复为首页传过来的顺序
        events = homePageEvents
        currentSortType = 0
        
        // 只保持视图位置，不保存到数据库
        scope.launch {
            listState.scrollToItem(currentFirstVisibleIndex, currentScrollOffset)
        }
    }
    
    fun sortAscending() {
        val currentFirstVisibleIndex = listState.firstVisibleItemIndex
        val currentScrollOffset = listState.firstVisibleItemScrollOffset
        
        // 按剩余天数正序排序（最近的事件在前）
        val sortedEvents = events.sortedBy { event ->
            calculateDaysAfter(event.eventDate).second
        }
        
        // 重新分配sortOrder但不立即保存到数据库
        val baseSortOrder = 1000
        val updatedEvents = sortedEvents.mapIndexed { index, event ->
            event.copy(sortOrder = baseSortOrder - index)
        }
        events = updatedEvents
        currentSortType = 1
        
        // 只保持视图位置，不保存到数据库
        scope.launch {
            listState.scrollToItem(currentFirstVisibleIndex, currentScrollOffset)
        }
    }
    
    fun sortDescending() {
        val currentFirstVisibleIndex = listState.firstVisibleItemIndex
        val currentScrollOffset = listState.firstVisibleItemScrollOffset
        
        // 按剩余天数倒序排序（最远的事件在前）
        val sortedEvents = events.sortedByDescending { event ->
            calculateDaysAfter(event.eventDate).second
        }
        
        // 重新分配sortOrder但不立即保存到数据库
        val baseSortOrder = 1000
        val updatedEvents = sortedEvents.mapIndexed { index, event ->
            event.copy(sortOrder = baseSortOrder - index)
        }
        events = updatedEvents
        currentSortType = 2
        
        // 只保持视图位置，不保存到数据库
        scope.launch {
            listState.scrollToItem(currentFirstVisibleIndex, currentScrollOffset)
        }
    }

    // 拖拽状态更新
    fun onDragUpdate(fromIndex: Int, targetIndex: Int?) {
        draggedIndex = fromIndex
        draggedTargetIndex = targetIndex
    }
    
    // 拖拽结束处理 - 保持视图位置不变，修复顶部拖拽BUG
    fun onDragFinish(fromIndex: Int, finalTargetIndex: Int) {
        if (fromIndex != finalTargetIndex && finalTargetIndex in events.indices) {
            val currentFirstVisibleIndex = listState.firstVisibleItemIndex
            val currentScrollOffset = listState.firstVisibleItemScrollOffset
            
            val newEvents = events.toMutableList()
            val draggedEvent = newEvents.removeAt(fromIndex)
            newEvents.add(finalTargetIndex, draggedEvent)
            
            // 更新sortOrder以保持顺序 - 使用较大的基数确保排序后的事件仍在前面
            val baseSortOrder = 1000 // 使用较大的基数
            val updatedEvents = newEvents.mapIndexed { index, event ->
                event.copy(sortOrder = baseSortOrder - index) // 降序分配，第一个事件sortOrder最大
            }
            
            events = updatedEvents
            // 拖拽后保持当前排序状态，不重置为默认状态
        
            // 保持当前视图位置，避免滚动跳动，但不立即保存到数据库
            scope.launch {
                listState.scrollToItem(currentFirstVisibleIndex, currentScrollOffset)
            }
        }
        
        // 重置拖拽状态
        draggedIndex = null
        draggedTargetIndex = null
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部区域 - 简约设计
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自定义排序",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onDone() },
                        enabled = !isSaving
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { saveAndExit() },
                        enabled = !isSaving
                    ) {
                        Text("保存")
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // 说明文字
            Text(
                text = "拖动右侧把手调整顺序",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // 简洁的排序按钮 - 等比展示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 默认按钮
                TextButton(
                    onClick = { sortDefault() },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (currentSortType == 0) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "默认",
                        fontWeight = if (currentSortType == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                // 正序按钮
                TextButton(
                    onClick = { sortAscending() },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (currentSortType == 1) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "正序",
                        fontWeight = if (currentSortType == 1) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                // 倒序按钮
                TextButton(
                    onClick = { sortDescending() },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (currentSortType == 2) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "倒序",
                        fontWeight = if (currentSortType == 2) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // 列表区域 - 添加状态管理
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(
                items = events, 
                key = { _, item -> item.id }
            ) { index, event ->
                DraggableEventItem(
                    event = event,
                    index = index,
                    draggedIndex = draggedIndex,
                    draggedTargetIndex = draggedTargetIndex,
                    totalItemsCount = events.size,
                    onDragUpdate = { fromIndex, targetIndex -> 
                        onDragUpdate(fromIndex, targetIndex)
                    },
                    onDragFinish = { fromIndex, finalTargetIndex -> 
                        onDragFinish(fromIndex, finalTargetIndex)
                    }
                )
            }
        }
    }
}

@Composable
fun DraggableEventItem(
    event: Event,
    index: Int,
    draggedIndex: Int?,
    draggedTargetIndex: Int?,
    totalItemsCount: Int,
    onDragUpdate: (Int, Int?) -> Unit,
    onDragFinish: (Int, Int) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var visualDragOffset by remember { mutableStateOf(0f) } // 视觉拖拽偏移，始终跟随手势
    val density = LocalDensity.current
    val itemHeight = with(density) { (120.dp + 12.dp).toPx() }
    
    // 拖拽时的平滑放大动画 - 更慢更稳定
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging && index == draggedIndex) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow // 降低刚度，使缩放动画更平滑
        ),
        label = "dragScale"
    )
    
    // 挤让动画 - 只有其他项参与，使用更慢更平滑的动画
    val squeezeOffset by animateIntOffsetAsState(
        targetValue = when {
            index == draggedIndex -> IntOffset.Zero // 被拖拽项不参与挤让
            draggedIndex != null && draggedTargetIndex != null && draggedIndex != draggedTargetIndex -> {
                val itemHeightInt = itemHeight.roundToInt()
                when {
                    // 向下拖拽：被跨越的项向上移
                    draggedIndex < draggedTargetIndex && index > draggedIndex && index <= draggedTargetIndex -> 
                        IntOffset(0, -itemHeightInt)
                    // 向上拖拽：被跨越的项向下移
                    draggedIndex > draggedTargetIndex && index < draggedIndex && index >= draggedTargetIndex -> 
                        IntOffset(0, itemHeightInt)
                    else -> IntOffset.Zero
                }
            }
            else -> IntOffset.Zero
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, // 更柔和的阻尼
            stiffness = Spring.StiffnessMedium // 适中的刚度，保证响应性
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                if (isDragging && index == draggedIndex) {
                    // 使用视觉偏移，确保拖拽卡片始终跟随手势
                    translationY = visualDragOffset
                }
            }
            .offset {
                if (isDragging && index == draggedIndex) {
                    // 拖拽卡片不使用offset，只使用graphicsLayer的translationY
                    IntOffset.Zero
                } else {
                    // 其他项使用挤让动画
                    squeezeOffset
                }
            }
            .shadow(
                elevation = if (isDragging && index == draggedIndex) 4.dp else 0.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .zIndex(if (isDragging && index == draggedIndex) 1f else 0f)
    ) {
        EventItem(
            event = event,
            isDragMode = true,
            onEdit = { },
            onDelete = { },
            onDragMove = { _, _ -> },
            dragIndex = index,
            onDragStart = { 
                isDragging = true
                visualDragOffset = 0f // 重置视觉偏移
            },
            onDragEnd = { 
                // 直接计算最终目标位置
                val offsetInItems = visualDragOffset / itemHeight
                val finalTargetIndex = (index + kotlin.math.round(offsetInItems)).toInt()
                    .coerceIn(0, totalItemsCount - 1)
                
                isDragging = false
                visualDragOffset = 0f // 重置视觉偏移
                onDragFinish(index, finalTargetIndex)
            },
            onDrag = { change ->
                if (isDragging) {
                    // 视觉偏移始终跟随手势
                    visualDragOffset += change.y
                    
                    // 简化逻辑：直接计算目标位置，不做复杂的逐步判定
                    val offsetInItems = visualDragOffset / itemHeight
                    val currentTargetIndex = (index + kotlin.math.round(offsetInItems)).toInt()
                        .coerceIn(0, totalItemsCount - 1)
                    
                    // 直接更新目标位置，让挤让动画跟随
                    onDragUpdate(index, currentTargetIndex)
                }
            }
        )
    }
}
