package com.example.xiaomaotai

import android.util.Log
import java.time.LocalDate

/**
 * 统一的日期解析工具类
 * 用于解析事件日期字符串，支持农历、公历和忽略年份格式
 */
object DateParser {

    private const val TAG = "DateParser"

    /**
     * 日期类型枚举
     */
    enum class DateType {
        LUNAR,           // 农历: lunar:2025-06-16 或 lunar:2025-L06-16
        SOLAR,           // 公历: 2025-12-18
        MONTH_DAY,       // 忽略年份(公历): 12-18
        LUNAR_MONTH_DAY  // 忽略年份(农历): lunar:06-16 或 lunar:L06-16
    }

    /**
     * 解析后的日期数据类
     */
    data class ParsedDate(
        val type: DateType,           // 日期类型
        val year: Int?,               // 年份（忽略年份格式时为null）
        val month: Int,               // 月份
        val day: Int,                 // 日期
        val isLeapMonth: Boolean,     // 是否闰月（仅农历有效）
        val rawDate: String           // 原始日期字符串
    )

    /**
     * 解析事件日期字符串
     * @param eventDate 事件日期字符串
     * @return ParsedDate 或 null（解析失败时）
     */
    fun parse(eventDate: String): ParsedDate? {
        return try {
            when {
                // 农历格式（包含忽略年份的农历）
                eventDate.startsWith("lunar:") -> parseLunarDate(eventDate)
                // 忽略年份格式 (MM-dd)
                eventDate.matches(Regex("\\d{2}-\\d{2}")) -> parseMonthDayDate(eventDate)
                // 标准公历格式 (yyyy-MM-dd)
                eventDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> parseSolarDate(eventDate)
                else -> {
                    Log.w(TAG, "未知日期格式: $eventDate")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析日期失败: $eventDate", e)
            null
        }
    }

    /**
     * 解析农历日期
     * 支持格式：
     * - lunar:2025-06-16 (普通月)
     * - lunar:2025-L06-16 (闰月)
     * - lunar:2025--6-16 (错误的闰月格式，兼容处理)
     * - lunar:06-16 (忽略年份的农历)
     * - lunar:L06-16 (忽略年份的农历闰月)
     */
    private fun parseLunarDate(eventDate: String): ParsedDate? {
        val lunarDatePart = eventDate.removePrefix("lunar:")

        return when {
            // 忽略年份的农历闰月格式: L06-16
            lunarDatePart.matches(Regex("L\\d{2}-\\d{2}")) -> {
                val monthPart = lunarDatePart.substring(0, 3) // L06
                val dayPart = lunarDatePart.substring(4) // 16
                val month = monthPart.substring(1).toIntOrNull() ?: 1
                val day = dayPart.toIntOrNull() ?: 1

                ParsedDate(
                    type = DateType.LUNAR_MONTH_DAY,
                    year = null,
                    month = month,
                    day = day,
                    isLeapMonth = true,
                    rawDate = eventDate
                )
            }
            // 忽略年份的农历普通月格式: 06-16
            lunarDatePart.matches(Regex("\\d{2}-\\d{2}")) -> {
                val parts = lunarDatePart.split("-")
                val month = parts[0].toIntOrNull() ?: 1
                val day = parts[1].toIntOrNull() ?: 1

                ParsedDate(
                    type = DateType.LUNAR_MONTH_DAY,
                    year = null,
                    month = month,
                    day = day,
                    isLeapMonth = false,
                    rawDate = eventDate
                )
            }
            // 正确的闰月格式: 2025-L06-16
            lunarDatePart.contains("-L") -> {
                val parts = lunarDatePart.split("-")
                if (parts.size >= 3) {
                    val year = parts[0].toIntOrNull() ?: LocalDate.now().year
                    val monthPart = parts[1] // L06
                    val day = parts[2].toIntOrNull() ?: 1
                    val month = monthPart.substring(1).toIntOrNull() ?: 1

                    ParsedDate(
                        type = DateType.LUNAR,
                        year = year,
                        month = month,
                        day = day,
                        isLeapMonth = true,
                        rawDate = eventDate
                    )
                } else null
            }
            // 错误的闰月格式: 2025--6-16 (兼容处理)
            lunarDatePart.contains("--") -> {
                val corrected = lunarDatePart.replace("--", "-")
                val parts = corrected.split("-")
                if (parts.size >= 3) {
                    val year = parts[0].toIntOrNull() ?: LocalDate.now().year
                    val month = parts[1].toIntOrNull() ?: 1
                    val day = parts[2].toIntOrNull() ?: 1

                    ParsedDate(
                        type = DateType.LUNAR,
                        year = year,
                        month = month,
                        day = day,
                        isLeapMonth = true,
                        rawDate = eventDate
                    )
                } else null
            }
            // 普通农历格式: 2025-06-16
            else -> {
                val parts = lunarDatePart.split("-")
                if (parts.size >= 3) {
                    val year = parts[0].toIntOrNull() ?: LocalDate.now().year
                    val month = parts[1].toIntOrNull() ?: 1
                    val day = parts[2].toIntOrNull() ?: 1

                    ParsedDate(
                        type = DateType.LUNAR,
                        year = year,
                        month = month,
                        day = day,
                        isLeapMonth = false,
                        rawDate = eventDate
                    )
                } else null
            }
        }
    }

    /**
     * 解析公历日期 (yyyy-MM-dd)
     */
    private fun parseSolarDate(eventDate: String): ParsedDate? {
        val parts = eventDate.split("-")
        return if (parts.size >= 3) {
            val year = parts[0].toIntOrNull() ?: LocalDate.now().year
            val month = parts[1].toIntOrNull() ?: 1
            val day = parts[2].toIntOrNull() ?: 1

            ParsedDate(
                type = DateType.SOLAR,
                year = year,
                month = month,
                day = day,
                isLeapMonth = false,
                rawDate = eventDate
            )
        } else null
    }

    /**
     * 解析忽略年份格式 (MM-dd)
     */
    private fun parseMonthDayDate(eventDate: String): ParsedDate? {
        val parts = eventDate.split("-")
        return if (parts.size >= 2) {
            val month = parts[0].toIntOrNull() ?: 1
            val day = parts[1].toIntOrNull() ?: 1

            ParsedDate(
                type = DateType.MONTH_DAY,
                year = null,
                month = month,
                day = day,
                isLeapMonth = false,
                rawDate = eventDate
            )
        } else null
    }

    /**
     * 判断是否为农历日期
     */
    fun isLunar(eventDate: String): Boolean {
        return eventDate.startsWith("lunar:")
    }

    /**
     * 判断是否为忽略年份格式（公历）
     */
    fun isMonthDayFormat(eventDate: String): Boolean {
        return eventDate.matches(Regex("\\d{2}-\\d{2}"))
    }

    /**
     * 判断是否为忽略年份格式（农历）
     */
    fun isLunarMonthDayFormat(eventDate: String): Boolean {
        if (!eventDate.startsWith("lunar:")) return false
        val lunarPart = eventDate.removePrefix("lunar:")
        return lunarPart.matches(Regex("L?\\d{2}-\\d{2}"))
    }

    /**
     * 格式化农历日期为显示文本
     * @param parsedDate 解析后的日期
     * @return 如：农历六月十六、农历闰六月十六
     */
    fun formatLunarForDisplay(parsedDate: ParsedDate): String {
        if (parsedDate.type != DateType.LUNAR) {
            return ""
        }

        val monthName = LunarCalendarHelper.getLunarMonthName(parsedDate.month, parsedDate.isLeapMonth)
        val dayName = LunarCalendarHelper.getLunarDayName(parsedDate.day)

        return "农历$monthName$dayName"
    }

    /**
     * 格式化公历日期为显示文本
     * @param month 月份
     * @param day 日期
     * @return 如：公历12月18日
     */
    fun formatSolarForDisplay(month: Int, day: Int): String {
        return "公历${month}月${day}日"
    }

    /**
     * 计算事件到下次的天数（统一入口）
     * 委托给 [Countdown] 深模块，仅保留旧的 Pair 返回形态供既有调用方使用。
     * @return Pair<显示文本, 天数>
     */
    fun calculateDaysUntil(eventDate: String, today: LocalDate = LocalDate.now()): Pair<String, Long> {
        val countdown = Countdown.of(eventDate, today) ?: return "日期无效" to 0L
        return countdown.label to countdown.days
    }
}
