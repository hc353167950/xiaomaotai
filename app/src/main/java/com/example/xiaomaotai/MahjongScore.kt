package com.example.xiaomaotai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 辅助数据类用于解构赋值
 */
data class Tuple4<T1, T2, T3, T4>(
    val first: T1,
    val second: T2,
    val third: T3,
    val fourth: T4
)

operator fun <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4>.component1(): T1 = first
operator fun <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4>.component2(): T2 = second
operator fun <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4>.component3(): T3 = third
operator fun <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4>.component4(): T4 = fourth

/**
 * 麻将计分记录数据模型
 */
@Serializable
data class MahjongScore(
    val id: Int = 0,
    @SerialName("record_time")
    val recordTime: String = "", // 格式：2024年12月31日20时30分
    @SerialName("winner_position")
    val winnerPosition: String = "", // 胡牌方位：上、下、左、右
    @SerialName("winner_fan")
    val winnerFan: Double = 0.0, // 胡牌番数
    @SerialName("position_data")
    val positionData: String = "", // JSON格式：各方位红中数、奖金番数
    @SerialName("calculation_detail")
    val calculationDetail: String = "", // JSON格式：详细计算过程
    @SerialName("final_amounts")
    val finalAmounts: String = "", // JSON格式：各方位最终净金额
    @SerialName("user_id")
    val userId: String? = null // 用户ID，未登录时为null
)

/**
 * 方位数据
 */
@Serializable
data class PositionData(
    val fan: Int = 0, // 番数
    val hongzhong: Int = 0, // 红中数
    val bonusFan: Double = 0.0 // 奖金番数
)

/**
 * 计算详情
 */
@Serializable
data class CalculationDetail(
    val firstLayer: List<FirstLayerCalculation> = emptyList(), // 第一层胡牌计算
    val secondLayer: List<SecondLayerCalculation> = emptyList() // 第二层奖金番互算
)

/**
 * 第一层胡牌计算
 */
@Serializable
data class FirstLayerCalculation(
    val fromPosition: String, // 支付方位
    val toPosition: String, // 收取方位
    val winnerFanAmount: Double, // 胡牌方每番金额
    val payerMultiplier: Int, // 支付方红中倍数
    val finalAmount: Double, // 最终转账金额
    val formula: String // 计算公式
)

/**
 * 第二层奖金番互算
 */
@Serializable
data class SecondLayerCalculation(
    val fromPosition: String, // 支付方位
    val toPosition: String, // 收取方位
    val higherFanAmount: Double, // 高方每番金额
    val payerMultiplier: Int, // 支付方红中倍数
    val finalAmount: Double, // 最终转账金额
    val formula: String // 计算公式
)

/**
 * 最终金额
 */
@Serializable
data class FinalAmounts(
    val up: Double = 0.0, // 上方位净金额
    val down: Double = 0.0, // 下方位净金额（雀神）
    val left: Double = 0.0, // 左方位净金额
    val right: Double = 0.0 // 右方位净金额
)

/**
 * 麻将计分计算器
 */
object MahjongCalculator {
    
