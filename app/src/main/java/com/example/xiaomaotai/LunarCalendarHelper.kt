package com.example.xiaomaotai

import android.util.Log
import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 农历计算工具：所有农历/公历转换与闰月判定的唯一来源。
 *
 * 设计要点：
 * 1. 闰月数据全部来自 lunar-java 的 [LunarYear.getLeapMonth]，不再硬编码年份表。
 *    （旧版硬编码表只覆盖 9 个年份，实际 2020-2050 有 12 个闰月年，漏了 2020/2047/2050。）
 * 2. 闰月纪念日「顺延」语义：若某个农历年没有该闰月，向后寻找最近的有该闰月的年份，
 *    倒计时按到那一年的天数显示，绝不降级为普通月。
 * 3. 农历三十遇到只有 29 天的月份时，同样顺延到下一个该月有 30 天的年份。
 * 4. 非法农历日期（如闰月不存在、日超出月长）在解析阶段即被拒绝，不再静默返回 365 天。
 */
object LunarCalendarHelper {

    private const val TAG = "LunarCalendarHelper"

    /** 顺延搜索上限：实测闰二月/闰八月最大间隔 57 年，100 年足以覆盖 */
    private const val MAX_LOOKAHEAD_YEARS = 100

    // ---------- 阳历 / 农历互转 ----------

    /** 阳历 → 农历显示文本，如「二〇二五年闰六月初一」 */
    fun solarToLunar(year: Int, month: Int, day: Int): String {
        val lunar = Solar(year, month, day).lunar
        val isLeap = lunar.month < 0
        val actualMonth = if (isLeap) -lunar.month else lunar.month
        return "${lunar.yearInChinese}年" +
            getLunarMonthName(actualMonth.coerceIn(1, 12), isLeap) +
            getLunarDayName(lunar.day.coerceIn(1, 30))
    }

