package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.xiaomaotai.DingGangConnection

/**
 * 获取方位中文名称
 */
private fun getPositionName(position: String): String {
    return when (position) {
        "up" -> "上家"
        "down" -> "雀神"
        "left" -> "下家"
        "right" -> "对家"
        else -> position
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DingGangDialog(
    dingGangConnections: List<DingGangConnection>,
    onDingGangConnectionsChange: (List<DingGangConnection>) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题栏 - 固定不滚动
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "顶杆设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(
                            onClick = { showAddDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加顶杆")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
                
                Divider()
                
                // 说明文字
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = if (dingGangConnections.isNotEmpty()) 
                                "顶杆规则说明 (${dingGangConnections.size}条记录)" 
                            else 
                                "顶杆规则说明",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 顶杆：A顶杆B，A的番数-1，B的番数+1\n• 影响胡牌计算和奖金番互算的番数差值\n• 顶杆的人给钱，被顶的人收钱",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // 顶杆列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dingGangConnections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无顶杆记录\n点击右上角 + 添加",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        dingGangConnections.forEachIndexed { index, connection ->
                            DingGangItem(
                                connection = connection,
                                onDelete = {
                                    val newList = dingGangConnections.toMutableList()
                                    newList.removeAt(index)
                                    onDingGangConnectionsChange(newList)
                                }
                            )
                        }
                    }
                }
                
                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            onDingGangConnectionsChange(emptyList())
                        }
                    ) {
                        Text("清空全部")
                    }
                    
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
    
    // 添加顶杆对话框
    if (showAddDialog) {
        AddDingGangDialog(
            onConfirm = { from, to, count ->
                val newConnection = DingGangConnection(from, to, count)
                onDingGangConnectionsChange(dingGangConnections + newConnection)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun DingGangItem(
    connection: DingGangConnection,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${getPositionName(connection.from)} 顶杆 ${getPositionName(connection.to)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "次数：${connection.count}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDingGangDialog(
    onConfirm: (String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFrom by remember { mutableStateOf<String?>(null) }
    var selectedTo by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf(1) }
    
    val positions = listOf("up", "down", "left", "right")
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "添加顶杆",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 顶杆方选择
                Text(
                    text = "顶杆方（给钱的人）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    positions.forEach { position ->
                        FilterChip(
                            onClick = { selectedFrom = position },
                            label = { Text(getPositionName(position)) },
                            selected = selectedFrom == position,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 被顶方选择
                Text(
                    text = "被顶方（收钱的人）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    positions.forEach { position ->
                        FilterChip(
                            onClick = { selectedTo = position },
                            label = { Text(getPositionName(position)) },
                            selected = selectedTo == position,
                            enabled = position != selectedFrom, // 不能选择同一个人
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 次数选择
                Text(
                    text = "顶杆次数",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (count > 1) count-- },
                        enabled = count > 1
                    ) {
                        Text("-")
                    }
                    
                    Text(
                        text = count.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedButton(
                        onClick = { if (count < 10) count++ },
                        enabled = count < 10
                    ) {
                        Text("+")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = {
                            if (selectedFrom != null && selectedTo != null) {
                                onConfirm(selectedFrom!!, selectedTo!!, count)
                            }
                        },
                        enabled = selectedFrom != null && selectedTo != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
