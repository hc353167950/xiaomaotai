package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MahjongHistoryScreen(
    dataManager: DataManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mahjongDataManager = remember { MahjongDataManager(context) }
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    
    // 处理返回按键，确保返回到上一页而不是退出APP
    BackHandler {
        onNavigateBack()
    }
    
    var scores by remember { mutableStateOf<List<MahjongScoreRecord>>(emptyList()) }
    var statistics by remember { mutableStateOf<MahjongStatistics?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableStateOf<MahjongScoreRecord?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var scoreToDelete by remember { mutableStateOf<MahjongScoreRecord?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // 加载数据
    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                val userId = dataManager.getCurrentUser()?.id
                scores = mahjongDataManager.getMahjongScores(userId)
                statistics = mahjongDataManager.getMahjongStatistics(userId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }
    
    // 云端数据同步
    fun syncFromCloud() {
        val userId = dataManager.getCurrentUser()?.id
        if (userId != null) {
            scope.launch {
                isSyncing = true
                try {
                    val success = mahjongDataManager.syncFromCloud(userId)
                    if (success) {
                        // 同步成功后重新加载数据
                        loadData()
                    }
                } catch (e: Exception) {
                    // 处理同步错误
                } finally {
                    isSyncing = false
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadData()
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        TopAppBar(
            title = { Text("麻将计分历史") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (scores.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "暂无计分记录",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开始第一局麻将计分吧！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 统计信息卡片
                statistics?.let { stats ->
                    item {
                        StatisticsCard(statistics = stats)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 历史记录列表
                items(scores) { score ->
                    ScoreHistoryItem(
                        score = score,
                        json = json,
                        onClick = { showDetailDialog = true },
                        onDelete = {
                            scope.launch {
                                mahjongDataManager.deleteMahjongScore(score.id.toInt())
                                loadData()
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 清空所有记录确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空所有麻将计分记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val userId = dataManager.getCurrentUser()?.id
                            mahjongDataManager.clearAllMahjongScores(userId)
                            showClearAllDialog = false
                            loadData()
                        }
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 详情对话框
    if (showDetailDialog && selectedScore != null) {
        ScoreDetailDialog(
            score = selectedScore!!,
            json = json,
            onDismiss = {
                showDetailDialog = false
                selectedScore = null
            }
        )
    }
}

@Composable
private fun StatisticsCard(
    statistics: MahjongStatistics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "统计信息",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem("总局数", statistics.totalGames.toString())
                StatisticItem("胜局", statistics.winCount.toString())
                StatisticItem("败局", statistics.loseCount.toString())
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem("总赢", "+${statistics.totalWinAmount}元")
                StatisticItem("总输", "-${statistics.totalLoseAmount}元")
                StatisticItem(
                    "净收益", 
                    when {
                        statistics?.netAmount?.let { it > 0 } == true -> "+${statistics?.netAmount}元"
                        statistics?.netAmount?.let { it < 0 } == true -> "${statistics?.netAmount}元"
                        else -> "0元"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreHistoryItem(
    score: MahjongScoreRecord,
    json: Json,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val finalAmounts = remember(score.finalAmounts) {
        score.finalAmounts
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 时间和胡牌信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(score.recordTime)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MahjongCalculator.getPositionDisplayName(score.winnerPosition)}胡牌 ${score.winnerFan}番",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 各方位金额
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AmountItem("对家", finalAmounts.up)
                AmountItem("上家", finalAmounts.left)
                AmountItem("下家", finalAmounts.right)
                AmountItem("雀神", finalAmounts.down)
            }
        }
    }
}

@Composable
private fun AmountItem(
    position: String,
    amount: Double
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                amount > 0 -> "+${amount}"
                amount < 0 -> "${amount}"
                else -> "0"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                amount > 0 -> Color(0xFF4CAF50)
                amount < 0 -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = position,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreDetailDialog(
    score: MahjongScoreRecord,
    json: Json,
    onDismiss: () -> Unit
) {
    val calculationDetail = remember(score.calculationDetail) {
        try {
            Json.decodeFromString<CalculationDetail>(score.calculationDetail)
        } catch (e: Exception) {
            CalculationDetail()
        }
    }
    
    val finalAmounts = remember(score.finalAmounts) {
        score.finalAmounts
    }
    
    ResultDialog(
        calculationDetail = calculationDetail,
        finalAmounts = finalAmounts,
        onDismiss = onDismiss
    )
}
