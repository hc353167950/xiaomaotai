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
    val label: String,       // 统一显示文案："就是今天" / "明天" / "还有N天" / "第N天"
    val nextDate: LocalDate, // 下一次纪念日对应的公历日期
    /**
     * 该事件在「本次循环周期」内的全部纪念日日期，按时间升序。
     *
     * 农历闰月年的「六月十六」会出现两次（普通六月十六 + 闰六月十六），
     * 这两个日期都会列入，供提醒调度各设一套闹钟。非闰月年通常只有一个。
     * 公历事件恒为一个。
     */
    val occurrences: List<LocalDate> = listOf(nextDate),
    /** 最近这一次是否命中闰月那天（用于文案区分「闰六月十六」） */
    val isLeapOccurrence: Boolean = false,
    /** 正数模式：从起始日至今已经过去的天数（仅当 mode == COUNT_UP 时有意义） */
    val elapsedDays: Long? = null,
    /** 该事件的计数方向 */
    val mode: EventMode = EventMode.COUNTDOWN
) {

    /** 供仍以 java.util.Date 工作的调用方（AlarmManager 相关）换取旧类型 */
    fun nextDateAsDate(): Date =
        Date.from(nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant())

    companion object {

        /** 农历顺延搜索失败时的哨兵值（不再用 365 伪装成合法天数） */
        const val INVALID_DAYS = Long.MAX_VALUE
        /**
         * 计算事件的倒计时。
         *
         * @param eventDate 事件日期字符串（yyyy-MM-dd / MM-dd / lunar:... 四种格式）
         * @param today 当前日期，默认取系统时钟，测试时注入固定值
         * @param mode 计数方向。COUNT_UP 时改用 [elapsedDays] 表示「已经第 N 天」
         * @return 解析失败返回 null，调用方据此显示"日期无效"
         */
        fun of(
            eventDate: String,
            today: LocalDate = LocalDate.now(),
            mode: EventMode = EventMode.COUNTDOWN
        ): Countdown? {
            val parsed = DateParser.parse(eventDate) ?: return null

            // 农历走候选模型：闰月年可能有两个日期（普通月 + 闰月）
            if (parsed.type == DateParser.DateType.LUNAR ||
                parsed.type == DateParser.DateType.LUNAR_MONTH_DAY
            ) {
                val next = LunarCalendarHelper.nextOccurrence(
                    eventYear = parsed.year,
                    eventMonth = parsed.month,
                    eventDay = parsed.day,
                    isEventLeap = parsed.isLeapMonth,
                    currentDate = today
                ) ?: return null

                // 本年全部候选（含闰月那天），用于给每个日期各设一套提醒
                val cycleYear = LunarCalendarHelper.currentLunarYear(next.date)
                val candidates = LunarCalendarHelper.candidatesForYear(
                    lunarYear = cycleYear,
                    eventMonth = parsed.month,
                    eventDay = parsed.day,
                    isEventLeap = parsed.isLeapMonth
                ).filter { !it.isBefore(today) }

                return Countdown(
                    days = next.days,
                    label = labelFor(next.days),
                    nextDate = next.date,
                    occurrences = if (candidates.isEmpty()) listOf(next.date) else candidates,
                    isLeapOccurrence = next.isLeapHit,
                    elapsedDays = elapsedFor(parsed, candidates, today, mode),
                    mode = mode
                )
            }

            // 公历：单一日期
            val days = solarDaysUntil(parsed, today)
            if (days == INVALID_DAYS) return null
            val nextDate = today.plusDays(days)
            return Countdown(
                days = days,
                label = labelFor(days),
                nextDate = nextDate,
                occurrences = listOf(nextDate),
                isLeapOccurrence = false,
                elapsedDays = elapsedFor(parsed, listOf(nextDate), today, mode),
                mode = mode
            )
        }

        /**
         * 正数模式下，从起始日至今的天数。
         *
         * 语义：纪念日「第 1 天」= 起始当日，所以 elapsed = 天数差 + 1。
         * 只有指定了具体年份的事件才算得出起点（`MM-dd` 这种无年份事件谈不上「第几天」），
         * 这类情况返回 null，UI 回退为普通倒数展示。
         */
        private fun elapsedFor(
            parsed: DateParser.ParsedDate,
            occurrences: List<LocalDate>,
            today: LocalDate,
            mode: EventMode
        ): Long? {
            if (mode != EventMode.COUNT_UP) return null
            val start = startDate(parsed, occurrences) ?: return null
            val diff = ChronoUnit.DAYS.between(start, today)
            return diff + 1
        }

        /** 推算事件的起始日（第一次发生的那天） */
        private fun startDate(
            parsed: DateParser.ParsedDate,
            occurrences: List<LocalDate>
        ): LocalDate? {
            return try {
                if (parsed.type == DateParser.DateType.LUNAR && parsed.year != null) {
                    LunarCalendarHelper.lunarToLocalDate(
                        lunarYear = parsed.year,
                        lunarMonth = parsed.month,
                        lunarDay = parsed.day,
                        isLeap = parsed.isLeapMonth
                    )
                } else if (parsed.type == DateParser.DateType.SOLAR && parsed.year != null) {
                    LocalDate.of(
                        parsed.year,
                        parsed.month,
                        safeFebruaryDay(parsed.year, parsed.month, parsed.day)
                    )
                } else {
                    // 无年份事件：无法定义起点
                    null
                }
            } catch (e: Exception) {
                occurrences.firstOrNull()
            }
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


        /** 2月29日在非闰年降级为2月28日 */
        private fun safeFebruaryDay(year: Int, month: Int, day: Int): Int =
            if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) 28 else day
    }
}
