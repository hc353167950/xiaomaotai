package com.example.xiaomaotai

import android.util.Log
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

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
        LUNAR,      // 农历: lunar:2025-06-16 或 lunar:2025-L06-16
        SOLAR,      // 公历: 2025-12-18
        MONTH_DAY   // 忽略年份: 12-18
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
                // 农历格式
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
     */
    private fun parseLunarDate(eventDate: String): ParsedDate? {
        val lunarDatePart = eventDate.removePrefix("lunar:")

        return when {
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
     * 判断是否为忽略年份格式
     */
    fun isMonthDayFormat(eventDate: String): Boolean {
        return eventDate.matches(Regex("\\d{2}-\\d{2}"))
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
     * 计算农历事件到下次的天数
     * @param parsedDate 解析后的日期
     * @param today 当前日期
     * @return 天数
     */
    fun calculateLunarDaysUntil(parsedDate: ParsedDate, today: LocalDate = LocalDate.now()): Long {
        if (parsedDate.type != DateType.LUNAR) {
            return 0L
        }

        return LunarCalendarHelper.calculateLunarCountdown(
            parsedDate.year ?: today.year,
            parsedDate.month,
            parsedDate.day,
            parsedDate.isLeapMonth,
            today
        )
    }

    /**
     * 计算公历/忽略年份事件到下次的天数
     * @param parsedDate 解析后的日期
     * @param today 当前日期
     * @return 天数（永远>=0，表示距离下一次纪念日的天数）
     *
     * 核心逻辑：纪念日是循环的，今年过了就计算到明年
     * - SOLAR类型：如果事件日期在未来（年份>今年），直接计算到该日期；否则按月日循环计算
     * - MONTH_DAY类型：只按月日循环计算
     * - 永远不会返回负数
     */
    fun calculateSolarDaysUntil(parsedDate: ParsedDate, today: LocalDate = LocalDate.now()): Long {
        val month = parsedDate.month
        val day = parsedDate.day

        return try {
            // SOLAR类型（有年份）：检查是否是未来年份的事件
            if (parsedDate.type == DateType.SOLAR && parsedDate.year != null) {
                val eventYear = parsedDate.year

                // 处理2月29日边界情况
                val safeDay = if (month == 2 && day == 29 && !Year.isLeap(eventYear.toLong())) 28 else day
                val eventDate = LocalDate.of(eventYear, month, safeDay)

                // 如果事件日期在今天之后（未来日期），直接计算天数
                if (!eventDate.isBefore(today)) {
                    return ChronoUnit.DAYS.between(today, eventDate)
                }

                // 事件日期已过，按循环逻辑计算到下一次（今年或明年的同月日）
            }

            // 处理2月29日边界情况：在非闰年使用2月28日
            val safeDay = if (month == 2 && day == 29 && !Year.isLeap(today.year.toLong())) 28 else day

            // 计算今年的目标日期
            var target = LocalDate.of(today.year, month, safeDay)

            // 如果今年的日期已过，使用明年的日期（纪念日是循环的）
            if (target.isBefore(today)) {
                val nextYear = today.year + 1
                val nextYearSafeDay = if (month == 2 && day == 29 && !Year.isLeap(nextYear.toLong())) 28 else day
                target = LocalDate.of(nextYear, month, nextYearSafeDay)
            }

            ChronoUnit.DAYS.between(today, target)
        } catch (e: Exception) {
            Log.e(TAG, "计算公历天数失败: $parsedDate", e)
            1L
        }
    }

    /**
     * 计算事件到下次的天数（统一入口）
     * @param eventDate 事件日期字符串
     * @param today 当前日期
     * @return Pair<显示文本, 天数>
     */
    fun calculateDaysUntil(eventDate: String, today: LocalDate = LocalDate.now()): Pair<String, Long> {
        val parsedDate = parse(eventDate)

        if (parsedDate == null) {
            return "日期无效" to 0L
        }

        return when (parsedDate.type) {
            DateType.LUNAR -> {
                val days = calculateLunarDaysUntil(parsedDate, today)
                val displayText = when {
                    days == 0L -> "今天"
                    days > 0 -> "还有${days}天"
                    else -> "已过${-days}天"
                }
                displayText to days
            }
            DateType.MONTH_DAY, DateType.SOLAR -> {
                val days = calculateSolarDaysUntil(parsedDate, today)
                val displayText = when {
                    days == 0L -> "今天"
                    days > 0 -> "还有${days}天"
                    else -> "已过${-days}天"
                }
                displayText to days
            }
        }
    }
}
