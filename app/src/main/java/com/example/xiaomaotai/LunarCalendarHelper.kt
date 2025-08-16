package com.example.xiaomaotai

import android.util.Log
import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 农历日期转换工具类
 * 使用专业的农历库进行准确的农历/阳历转换
 */
object LunarCalendarHelper {
    
    /**
     * 将阳历日期转换为农历日期字符串
     */
    fun solarToLunar(year: Int, month: Int, day: Int): String {
        val solar = Solar(year, month, day)
        val lunar = solar.lunar

        // 确保月份和日期在有效范围内
        val isLeap = lunar.month < 0 // 负数表示闰月
        val actualMonth = if (isLeap) -lunar.month else lunar.month
        val monthName = getLunarMonthName(actualMonth.coerceIn(1, 12), isLeap)
        val dayName = getLunarDayName(lunar.day.coerceIn(1, 30))

        return "${lunar.yearInChinese}年${monthName}${dayName}"
    }
    
    /**
     * 将农历日期转换为阳历日期字符串
     */
    fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int): String {
        val lunar = Lunar(lunarYear, lunarMonth, lunarDay)
        val solar = lunar.solar

        return String.format("%04d-%02d-%02d",
            solar.year, solar.month, solar.day)
    }
    
    /**
     * 将农历日期转换为阳历日期字符串（支持闰月）
     */
    fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeap: Boolean): String {
        val lunar = if (isLeap) {
            Lunar(lunarYear, -lunarMonth, lunarDay) // 负数表示闰月
        } else {
            Lunar(lunarYear, lunarMonth, lunarDay)
        }
        val solar = lunar.solar

        return String.format("%04d-%02d-%02d",
            solar.year, solar.month, solar.day)
    }
    
    /**
     * 获取农历月份名称
     */
    fun getLunarMonthName(month: Int): String {
        return when (month) {
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
    }
    
    /**
     * 获取农历月份名称（支持闰月）
     */
    fun getLunarMonthName(month: Int, isLeap: Boolean): String {
        val baseName = getLunarMonthName(month)
        return if (isLeap) "闰$baseName" else baseName
    }
    
    /**
     * 获取农历日期名称
     */
    fun getLunarDayName(day: Int): String {
        return when (day) {
            1 -> "初一"
            2 -> "初二"
            3 -> "初三"
            4 -> "初四"
            5 -> "初五"
            6 -> "初六"
            7 -> "初七"
            8 -> "初八"
            9 -> "初九"
            10 -> "初十"
            11 -> "十一"
            12 -> "十二"
            13 -> "十三"
            14 -> "十四"
            15 -> "十五"
            16 -> "十六"
            17 -> "十七"
            18 -> "十八"
            19 -> "十九"
            20 -> "二十"
            21 -> "廿一"
            22 -> "廿二"
            23 -> "廿三"
            24 -> "廿四"
            25 -> "廿五"
            26 -> "廿六"
            27 -> "廿七"
            28 -> "廿八"
            29 -> "廿九"
            30 -> "三十"
            else -> "未知日"
        }
    }
    
    /**
     * 检查是否为闰年
     */
    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
    
    /**
     * 获取指定年月的天数
     */
    fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
    
    /**
     * 格式化农历日期
     */
    fun formatLunarDate(year: Int, month: Int, day: Int): String {
        return "${year}年${getLunarMonthName(month)}${getLunarDayName(day)}"
    }

    fun getYearName(year: Int): String {
        return "${year}年"
    }

    fun getMonthName(month: Int): String {
        return getLunarMonthName(month)
    }
    
    /**
     * 获取指定年份的农历月份列表（包含闰月）
     */
    fun getLunarMonthsInYear(year: Int): List<Pair<Int, Boolean>> {
        val months = mutableListOf<Pair<Int, Boolean>>()
        
        // 闰月数据表（作为备用方案）
        val knownLeapMonths = mapOf(
            2023 to 2,  // 闰二月
            2025 to 6,  // 闰六月
            2028 to 5,  // 闰五月
            2031 to 3,  // 闰三月
            2033 to 11, // 闰十一月
            2036 to 6,  // 闰六月
            2039 to 5,  // 闰五月
            2042 to 2,  // 闰二月
            2044 to 7   // 闰七月
        )
        
        // 检查是否有闰月
        var leapMonth = 0
        try {
            // 方法1: 使用LunarYear API
            val lunarYear = com.nlf.calendar.LunarYear.fromYear(year)
            leapMonth = lunarYear.leapMonth
            android.util.Log.d("LunarCalendarHelper", "Method 1 - Year $year leap month: $leapMonth")
            
            // 方法2: 如果API失败，使用已知数据
            if (leapMonth == 0 && knownLeapMonths.containsKey(year)) {
                leapMonth = knownLeapMonths[year]!!
                android.util.Log.d("LunarCalendarHelper", "Method 2 - Using known data, year $year leap month: $leapMonth")
            }
            
            // 方法3: 通过遍历每个月份来检测闰月
            if (leapMonth == 0) {
                android.util.Log.d("LunarCalendarHelper", "Method 1&2 failed, trying method 3")
                for (month in 1..12) {
                    try {
                        // 尝试创建闰月日期，如果成功则说明有闰月
                        val testLunar = com.nlf.calendar.Lunar(year, -month, 1)
                        val testSolar = testLunar.solar
                        if (testSolar.year == year || testSolar.year == year + 1) {
                            leapMonth = month
                            android.util.Log.d("LunarCalendarHelper", "Method 3 - Found leap month $month for year $year")
                            break
                        }
                    } catch (e: Exception) {
                        // 这个月份没有闰月，继续下一个
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LunarCalendarHelper", "Error getting leap month for year $year", e)
            // 如果所有方法都失败，尝试使用已知数据
            if (knownLeapMonths.containsKey(year)) {
                leapMonth = knownLeapMonths[year]!!
                android.util.Log.d("LunarCalendarHelper", "Fallback - Using known data, year $year leap month: $leapMonth")
            }
        }
        
        // 按顺序添加月份，在闰月位置后插入闰月
        for (month in 1..12) {
            months.add(Pair(month, false)) // 正常月份
            
            // 如果当前月份是闰月，在其后插入闰月
            if (leapMonth > 0 && month == leapMonth) {
                months.add(Pair(month, true)) // 闰月
                android.util.Log.d("LunarCalendarHelper", "Added leap month $month for year $year")
            }
        }
        
        android.util.Log.d("LunarCalendarHelper", "Total months for year $year: ${months.size}, months: $months")
        return months
    }
    
    /**
     * 计算农历事件的准确倒计时天数，正确处理闰月影响
     * 采用"当年剩余 + 次年到事件"的合计方式
     */
    fun calculateLunarCountdown(
        eventYear: Int,
        eventMonth: Int, 
        eventDay: Int,
        isEventLeap: Boolean,
        currentDate: LocalDate
    ): Long {
        return try {
            val currentYear = currentDate.year
            
            // 添加测试验证用户的分析
            if (currentYear == 2025 && eventMonth == 6 && eventDay == 16) {
                try {
                    val normal = lunarToSolar(2025, 6, 16, false)
                    val leap = lunarToSolar(2025, 6, 16, true)
                    val leapMonth = getLeapMonth(2025)
                    Log.d("LunarTest", "=== 验证用户分析 ===")
                    Log.d("LunarTest", "2025年闰月: $leapMonth")
                    Log.d("LunarTest", "2025年农历六月十六（普通月）-> $normal")
                    Log.d("LunarTest", "2025年农历闰六月十六 -> $leap")
                    Log.d("LunarTest", "当前日期: $currentDate")
                    Log.d("LunarTest", "事件类型: ${if (isEventLeap) "闰月" else "普通月"}")
                    
                    // 比较日期
                    val normalDate = LocalDate.parse(normal)
                    val leapDate = LocalDate.parse(leap)
                    Log.d("LunarTest", "普通月日期: $normalDate, 是否已过: ${normalDate.isBefore(currentDate)}")
                    Log.d("LunarTest", "闰月日期: $leapDate, 是否已过: ${leapDate.isBefore(currentDate)}")
                } catch (e: Exception) {
                    Log.e("LunarTest", "测试失败", e)
                }
            }
            
            // 先尝试当年的事件日期
            val thisYearEventDate = try {
                val solarDate = lunarToSolar(currentYear, eventMonth, eventDay, isEventLeap)
                Log.d("LunarCalendarHelper", "当年事件转换结果: 农历${currentYear}年${eventMonth}月${eventDay}日(闰月:$isEventLeap) -> 阳历$solarDate")
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                Log.e("LunarCalendarHelper", "当年事件转换失败", e)
                null
            }
            
            // 如果当年事件日期有效且未过期，直接计算
            if (thisYearEventDate != null && !thisYearEventDate.isBefore(currentDate)) {
                val days = ChronoUnit.DAYS.between(currentDate, thisYearEventDate)
                Log.d("LunarCalendarHelper", "当年事件未过期: $thisYearEventDate，剩余${days}天")
                return days
            } else {
                Log.d("LunarCalendarHelper", "当年事件已过期或不存在: $thisYearEventDate，需要跨年计算")
            }
            
            // 当年事件已过期或不存在，计算跨年天数
            Log.d("LunarCalendarHelper", "开始跨年计算: 事件农历${eventMonth}月${eventDay}日(闰月:$isEventLeap)")
            return calculateCrossYearLunarDays(currentDate, eventMonth, eventDay, isEventLeap)
            
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "计算农历倒计时失败", e)
            365L
        }
    }
    
    /**
     * 计算跨年农历天数：正确处理闰月对天数的影响
     * 根据用户分析重构：普通月需包含闰月天数，闰月需找下一个对应年份
     */
    private fun calculateCrossYearLunarDays(
        currentDate: LocalDate,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean
    ): Long {
        val currentYear = currentDate.year
        
        return try {
            if (isEventLeap) {
                // 闰月事件：找下一个有对应闰月的年份
                calculateLeapMonthEventDays(currentDate, eventMonth, eventDay)
            } else {
                // 普通月事件：需要正确处理闰月影响
                calculateNormalMonthEventDays(currentDate, eventMonth, eventDay)
            }
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "跨年计算失败", e)
            365L
        }
    }
    
    /**
     * 计算闰月事件的跨年天数
     * 今年闰月未到：直接返回剩余天数
     * 今年闰月已过：今年剩余 + 明年到普通月（考虑闰月影响）
     */
    private fun calculateLeapMonthEventDays(
        currentDate: LocalDate,
        eventMonth: Int,
        eventDay: Int
    ): Long {
        val currentYear = currentDate.year
        val nextYear = currentYear + 1
        
        try {
            // 第一步：计算今年剩余天数（当前日期 -> 闰月日期 -> 年底）
            // 先计算到闰月日期的天数（如果还没到）
            val thisYearLeapEventDate = try {
                val solarDate = lunarToSolar(currentYear, eventMonth, eventDay, true)
                Log.d("LunarCalendarHelper", "当年闰月事件转换结果: 农历${currentYear}年闰${eventMonth}月${eventDay}日 -> 阳历$solarDate")
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                Log.e("LunarCalendarHelper", "当年闰月事件转换失败", e)
                null
            }
            
            val daysLeftThisYear = if (thisYearLeapEventDate != null && !thisYearLeapEventDate.isBefore(currentDate)) {
                // 今年闰月事件还没到，直接计算到事件的天数
                val daysToEvent = ChronoUnit.DAYS.between(currentDate, thisYearLeapEventDate)
                Log.d("LunarCalendarHelper", "今年闰月事件未到: $thisYearLeapEventDate，还有${daysToEvent}天")
                return daysToEvent // 直接返回，不需跨年
            } else {
                // 今年闰月事件已过，计算到年底的天数
                val yearEnd = LocalDate.of(currentYear, 12, 31)
                val daysToYearEnd = ChronoUnit.DAYS.between(currentDate, yearEnd) + 1
                Log.d("LunarCalendarHelper", "今年闰月事件已过: $thisYearLeapEventDate，到年底还有${daysToYearEnd}天")
                daysToYearEnd
            }
            
            // 第二步：计算明年从年初到普通月事件的天数（考虑闰月影响）
            val nextYearLeapMonth = getLeapMonth(nextYear)
            val nextYearEventDate = if (nextYearLeapMonth > 0 && nextYearLeapMonth <= eventMonth) {
                // 明年有闰月且在事件月份之前或同月，普通月会被推后
                try {
                    val solarDate = lunarToSolar(nextYear, eventMonth, eventDay, false)
                    LocalDate.parse(solarDate)
                } catch (e: Exception) {
                    LocalDate.of(nextYear, eventMonth.coerceIn(1, 12), eventDay.coerceIn(1, 28))
                }
            } else {
                // 明年无闰月或闰月在事件月份之后
                try {
                    val solarDate = lunarToSolar(nextYear, eventMonth, eventDay, false)
                    LocalDate.parse(solarDate)
                } catch (e: Exception) {
                    LocalDate.of(nextYear, eventMonth.coerceIn(1, 12), eventDay.coerceIn(1, 28))
                }
            }
            
            val daysToEventNextYear = ChronoUnit.DAYS.between(LocalDate.of(nextYear, 1, 1), nextYearEventDate)
            val totalDays = daysLeftThisYear + daysToEventNextYear
            
            Log.d("LunarCalendarHelper", "闰月跨年计算: 今年剩余${daysLeftThisYear}天 + 明年到普通月${daysToEventNextYear}天 = 总计${totalDays}天")
            Log.d("LunarCalendarHelper", "事件日期: $nextYearEventDate")
            
            return totalDays
            
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "闰月跨年计算失败", e)
            return 365L
        }
    }
    
    /**
     * 计算普通月事件的跨年天数
     * 正确理解：普通月在闰月之前，不会被“推后”
     * 今年普通月未到：直接返回剩余天数
     * 今年普通月已过：今年剩余 + 明年到普通月
     */
    private fun calculateNormalMonthEventDays(
        currentDate: LocalDate,
        eventMonth: Int,
        eventDay: Int
    ): Long {
        val currentYear = currentDate.year
        val nextYear = currentYear + 1
        
        try {
            // 第一步：计算今年剩余天数（当前日期 -> 闰月日期 -> 普通月日期）
            val thisYearLeapMonth = getLeapMonth(currentYear)
            
            // 先检查今年普通月事件是否还没到
            val thisYearNormalEventDate = try {
                val solarDate = lunarToSolar(currentYear, eventMonth, eventDay, false)
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                null
            }
            
            val daysLeftThisYear = if (thisYearNormalEventDate != null && !thisYearNormalEventDate.isBefore(currentDate)) {
                // 今年普通月事件还没到，直接计算到事件的天数
                val daysToEvent = ChronoUnit.DAYS.between(currentDate, thisYearNormalEventDate)
                Log.d("LunarCalendarHelper", "今年普通月事件未到: $thisYearNormalEventDate，还有${daysToEvent}天")
                return daysToEvent // 直接返回，不需跨年
            } else {
                // 今年普通月事件已过，需要计算跨年天数
                // 计算到年底的天数
                val yearEnd = LocalDate.of(currentYear, 12, 31)
                val daysToYearEnd = ChronoUnit.DAYS.between(currentDate, yearEnd) + 1
                Log.d("LunarCalendarHelper", "今年普通月事件已过，到年底还有${daysToYearEnd}天")
                
                // 普通月在闰月之前，不受闰月影响
                if (thisYearLeapMonth > 0) {
                    Log.d("LunarCalendarHelper", "今年有闰${thisYearLeapMonth}月，但不影响普通${eventMonth}月")
                }
                
                daysToYearEnd
            }
            
            // 第二步：计算明年从年初到普通月事件的天数（考虑闰月影响）
            val nextYearLeapMonth = getLeapMonth(nextYear)
            val nextYearEventDate = if (nextYearLeapMonth > 0 && nextYearLeapMonth <= eventMonth) {
                // 明年有闰月且在事件月份之前或同月，普通月会被推后
                try {
                    val solarDate = lunarToSolar(nextYear, eventMonth, eventDay, false)
                    LocalDate.parse(solarDate)
                } catch (e: Exception) {
                    LocalDate.of(nextYear, eventMonth.coerceIn(1, 12), eventDay.coerceIn(1, 28))
                }
            } else {
                // 明年无闰月或闰月在事件月份之后
                try {
                    val solarDate = lunarToSolar(nextYear, eventMonth, eventDay, false)
                    LocalDate.parse(solarDate)
                } catch (e: Exception) {
                    LocalDate.of(nextYear, eventMonth.coerceIn(1, 12), eventDay.coerceIn(1, 28))
                }
            }
            
            val daysToEventNextYear = ChronoUnit.DAYS.between(LocalDate.of(nextYear, 1, 1), nextYearEventDate)
            val totalDays = daysLeftThisYear + daysToEventNextYear
            
            Log.d("LunarCalendarHelper", "普通月跨年计算: 今年剩余${daysLeftThisYear}天 + 明年到事件${daysToEventNextYear}天 = 总计${totalDays}天")
            Log.d("LunarCalendarHelper", "今年闰月: $thisYearLeapMonth, 事件日期: $nextYearEventDate")
            
            return totalDays
            
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "普通月跨年计算失败", e)
            return 365L
        }
    }
    
    /**
     * 计算考虑闰月影响的事件日期
     */
    private fun calculateEventDateWithLeapInfluence(
        year: Int,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean,
        yearLeapMonth: Int
    ): LocalDate? {
        return try {
            when {
                yearLeapMonth <= 0 -> {
                    // 该年没有闰月，正常计算
                    val solarDate = lunarToSolar(year, eventMonth, eventDay, isEventLeap)
                    LocalDate.parse(solarDate)
                }
                yearLeapMonth < eventMonth -> {
                    // 闰月在事件月份之前，事件日期会被推迟
                    val solarDate = lunarToSolar(year, eventMonth, eventDay, isEventLeap)
                    LocalDate.parse(solarDate)
                }
                yearLeapMonth > eventMonth -> {
                    // 闰月在事件月份之后，不影响事件日期
                    val solarDate = lunarToSolar(year, eventMonth, eventDay, isEventLeap)
                    LocalDate.parse(solarDate)
                }
                else -> {
                    // yearLeapMonth == eventMonth，即闰月就是事件月份
                    if (isEventLeap) {
                        // 事件就在闰月中
                        val solarDate = lunarToSolar(year, eventMonth, eventDay, true)
                        LocalDate.parse(solarDate)
                    } else {
                        // 事件在普通月，但该月有闰月，所以普通月会在闰月之后
                        val solarDate = lunarToSolar(year, eventMonth, eventDay, false)
                        LocalDate.parse(solarDate)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "计算带闰月影响的日期失败", e)
            null
        }
    }
    

    
    /**
     * 检查指定年份是否有闰月
     */
    fun hasLeapMonth(year: Int): Boolean {
        return try {
            val lunarYear = com.nlf.calendar.LunarYear.fromYear(year)
            lunarYear.leapMonth > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取指定年份的闰月月份（如果有）
     */
    fun getLeapMonth(year: Int): Int {
        return try {
            val lunarYear = com.nlf.calendar.LunarYear.fromYear(year)
            lunarYear.leapMonth
        } catch (e: Exception) {
            0
        }
    }

    fun getDayName(day: Int): String {
        return getLunarDayName(day)
    }
}
