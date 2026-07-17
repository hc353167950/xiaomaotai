package com.example.xiaomaotai

import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * 倒计时深模块：整个应用中"距离下一次纪念日还有几天"的唯一答案来源。
 *
 * 接口刻意收窄为一个入口 [of]：调用方只需给出事件日期字符串和"今天"，
 * 拿回天数、统一文案和下一次纪念日的公历日期。公历/农历/忽略年份/闰月/
 * 2月29日/跨年循环这些分支全部是实现细节，不再泄漏给调用方。
 *
 * 约束：
 * - 纪念日是循环的，[days] 永远 >= 0，表示距离下一次的天数；
 * - [today] 可注入，测试用固定日期即可覆盖全部边界，不依赖系统时钟。
 */
data class Countdown(
    val days: Long,          // 距离下一次纪念日的天数（>= 0）
    val label: String,       // 统一显示文案："就是今天" / "明天" / "还有N天"
    val nextDate: LocalDate  // 下一次纪念日对应的公历日期
) {

    /** 供仍以 java.util.Date 工作的调用方（AlarmManager 相关）换取旧类型 */
    fun nextDateAsDate(): Date =
        Date.from(nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant())

    companion object {

        /**
         * 计算事件的倒计时。
         * @param eventDate 事件日期字符串（yyyy-MM-dd / MM-dd / lunar:... 四种格式）
         * @param today 当前日期，默认取系统时钟，测试时注入固定值
         * @return 解析失败返回 null，调用方据此显示"日期无效"
         */
        fun of(eventDate: String, today: LocalDate = LocalDate.now()): Countdown? {
            val parsed = DateParser.parse(eventDate) ?: return null

            val days = when (parsed.type) {
                DateParser.DateType.LUNAR -> lunarDaysUntil(parsed, today)
                DateParser.DateType.LUNAR_MONTH_DAY -> lunarMonthDayDaysUntil(parsed, today)
                DateParser.DateType.SOLAR, DateParser.DateType.MONTH_DAY -> solarDaysUntil(parsed, today)
            }

            return Countdown(
                days = days,
                label = labelFor(days),
                nextDate = today.plusDays(days)
            )
        }

        /** 倒计时文案的唯一来源，消除各处 "今天"/"就是今天" 不一致 */
        fun labelFor(days: Long): String = when {
            days == 0L -> "就是今天"
            days == 1L -> "明天"
            days == -1L -> "昨天"
            days > 0 -> "还有${days}天"
            else -> "${-days}天前"
        }

        /**
         * 公历（含忽略年份）事件的循环倒计时。
         * SOLAR 且日期在未来：直接计算；否则按"今年同月日已过则取明年"循环。
         * 2月29日在非闰年按2月28日处理。
         */
        private fun solarDaysUntil(parsed: DateParser.ParsedDate, today: LocalDate): Long {
            val month = parsed.month
            val day = parsed.day

            return try {
                if (parsed.type == DateParser.DateType.SOLAR && parsed.year != null) {
                    val safeDay = safeFebruaryDay(parsed.year, month, day)
                    val eventDate = LocalDate.of(parsed.year, month, safeDay)
                    if (!eventDate.isBefore(today)) {
                        return ChronoUnit.DAYS.between(today, eventDate)
                    }
                    // 未来日期已过，落入下方循环逻辑
                }

                var target = LocalDate.of(today.year, month, safeFebruaryDay(today.year, month, day))
                if (target.isBefore(today)) {
                    val nextYear = today.year + 1
                    target = LocalDate.of(nextYear, month, safeFebruaryDay(nextYear, month, day))
                }
                ChronoUnit.DAYS.between(today, target)
            } catch (e: Exception) {
                1L
            }
        }

        /** 农历事件（带年份）的循环倒计时，闰月顺序交由 LunarCalendarHelper 统一处理 */
        private fun lunarDaysUntil(parsed: DateParser.ParsedDate, today: LocalDate): Long {
            // 未指定年份时使用当前农历年份（农历腊月可能对应下一个公历年，不能直接用公历年）
            val eventYear = parsed.year ?: currentLunarYear(today)
            return LunarCalendarHelper.calculateLunarCountdown(
                eventYear, parsed.month, parsed.day, parsed.isLeapMonth, today
            )
        }

        /** 忽略年份的农历事件：以当前农历年为基准循环计算 */
        private fun lunarMonthDayDaysUntil(parsed: DateParser.ParsedDate, today: LocalDate): Long {
            return LunarCalendarHelper.calculateLunarCountdown(
                currentLunarYear(today), parsed.month, parsed.day, parsed.isLeapMonth, today
            )
        }

        /** 当前公历日期对应的农历年份，转换失败时退回公历年份 */
        private fun currentLunarYear(today: LocalDate): Int = try {
            com.nlf.calendar.Solar(today.year, today.monthValue, today.dayOfMonth).lunar.year
        } catch (e: Exception) {
            today.year
        }

        /** 2月29日在非闰年降级为2月28日 */
        private fun safeFebruaryDay(year: Int, month: Int, day: Int): Int =
            if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) 28 else day
    }
}
