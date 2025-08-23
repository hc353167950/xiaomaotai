package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.example.xiaomaotai.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MahjongScoreScreen(
    dataManager: DataManager,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var winnerPosition by remember { mutableStateOf<String?>(null) }
    var winnerFan by remember { mutableStateOf("") }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFanInfoDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var calculationResult by remember { mutableStateOf<Pair<CalculationDetail, FinalAmounts>?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var showErrorToast by remember { mutableStateOf(false) }
    var errorToastMessage by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mahjongDataManager = remember { MahjongDataManager(context) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 各方位数据
    var upData by remember { mutableStateOf(PositionData()) }
    var downData by remember { mutableStateOf(PositionData()) }
    var leftData by remember { mutableStateOf(PositionData()) }
    var rightData by remember { mutableStateOf(PositionData()) }
    
    // 输入框状态
    var upHongzhong by remember { mutableStateOf("") }
    var upBonusFan by remember { mutableStateOf("") }
    var upFan by remember { mutableStateOf("") }
    var downHongzhong by remember { mutableStateOf("") }
    var downBonusFan by remember { mutableStateOf("") }
    var downFan by remember { mutableStateOf("") }
    var leftHongzhong by remember { mutableStateOf("") }
    var leftBonusFan by remember { mutableStateOf("") }
    var leftFan by remember { mutableStateOf("") }
    var rightHongzhong by remember { mutableStateOf("") }
    var rightBonusFan by remember { mutableStateOf("") }
    var rightFan by remember { mutableStateOf("") }
    
    // 更新数据的函数
    fun updatePositionData() {
        upData = PositionData(
            fan = upFan.toIntOrNull() ?: 0,
            hongzhong = upHongzhong.toIntOrNull() ?: 0,
            bonusFan = upBonusFan.toDoubleOrNull() ?: 0.0
        )
        downData = PositionData(
            fan = downFan.toIntOrNull() ?: 0,
            hongzhong = downHongzhong.toIntOrNull() ?: 0,
            bonusFan = downBonusFan.toDoubleOrNull() ?: 0.0
        )
        leftData = PositionData(
            fan = leftFan.toIntOrNull() ?: 0,
            hongzhong = leftHongzhong.toIntOrNull() ?: 0,
            bonusFan = leftBonusFan.toDoubleOrNull() ?: 0.0
        )
        rightData = PositionData(
            fan = rightFan.toIntOrNull() ?: 0,
            hongzhong = rightHongzhong.toIntOrNull() ?: 0,
            bonusFan = rightBonusFan.toDoubleOrNull() ?: 0.0
        )
    }
    
    // 清空所有数据
    fun clearAllData() {
        winnerPosition = null
        winnerFan = ""
        upHongzhong = ""
        upBonusFan = ""
        upFan = ""
        downHongzhong = ""
        downBonusFan = ""
        downFan = ""
        leftHongzhong = ""
        leftBonusFan = ""
        leftFan = ""
        rightHongzhong = ""
        rightBonusFan = ""
        rightFan = ""
        // 清除焦点和关闭键盘
        focusManager.clearFocus()
        keyboardController?.hide()
        errorMessage = null
        calculationResult = null
    }
    
    // 设置胡牌方位
    fun setWinnerPosition(position: String) {
        if (winnerPosition == position) {
            // 取消胡牌状态
            winnerPosition = null
            winnerFan = ""
        } else {
            // 设置新的胡牌方位
            winnerPosition = position
            // 清空原胡牌方的番数
            winnerFan = ""
            // 清空其他方的番数输入
            if (position != "up") upFan = ""
            if (position != "down") downFan = ""
            if (position != "left") leftFan = ""
            if (position != "right") rightFan = ""
            // 清空新胡牌方的奖金番数
            when (position) {
                "up" -> upBonusFan = ""
                "down" -> downBonusFan = ""
                "left" -> leftBonusFan = ""
                "right" -> rightBonusFan = ""
            }
        }
    }
    
    // 验证输入
    fun validateInput(): String? {
        if (winnerPosition == null) {
            return "请选择胡牌方位"
        }
        
        val winnerFanValue = winnerFan.toDoubleOrNull()
        if (winnerFanValue == null) {
            return "请输入有效的胡牌番数"
        }
        
        updatePositionData()
        val positionData = mapOf(
            "up" to upData, "down" to downData, "left" to leftData, "right" to rightData
        )
        
        return MahjongCalculator.validateInput(winnerPosition, winnerFanValue, positionData)
    }
    
    // 计算并保存
    fun calculateAndSave() {
        val validationError = validateInput()
        if (validationError != null) {
            // 显示错误Toast
            errorToastMessage = validationError
            showErrorToast = true
            scope.launch {
                delay(2000)
                showErrorToast = false
            }
            return
        }
        
        isCalculating = true
        errorMessage = null
        
        scope.launch {
            try {
                updatePositionData()
                
                val positionDataMap = mapOf(
                    "up" to upData,
                    "down" to downData,
                    "left" to leftData,
                    "right" to rightData
                )
                
                val result = MahjongCalculator.calculateScore(
                    winnerPosition = winnerPosition!!,
                    winnerFan = winnerFan.toDouble(),
                    positionData = positionDataMap
                )
                
                // 不在页面上显示结果，只在弹窗中显示
                // calculationResult = result
                
                // 检查用户登录状态
                val currentUserId = if (dataManager.isLoggedIn()) dataManager.getCurrentUserId() else null
                android.util.Log.d("MahjongScore", "用户登录状态: ${dataManager.isLoggedIn()}, 用户ID: $currentUserId")
                
                // 保存到数据库（自动包含云端同步）
                val saveResult = mahjongDataManager.saveMahjongScore(
                    winnerPosition = winnerPosition!!,
                    winnerFan = winnerFan.toDouble(),
                    positionData = positionDataMap,
                    calculationDetail = result.first.toString(),
                    finalAmounts = result.second,
                    userId = currentUserId
                )
                
                // 检查保存结果
                if (saveResult <= 0) {
                    throw Exception("数据保存失败")
                }
                
                android.util.Log.d("MahjongScore", "数据保存成功，ID: $saveResult")
                
                // 直接显示计算结果弹窗，不显示成功Toast
                calculationResult = result
                showResultDialog = true
                
            } catch (e: Exception) {
                // 显示错误Toast
                errorToastMessage = "计算失败: ${e.message}"
                showErrorToast = true
                scope.launch {
                    delay(2000)
                    showErrorToast = false
                }
            } finally {
                isCalculating = false
            }
        }
    }
    
    Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding() // 添加键盘避让
        .padding(12.dp)
        .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            // 点击空白区域清空焦点并关闭键盘
            focusManager.clearFocus()
            keyboardController?.hide()
        },
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    // 顶部标题栏：感叹号在左侧，标题居中，历史入口在右上角
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 左侧感叹号图标
        IconButton(
            onClick = { showFanInfoDialog = true },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "奖金番数说明",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "麻将计分",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        TextButton(
            onClick = onNavigateToHistory,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text("历史记录")
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // 四方位布局 - 顶部对家；中间：上家|按钮|下家；底部：雀神
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部：对家（标题下面）
        HorizontalPositionCard(
            position = "right",
            displayName = "对家",
            isWinner = winnerPosition == "right",
            hongzhongValue = rightHongzhong,
            bonusFanValue = rightBonusFan,
            fanValue = if (winnerPosition == "right") winnerFan else "",
            onHongzhongChange = { rightHongzhong = it },
            onBonusFanChange = { if (winnerPosition != "right") rightBonusFan = it },
            onFanChange = { if (winnerPosition == "right") winnerFan = it },
            onWinnerClick = { setWinnerPosition("right") },
            finalAmount = calculationResult?.second?.right
        )

        // 中间行：左=上家 | 中=开始按钮 | 右=下家
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // 上家（左侧）
            PositionCard(
                position = "up",
                displayName = "上家",
                isWinner = winnerPosition == "up",
                hongzhongValue = upHongzhong,
                bonusFanValue = upBonusFan,
                fanValue = if (winnerPosition == "up") winnerFan else "",
                onHongzhongChange = { upHongzhong = it },
                onBonusFanChange = { if (winnerPosition != "up") upBonusFan = it },
                onFanChange = { if (winnerPosition == "up") winnerFan = it },
                onWinnerClick = { setWinnerPosition("up") },
                finalAmount = calculationResult?.second?.up,
                modifier = Modifier
                    .width(105.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (-12).dp)
            )

            // 开始按钮（中间）- 保持居中不变
            Button(
                onClick = { calculateAndSave() },
                modifier = Modifier
                    .size(85.dp)
                    .align(Alignment.Center),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "开始\n计算",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 下家（右侧）
            PositionCard(
                position = "left",
                displayName = "下家",
                isWinner = winnerPosition == "left",
                hongzhongValue = leftHongzhong,
                bonusFanValue = leftBonusFan,
                fanValue = if (winnerPosition == "left") winnerFan else "",
                onHongzhongChange = { leftHongzhong = it },
                onBonusFanChange = { if (winnerPosition != "left") leftBonusFan = it },
                onFanChange = { if (winnerPosition == "left") winnerFan = it },
                onWinnerClick = { setWinnerPosition("left") },
                finalAmount = calculationResult?.second?.left,
                modifier = Modifier
                    .width(105.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
            )
        }

        // 底部：雀神
        HorizontalPositionCard(
            position = "down",
            displayName = "雀神",
            isWinner = winnerPosition == "down",
            hongzhongValue = downHongzhong,
            bonusFanValue = downBonusFan,
            fanValue = if (winnerPosition == "down") winnerFan else "",
            onHongzhongChange = { downHongzhong = it },
            onBonusFanChange = { if (winnerPosition != "down") downBonusFan = it },
            onFanChange = { if (winnerPosition == "down") winnerFan = it },
            onWinnerClick = { setWinnerPosition("down") },
            finalAmount = calculationResult?.second?.down
        )
    }
    
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 底部按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 使用规则按钮
            OutlinedButton(
                onClick = { showRulesDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "使用规则", 
                    fontSize = 13.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            
            // 重置按钮
            OutlinedButton(
                onClick = { 
                    // 恢复到完全初始状态
                    clearAllData()
                    calculationResult = null
                    showResultDialog = false
                    errorMessage = null
                    showToast = false
                    showErrorToast = false
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "重置", 
                    fontSize = 13.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
        

    }
    
    // 成功Toast显示
    if (showToast) {
        Dialog(
            onDismissRequest = { 
                showToast = false
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showToast = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = toastMessage,
                        modifier = Modifier.padding(24.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
    
    // 错误Toast显示
    if (showErrorToast) {
        Dialog(
            onDismissRequest = { 
                showErrorToast = false
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showErrorToast = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = errorToastMessage,
                        modifier = Modifier.padding(24.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
    
    // 规则说明对话框
    if (showRulesDialog) {
        RulesDialog(
            onDismiss = { showRulesDialog = false }
        )
    }
    
    // 奖金番数说明对话框
    if (showFanInfoDialog) {
        FanInfoDialog(
            onDismiss = { showFanInfoDialog = false }
        )
    }
    
    // 计算结果对话框
    if (showResultDialog && calculationResult != null) {
        ResultDialog(
            calculationDetail = calculationResult!!.first,
            finalAmounts = calculationResult!!.second,
            onDismiss = { 
                showResultDialog = false
                // 关闭结果弹窗后恢复默认状态并清空所有输入
                calculationResult = null
                errorMessage = null
                showToast = false
                showErrorToast = false
                // 清空所有输入和选择状态
                clearAllData()
            }
        )
    }
}



@Composable
fun HorizontalPositionCard(
    position: String,
    displayName: String,
    isWinner: Boolean,
    hongzhongValue: String,
    bonusFanValue: String,
    fanValue: String,
    onHongzhongChange: (String) -> Unit,
    onBonusFanChange: (String) -> Unit,
    onFanChange: (String) -> Unit,
    onWinnerClick: () -> Unit,
    finalAmount: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWinner) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：方位名称和胡牌按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Button(
                    onClick = onWinnerClick,
                    modifier = Modifier
                        .width(80.dp)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWinner) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = if (isWinner) "胡牌" else "胡牌",
                        fontSize = 10.sp
                    )
                }
            }
            
            // 中间：输入框区域
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第一个输入框：奖金番（非胡牌方）或番数（胡牌方）
                OutlinedTextField(
                    value = if (isWinner) fanValue else bonusFanValue,
                    onValueChange = if (isWinner) onFanChange else onBonusFanChange,
                    label = { Text(if (isWinner) "番数" else "奖金番", fontSize = 10.sp) },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    singleLine = true
                )
                
                // 第二个输入框：红中数输入（位置保持不变）
                OutlinedTextField(
                    value = hongzhongValue,
                    onValueChange = onHongzhongChange,
                    label = { Text("红中", fontSize = 10.sp) },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    singleLine = true
                )
            }
            
            // 右侧：最终金额显示
            finalAmount?.let { amount ->
                Text(
                    text = if (amount > 0) "+${amount}元" 
                          else if (amount < 0) "${amount}元" 
                          else "0元",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        amount > 0 -> Color(0xFF4CAF50) // 绿色表示赢
                        amount < 0 -> Color(0xFFF44336) // 红色表示输
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PositionCard(
    position: String,
    displayName: String,
    isWinner: Boolean,
    hongzhongValue: String,
    bonusFanValue: String,
    fanValue: String,
    onHongzhongChange: (String) -> Unit,
    onBonusFanChange: (String) -> Unit,
    onFanChange: (String) -> Unit,
    onWinnerClick: () -> Unit,
    finalAmount: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWinner) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .heightIn(min = 200.dp), // 增加最小高度确保金额显示区域
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp) // 增加间距
        ) {
            // 胡牌按钮
            Button(
                onClick = onWinnerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWinner) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = if (isWinner) "胡牌" else "胡牌",
                    fontSize = 10.sp
                )
            }
            
            // 方位名称
            Text(
                text = displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            // 第一个输入框：奖金番（非胡牌方）或番数（胡牌方）
            OutlinedTextField(
                value = if (isWinner) fanValue else bonusFanValue,
                onValueChange = if (isWinner) onFanChange else onBonusFanChange,
                label = { Text(if (isWinner) "番数" else "奖金番", fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                singleLine = true
            )
            
            // 第二个输入框：红中数输入（位置保持不变）
            OutlinedTextField(
                value = hongzhongValue,
                onValueChange = onHongzhongChange,
                label = { Text("红中", fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                singleLine = true
            )
            
            // 最终金额显示
            finalAmount?.let { amount ->
                Text(
                    text = if (amount > 0) "+${amount}元" 
                          else if (amount < 0) "${amount}元" 
                          else "0元",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        amount > 0 -> Color(0xFF4CAF50) // 绿色表示赢
                        amount < 0 -> Color(0xFFF44336) // 红色表示输
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
