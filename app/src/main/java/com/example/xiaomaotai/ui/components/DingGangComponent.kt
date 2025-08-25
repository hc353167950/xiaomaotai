package com.example.xiaomaotai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DingGangConnection
import com.example.xiaomaotai.MahjongCalculator

/**
 * 顶杆组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DingGangComponent(
    connections: List<DingGangConnection>,
    onConnectionsChange: (List<DingGangConnection>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDingGangMode by remember { mutableStateOf(false) }
    var selectedPosition by remember { mutableStateOf<String?>(null) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "顶杆记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connections.isNotEmpty()) {
                        Text(
                            text = "${connections.size}条",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }
            
            // 展开内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                isDingGangMode = !isDingGangMode
                                selectedPosition = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDingGangMode) MaterialTheme.colorScheme.error 
                                               else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (isDingGangMode) "退出顶杆模式" else "进入顶杆模式",
                                fontSize = 12.sp
                            )
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                onConnectionsChange(emptyList())
                                isDingGangMode = false
                                selectedPosition = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                    
                    // 顶杆模式状态提示
                    if (isDingGangMode) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = if (selectedPosition == null) 
                                    "请点击顶杆方位" 
                                else 
                                    "已选择${MahjongCalculator.getPositionDisplayName(selectedPosition!!)}，请点击被顶方位",
                                modifier = Modifier.padding(8.dp),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    // 方位选择区域（仅在顶杆模式下显示）
                    if (isDingGangMode) {
                        DingGangPositionSelector(
                            selectedPosition = selectedPosition,
                            onPositionClick = { position ->
                                if (selectedPosition == null) {
                                    selectedPosition = position
                                } else if (selectedPosition == position) {
                                    selectedPosition = null
                                } else {
                                    // 创建顶杆连接
                                    val newConnection = DingGangConnection(
                                        from = selectedPosition!!,
                                        to = position,
                                        count = 1
                                    )
                                    
                                    // 检查是否已存在相同连接
                                    val existingIndex = connections.indexOfFirst { 
                                        it.from == newConnection.from && it.to == newConnection.to 
                                    }
                                    
                                    val updatedConnections = if (existingIndex >= 0) {
                                        // 增加次数
                                        connections.toMutableList().apply {
                                            this[existingIndex] = this[existingIndex].copy(
                                                count = this[existingIndex].count + 1
                                            )
                                        }
                                    } else {
                                        // 添加新连接
                                        connections + newConnection
                                    }
                                    
                                    onConnectionsChange(updatedConnections)
                                    selectedPosition = null
                                }
                            }
                        )
                    }
                    
                    // 顶杆关系列表
                    if (connections.isNotEmpty()) {
                        Text(
                            text = "当前顶杆关系：",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        connections.forEachIndexed { index, connection ->
                            DingGangConnectionItem(
                                connection = connection,
                                onCountChange = { newCount ->
                                    val updatedConnections = connections.toMutableList()
                                    if (newCount <= 0) {
                                        updatedConnections.removeAt(index)
                                    } else {
                                        updatedConnections[index] = connection.copy(count = newCount)
                                    }
                                    onConnectionsChange(updatedConnections)
                                },
                                onDelete = {
                                    val updatedConnections = connections.toMutableList()
                                    updatedConnections.removeAt(index)
                                    onConnectionsChange(updatedConnections)
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "暂无顶杆关系",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶杆方位选择器
 */
@Composable
private fun DingGangPositionSelector(
    selectedPosition: String?,
    onPositionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val positions = mapOf(
        "right" to "对家",
        "up" to "上家", 
        "left" to "下家",
        "down" to "雀神"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 对家
        PositionButton(
            position = "right",
            displayName = positions["right"]!!,
            isSelected = selectedPosition == "right",
            onClick = { onPositionClick("right") }
        )
        
        // 中间行：上家和下家
        Row(
            horizontalArrangement = Arrangement.spacedBy(60.dp)
        ) {
            PositionButton(
                position = "up",
                displayName = positions["up"]!!,
                isSelected = selectedPosition == "up",
                onClick = { onPositionClick("up") }
            )
            
            PositionButton(
                position = "left",
                displayName = positions["left"]!!,
                isSelected = selectedPosition == "left",
                onClick = { onPositionClick("left") }
            )
        }
        
        // 雀神
        PositionButton(
            position = "down",
            displayName = positions["down"]!!,
            isSelected = selectedPosition == "down",
            onClick = { onPositionClick("down") }
        )
    }
}

/**
 * 方位按钮
 */
@Composable
private fun PositionButton(
    position: String,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(60.dp, 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            text = displayName,
            fontSize = 10.sp
        )
    }
}

/**
 * 顶杆连接项
 */
@Composable
private fun DingGangConnectionItem(
    connection: DingGangConnection,
    onCountChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${MahjongCalculator.getPositionDisplayName(connection.from)} → ${MahjongCalculator.getPositionDisplayName(connection.to)}",
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onCountChange(connection.count - 1) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    text = "${connection.count}次",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                
                IconButton(
                    onClick = { onCountChange(connection.count + 1) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}