    /**
     * 计算麻将得分
     * @param winnerPosition 胡牌方位
     * @param winnerFan 胡牌番数
     * @param positionData 各方位数据
     * @return 计算结果
     */
    fun calculateScore(
        winnerPosition: String,
        winnerFan: Double,
        positionData: Map<String, PositionData>
    ): Pair<CalculationDetail, FinalAmounts> {
        
        val positions = listOf("up", "down", "left", "right")
        val firstLayerCalculations = mutableListOf<FirstLayerCalculation>()
        val secondLayerCalculations = mutableListOf<SecondLayerCalculation>()
        
        // 临时金额记录
        val tempAmounts = mutableMapOf<String, Double>().apply {
            positions.forEach { put(it, 0.0) }
        }
        
        // 第一层：胡牌计算
        val winnerData = positionData[winnerPosition] ?: PositionData()
        val winnerMultiplier = winnerData.hongzhong + 1
        
        positions.filter { it != winnerPosition }.forEach { position ->
            val posData = positionData[position] ?: PositionData()
            val payerMultiplier = posData.hongzhong + 1
            
            // 修正算法：(胡牌番数-奖金番) × (胡牌者红中+1) × 10 × (支付者红中+1)
            val effectiveFan = winnerFan - posData.bonusFan
            val finalAmount = effectiveFan * winnerMultiplier * 10 * payerMultiplier
            
            val formula = "(${winnerFan}-${posData.bonusFan})×${winnerMultiplier}×10×${payerMultiplier} = ${finalAmount}元"
            
            firstLayerCalculations.add(
                FirstLayerCalculation(
                    fromPosition = position,
                    toPosition = winnerPosition,
                    winnerFanAmount = effectiveFan,
                    payerMultiplier = payerMultiplier,
                    finalAmount = finalAmount,
                    formula = formula
                )
            )
            
            // 更新临时金额
            tempAmounts[position] = tempAmounts[position]!! - finalAmount
            tempAmounts[winnerPosition] = tempAmounts[winnerPosition]!! + finalAmount
        }
        
        // 第二层：奖金番互算
        val nonWinnerPositions = positions.filter { it != winnerPosition }
        for (i in nonWinnerPositions.indices) {
            for (j in i + 1 until nonWinnerPositions.size) {
                val pos1 = nonWinnerPositions[i]
                val pos2 = nonWinnerPositions[j]
                val data1 = positionData[pos1] ?: PositionData()
                val data2 = positionData[pos2] ?: PositionData()
                
                // 跳过奖金番相等的情况（包括都为0的情况）
                if (data1.bonusFan == data2.bonusFan) {
                    continue
                }
                
                val (higherPos, lowerPos, higherData, lowerData) = if (data1.bonusFan > data2.bonusFan) {
                    Tuple4(pos1, pos2, data1, data2)
                } else {
                    Tuple4(pos2, pos1, data2, data1)
                }
                
                val higherMultiplier = higherData.hongzhong + 1
                val lowerMultiplier = lowerData.hongzhong + 1
                
                // 修正算法：(高奖金番-低奖金番) × (高奖金番方红中+1) × 10 × (低奖金番方红中+1)
                val fanDifference = higherData.bonusFan - lowerData.bonusFan
                val finalAmount = fanDifference * higherMultiplier * 10 * lowerMultiplier
                
                val formula = "(${higherData.bonusFan}-${lowerData.bonusFan})×${higherMultiplier}×10×${lowerMultiplier} = ${finalAmount}元"
                
                secondLayerCalculations.add(
                    SecondLayerCalculation(
                        fromPosition = lowerPos,
                        toPosition = higherPos,
                        higherFanAmount = fanDifference,
                        payerMultiplier = lowerMultiplier,
                        finalAmount = finalAmount,
                        formula = formula
                    )
                )
                
                // 更新临时金额
                tempAmounts[lowerPos] = tempAmounts[lowerPos]!! - finalAmount
                tempAmounts[higherPos] = tempAmounts[higherPos]!! + finalAmount
            }
        }
        
        val calculationDetail = CalculationDetail(firstLayerCalculations, secondLayerCalculations)
        val finalAmounts = FinalAmounts(
            up = tempAmounts["up"] ?: 0.0,
            down = tempAmounts["down"] ?: 0.0,
            left = tempAmounts["left"] ?: 0.0,
            right = tempAmounts["right"] ?: 0.0
        )
        
        return Pair(calculationDetail, finalAmounts)
    }
    
    /**
     * 验证输入数据
     */
    fun validateInput(
        winnerPosition: String?,
        winnerFan: Double?,
        positionData: Map<String, PositionData>
    ): String? {
        // 检查是否有胡牌方
        if (winnerPosition.isNullOrBlank()) {
            return "请选择胡牌方位"
        }
        
        // 检查胡牌方番数
        if (winnerFan == null) {
            return "请输入胡牌番数"
        }
        
        // 检查番数格式（必须是0.5的倍数）
        if (winnerFan < 0 || (winnerFan * 2) % 1 != 0.0) {
            return "胡牌番数必须是0.5的倍数"
        }
        
        // 检查各方位数据
        positionData.forEach { (position, data) ->
            // 检查红中数（必须是非负整数）
            if (data.hongzhong < 0) {
                return "${getPositionName(position)}的红中数必须是非负整数"
            }
            
            // 检查奖金番数格式（必须是0.5的倍数）
            if (data.bonusFan < 0 || (data.bonusFan * 2) % 1 != 0.0) {
                return "${getPositionName(position)}的奖金番数必须是0.5的倍数"
            }
            
            // 胡牌方不能有奖金番数
            if (position == winnerPosition && data.bonusFan > 0) {
                return "胡牌方不能输入奖金番数"
            }
        }
        
        return null // 验证通过
    }
    
    /**
     * 获取方位中文名称
     */
    fun getPositionName(position: String): String {
        return when (position) {
            "up" -> "上家"
            "down" -> "雀神"
            "left" -> "下家"
            "right" -> "对家"
            else -> position
        }
    }
    
    /**
     * 获取方位显示名称
     */
    fun getPositionDisplayName(position: String): String {
        return when (position) {
            "up" -> "上家"
            "down" -> "雀神"
            "left" -> "下家"
            "right" -> "对家"
            else -> position
        }
    }
}
