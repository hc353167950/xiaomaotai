package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.xiaomaotai.*

/**
 * 规则说明对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "麻将计分规则",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                // 规则内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    RuleSection(
                        title = "基础设定",
                        content = """
                            • 基础金额：1番 = 10元，0.5番 = 5元
                            • 游戏人数：4人
                            • 红中机制：红中数量+1 = 实际倍数
                              (0个红中=1倍，1个红中=2倍，依此类推)
                        """.trimIndent()
                    )
                    
                    RuleSection(
                        title = "方位说明",
                        content = """
                            • 四个方位：雀神（自己）、上家、下家、对家
                            • 四个方位中仅有一位可以胡牌
                            • 所有输赢金额总和为0（平衡）
                        """.trimIndent()
                    )
                    
                    RuleSection(
                        title = "输入规则",
                        content = """
                            • 番数：仅胡牌方可输入，必须是0.5的倍数
                            • 红中数：只能输入非负整数
                            • 奖金番数：非胡牌方输入，必须是0.5的倍数
                            • 胡牌方不能输入奖金番数
                        """.trimIndent()
                    )
                    
                    RuleSection(
                        title = "计算逻辑",
                        content = """
                            第一层：胡牌计算
                            • 计算公式：(胡牌番数 - 奖金番) × (胡牌者红中+1) × 10 × (支付者红中+1)
                            • 每个非胡牌方都要向胡牌方支付相应金额
                            • 注意：如果胡牌番数小于奖金番，结果为负数，表示胡牌方要向该方支付
                            
                            第二层：奖金番互算
                            • 步骤1：计算奖金番差值 = 高奖金番 - 低奖金番
                            • 步骤2：奖金番高的人每番价值 = 奖金番差值 × (高奖金番方红中+1) × 10元
                            • 步骤3：奖金番低的人支付 = 每番价值 × (低奖金番方红中+1)
                            • 计算公式：(高奖金番-低奖金番) × (高奖金番方红中+1) × 10 × (低奖金番方红中+1)
                            • 注意：如果两家奖金番相等（包括都为0），则跳过该对的计算
                        """.trimIndent()
                    )
                    
                    RuleSection(
                        title = "计算示例（四家游戏）",
                        content = """
                            假设：雀神胡牌2番，红中1个
                            上家：红中0个，奖金番1
                            下家：红中2个，奖金番0.5
                            对家：红中1个，奖金番0
                            
                            第一层胡牌计算：
                            • 上家支付：(2-1)×(1+1)×10×(0+1)=20元
                            • 下家支付：(2-0.5)×(1+1)×10×(2+1)=90元
                            • 对家支付：(2-0)×(1+1)×10×(1+1)=80元
                            
                            第二层奖金番互算：
                            • 下家向上家支付：(1-0.5)×(0+1)×10×(2+1)=15元
                            • 对家向上家支付：(1-0)×(0+1)×10×(1+1)=20元
                            • 对家向下家支付：(0.5-0)×(2+1)×10×(1+1)=30元
                            
                            最终结果（总和=0）：
                            • 雀神：+190元（20+90+80）
                            • 上家：+15元（-20+15+20）
                            • 下家：-75元（-90-15+30）
                            • 对家：-130元（-80-20-30）
                        """.trimIndent()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 计算结果对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDialog(
    calculationDetail: CalculationDetail,
    finalAmounts: FinalAmounts,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "计算结果",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                // 结果内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 最终金额汇总
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
                                text = "最终结果",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            FinalAmountItem("对家", finalAmounts.up)
                            FinalAmountItem("上家", finalAmounts.left)
                            FinalAmountItem("下家", finalAmounts.right)
                            FinalAmountItem("雀神", finalAmounts.down)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 第一层胡牌计算详情
                    if (calculationDetail.firstLayer.isNotEmpty()) {
                        Text(
                            text = "第一层：胡牌计算",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        calculationDetail.firstLayer.forEach { calc ->
                            CalculationItem(
                                title = "${MahjongCalculator.getPositionDisplayName(calc.fromPosition)} → ${MahjongCalculator.getPositionDisplayName(calc.toPosition)}",
                                formula = calc.formula,
                                amount = calc.finalAmount
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 第二层奖金番互算详情
                    if (calculationDetail.secondLayer.isNotEmpty()) {
                        Text(
                            text = "第二层：奖金番互算",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        calculationDetail.secondLayer.forEach { calc ->
                            CalculationItem(
                                title = "${MahjongCalculator.getPositionDisplayName(calc.fromPosition)} → ${MahjongCalculator.getPositionDisplayName(calc.toPosition)}",
                                formula = calc.formula,
                                amount = calc.finalAmount
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = content,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun FinalAmountItem(
    positionName: String,
    amount: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = positionName,
            fontSize = 14.sp
        )
        Text(
            text = when {
                amount > 0 -> "+${amount}元"
                amount < 0 -> "${amount}元"
                else -> "0元"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                amount > 0 -> Color(0xFF4CAF50)
                amount < 0 -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun CalculationItem(
    title: String,
    formula: String,
    amount: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formula,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "转账金额：${amount}元",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (amount > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

/**
 * 奖金番数说明对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "奖金番数说明",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 番数部分
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
                                text = "番数对照表",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val fanData = listOf(
                                "回头笑" to "0.5番",
                                "胡牌" to "1番",
                                "顶杠" to "1番", 
                                "门清胡牌" to "1.5番",
                                "暗杠" to "2番",
                                "对子胡" to "3番",
                                "软霍带" to "3番",
                                "硬霍带" to "5番",
                                "清一色" to "5番",
                                "七对" to "5番",
                                "裸奔" to "7番",
                                "夹七对" to "10番"
                            )
                            
                            fanData.forEach { (name, fan) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = fan,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 牌型介绍部分
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "牌型详细介绍",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val typeDescriptions = listOf(
                                "回头笑" to "自己碰了牌，然后又摸到一张一样的，放下去",
                                "胡牌" to "碰牌后再胡",
                                "顶杠" to "自己打的牌或者别人打的牌被杠下来",
                                "门清胡牌" to "可以有回头笑和暗杠，只要没碰胡牌",
                                "暗杠" to "自己摸到四张一样的牌型",
                                "对子胡" to "碰牌后，最后两个对子胡牌",
                                "软霍带" to "碰了之后（要自己碰），又摸到这张牌胡了，或者手里有一杠，先碰了手里还留有一张，再摸牌胡了",
                                "硬霍带" to "胡牌时手里有四张一样的牌（没有暗杠下来）",
                                "清一色" to "全部是一个花色的牌型胡了（包括碰、杠都要是一个色）",
                                "七对" to "手里七个对子胡了",
                                "裸奔" to "手里只剩一张牌胡牌",
                                "夹七对" to "手里五个对子加四张一样的牌型（没有暗杠下来）"
                            )
                            
                            typeDescriptions.forEach { (name, description) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "• $name",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = description,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