    /**
     * 农历 → 阳历。
     * @return 形如 2025-07-25；农历日期非法时返回 null（不再抛异常给调用方）
     */
    fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeap: Boolean = false): String? {
        return try {
            val signedMonth = if (isLeap) -lunarMonth else lunarMonth
            val solar = Lunar(lunarYear, signedMonth, lunarDay).solar
            String.format("%04d-%02d-%02d", solar.year, solar.month, solar.day)
        } catch (e: Exception) {
            Log.w(TAG, "农历转公历失败: ${lunarYear}年${if (isLeap) "闰" else ""}${lunarMonth}月${lunarDay}日 -> ${e.message}")
            null
        }
    }

    /** 农历 → [LocalDate]，非法日期返回 null */
    fun lunarToLocalDate(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeap: Boolean): LocalDate? =
        lunarToSolar(lunarYear, lunarMonth, lunarDay, isLeap)?.let { LocalDate.parse(it) }

    /** 校验农历日期是否真实存在（用于添加事件时拦截非法日期） */
    fun isValidLunarDate(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeap: Boolean): Boolean =
        lunarToLocalDate(lunarYear, lunarMonth, lunarDay, isLeap) != null

    // ---------- 闰月查询 ----------

    /** 指定农历年的闰月月份，0 表示无闰月。数据来自库，无需硬编码 */
    fun getLeapMonth(year: Int): Int = try {
        LunarYear.fromYear(year).leapMonth
    } catch (e: Exception) {
        Log.e(TAG, "获取 $year 年闰月失败: ${e.message}")
        0
    }

    fun hasLeapMonth(year: Int): Boolean = getLeapMonth(year) > 0

    /**
     * 从 [fromYear] 起（含）寻找最近一个存在闰 [leapMonth] 月的年份。
     * @return 找到的年份；[MAX_LOOKAHEAD_YEARS] 年内不存在则返回 null
     */
    fun findNextLeapMonthYear(leapMonth: Int, fromYear: Int): Int? {
        if (leapMonth !in 1..12) return null
        for (year in fromYear..(fromYear + MAX_LOOKAHEAD_YEARS)) {
            if (getLeapMonth(year) == leapMonth) return year
        }
        return null
    }

    /**
     * 从 [fromYear] 起（含）寻找最近一个该农历月有 [day] 天的年份。
     * 用于「农历三十」在只有 29 天的月份里的顺延。
     */
    fun findNextYearWhereMonthHasDay(month: Int, day: Int, fromYear: Int): Int? {
        if (month !in 1..12 || day !in 1..30) return null
        for (year in fromYear..(fromYear + MAX_LOOKAHEAD_YEARS)) {
            val days = getLunarMonthDays(year, month, isLeap = false)
            if (days >= day) return year
        }
        return null
    }

    // ---------- 月份/日期名称 ----------

    fun getLunarMonthName(month: Int): String = when (month) {
        1 -> "正月"
        2 -> "二月"
        3 -> "三月"
        4 -> "四月"
        5 -> "五月"
        6 -> "六月"
        7 -> "七月"
        8 -> "八月"
        9 -> "九月"
        10 -> "十月"
        11 -> "十一月"
        12 -> "腊月"
        else -> "未知月"
    }

    fun getLunarMonthName(month: Int, isLeap: Boolean): String =
        if (isLeap) "闰${getLunarMonthName(month)}" else getLunarMonthName(month)

    fun getLunarDayName(day: Int): String = when (day) {
        1 -> "初一"; 2 -> "初二"; 3 -> "初三"; 4 -> "初四"; 5 -> "初五"
        6 -> "初六"; 7 -> "初七"; 8 -> "初八"; 9 -> "初九"; 10 -> "初十"
        11 -> "十一"; 12 -> "十二"; 13 -> "十三"; 14 -> "十四"; 15 -> "十五"
        16 -> "十六"; 17 -> "十七"; 18 -> "十八"; 19 -> "十九"; 20 -> "二十"
        21 -> "廿一"; 22 -> "廿二"; 23 -> "廿三"; 24 -> "廿四"; 25 -> "廿五"
        26 -> "廿六"; 27 -> "廿七"; 28 -> "廿八"; 29 -> "廿九"; 30 -> "三十"
        else -> "未知日"
    }

    fun getDayName(day: Int): String = getLunarDayName(day)

    fun getYearName(year: Int): String = "${year}年"

    fun getMonthName(month: Int): String = getLunarMonthName(month)

    fun formatLunarDate(year: Int, month: Int, day: Int): String =
        "${year}年${getLunarMonthName(month)}${getLunarDayName(day)}"

    // ---------- 公历辅助 ----------

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun getDaysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /**
     * 指定农历月的实际天数（大月 30 / 小月 29）。
     * 直接问库的 [LunarYear.getMonth]，闰月用负数传入，不再用 abs 猜。
     */
    fun getLunarMonthDays(year: Int, month: Int, isLeap: Boolean): Int {
        return try {
            val signedMonth = if (isLeap) -month else month
            val lunarMonth = LunarYear.fromYear(year).getMonth(signedMonth)
            lunarMonth?.dayCount ?: 30
        } catch (e: Exception) {
            Log.e(TAG, "获取农历${year}年${if (isLeap) "闰" else ""}${month}月天数失败: ${e.message}")
            30
        }
    }

    /**
     * 指定农历年的月份列表（含闰月）。
     * 闰月以负数表示（如闰六月 → -6），与选择器的约定一致。
     */
    fun getLunarMonthsInYear(year: Int): List<Pair<Int, Boolean>> {
        val leapMonth = getLeapMonth(year)
        val months = mutableListOf<Pair<Int, Boolean>>()
        for (month in 1..12) {
            months.add(Pair(month, false))
            // 闰月插在同名普通月之后
            if (leapMonth > 0 && month == leapMonth) {
                months.add(Pair(month, true))
            }
        }
        return months
    }

    /**
     * 计算农历事件在某农历年内全部可能发生的公历日期。
     *
     * 语义（与产品确认一致）：
     * - **普通月事件**（如六月十六）：该年有此闰月时，普通六月十六与闰六月十六
     *   是两天，都要过 —— 返回两个候选。无闰月年份只有普通月一天。
     * - **闰月事件**（如闰六月十六）：该年有此闰月则用闰月那天；该年没有此闰月，
     *   退回普通月那天（「实际没有闰月，普通也会过」），而不是顺延到几十年后。
     * - 农历三十遇只有 29 天的月份：该年无候选，由调用方继续向后找年份。
     *
     * @return 该年的候选日期列表（可能为空），按时间升序
     */
    fun candidatesForYear(
        lunarYear: Int,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean
    ): List<LocalDate> {
        return try {
            val hasThisLeapMonth = getLeapMonth(lunarYear) == eventMonth
            val result = mutableListOf<LocalDate>()

            if (isEventLeap) {
                // 闰月事件：有此闰月用闰月，没有则退回普通月
                val date = if (hasThisLeapMonth) {
                    lunarToLocalDate(lunarYear, eventMonth, eventDay, isLeap = true)
                } else {
                    lunarToLocalDate(lunarYear, eventMonth, eventDay, isLeap = false)
                }
                date?.let { result.add(it) }
            } else {
                // 普通月事件：普通月那天必有（除非该月天数不足）
                lunarToLocalDate(lunarYear, eventMonth, eventDay, isLeap = false)
                    ?.let { result.add(it) }
                // 该年有此闰月时，闰月那天是「第二次」
                if (hasThisLeapMonth) {
                    lunarToLocalDate(lunarYear, eventMonth, eventDay, isLeap = true)
                        ?.let { result.add(it) }
                }
            }
            result.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "计算农历候选日期失败: ${lunarYear}年${eventMonth}月${eventDay}日", e)
            emptyList()
        }
    }

    /**
     * 一个农历事件的下一次发生：天数 + 命中的公历日期 + 是否命中的闰月。
     */
    data class NextOccurrence(
        val days: Long,
        val date: LocalDate,
        /** true 表示这次命中的是闰月那天（闰月年的「第二次」） */
        val isLeapHit: Boolean
    )

    /**
     * 计算农历事件的下一次发生。
     *
     * 从当前农历年起逐年向后找，取所有候选中最早的不早于 [currentDate] 的那一天。
     * 这样闰月年的「普通月 → 闰月」两次会依次命中，两次都过完才轮到下一农历年。
     *
     * @param eventYear 事件指定年份；传 null 表示忽略年份（按当前农历年循环）
     * @return 找不到返回 null（如闰月日在该月不存在且百年内无匹配）
     */
    fun nextOccurrence(
        eventYear: Int?,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean,
        currentDate: LocalDate
    ): NextOccurrence? {
        return try {
            // 指定了年份：先看该年是否还有未过的候选
            if (eventYear != null) {
                val hit = candidatesForYear(eventYear, eventMonth, eventDay, isEventLeap)
                    .firstOrNull { !it.isBefore(currentDate) }
                if (hit != null) {
                    return NextOccurrence(
                        days = ChronoUnit.DAYS.between(currentDate, hit),
                        date = hit,
                        isLeapHit = isLeapCandidate(eventYear, eventMonth, eventDay, hit)
                    )
                }
            }

            val startYear = currentLunarYear(currentDate)
            for (offset in 0..MAX_LOOKAHEAD_YEARS) {
                val year = startYear + offset
                val best = candidatesForYear(year, eventMonth, eventDay, isEventLeap)
                    .firstOrNull { !it.isBefore(currentDate) }
                    ?: continue
                return NextOccurrence(
                    days = ChronoUnit.DAYS.between(currentDate, best),
                    date = best,
                    isLeapHit = isLeapCandidate(year, eventMonth, eventDay, best)
                )
            }
            Log.w(TAG, "农历${eventMonth}月${eventDay}日在 $MAX_LOOKAHEAD_YEARS 年内无候选")
            null
        } catch (e: Exception) {
            Log.e(TAG, "计算农历下一次发生失败", e)
            null
        }
    }

    /**
     * 判断某个候选日期是否来自闰月那天。
     * 用于通知文案区分「六月十六」与「闰六月十六」。
     */
    fun isLeapCandidate(lunarYear: Int, eventMonth: Int, eventDay: Int, date: LocalDate): Boolean {
        if (getLeapMonth(lunarYear) != eventMonth) return false
        return lunarToLocalDate(lunarYear, eventMonth, eventDay, isLeap = true) == date
    }

    /**
     * 兼容旧调用方的倒计时接口。
     * @return 天数；找不到返回 [Long.MAX_VALUE]（由 Countdown 转成 null）
     */
    fun calculateLunarCountdown(
        eventYear: Int?,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean,
        currentDate: LocalDate
    ): Long = nextOccurrence(eventYear, eventMonth, eventDay, isEventLeap, currentDate)?.days
        ?: Long.MAX_VALUE

    /** 当前公历日期对应的农历年份（农历腊月可能落在下一公历年，不能用公历年） */
    fun currentLunarYear(date: LocalDate): Int = try {
        Solar(date.year, date.monthValue, date.dayOfMonth).lunar.year
    } catch (e: Exception) {
        date.year
    }

    /**
     * 描述闰月事件的「下次发生」信息，供 UI 说明用。
     * 例：闰六月十六 → 「下次闰六月在 2036 年」
     */
    fun describeNextLeapOccurrence(
        eventMonth: Int,
        eventDay: Int,
        currentDate: LocalDate = LocalDate.now()
    ): String? {
        val startYear = currentLunarYear(currentDate)
        val year = findNextLeapMonthYear(eventMonth, startYear) ?: return null
        val date = lunarToLocalDate(year, eventMonth, eventDay, true) ?: return null
        return "下次闰${getLunarMonthName(eventMonth)}${getLunarDayName(eventDay)}在 $year 年（$date）"
    }
}
