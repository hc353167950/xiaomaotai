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
     * 统一逻辑：
     * - 如果事件年份在未来，直接计算到该年份的农历日期
     * - 否则按照"普通月→闰月（如果有）→明年"的顺序提醒
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

            Log.d("LunarCalendarHelper", "开始农历计算: 事件农历${eventYear}年${if (isEventLeap) "闰" else ""}${eventMonth}月${eventDay}日, 当前公历年份: $currentYear")

            // 首先将农历日期转换为公历日期，然后与当前日期比较
            // 不能直接比较农历年份和公历年份，因为农历腊月可能对应下一个公历年
            val eventSolarDate = try {
                val solarDate = lunarToSolar(eventYear, eventMonth, eventDay, isEventLeap)
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                Log.e("LunarCalendarHelper", "农历转公历失败: 农历${eventYear}年${if (isEventLeap) "闰" else ""}${eventMonth}月${eventDay}日", e)
                null
            }

            if (eventSolarDate != null) {
                Log.d("LunarCalendarHelper", "农历${eventYear}年${if (isEventLeap) "闰" else ""}${eventMonth}月${eventDay}日 -> 公历${eventSolarDate}")
                
                // 如果事件的公历日期在今天或之后，直接计算天数差
                if (!eventSolarDate.isBefore(currentDate)) {
                    val daysToEvent = ChronoUnit.DAYS.between(currentDate, eventSolarDate)
                    Log.d("LunarCalendarHelper", "✅ 事件公历日期未过: $eventSolarDate，还有${daysToEvent}天")
                    return daysToEvent
                }
                
                Log.d("LunarCalendarHelper", "⚠️ 事件公历日期已过: $eventSolarDate，按循环逻辑计算下一次")
            }

            // 事件日期已过，按循环逻辑计算下一次（今年或明年的同农历日期）
            return calculateUnifiedLunarEventDays(currentDate, eventMonth, eventDay)

        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "计算农历倒计时失败", e)
            365L
        }
    }
    
    /**
     * 计算跨年农历天数：正确处理闰月对天数的影响
     * 统一逻辑：无论用户添加的是普通月还是闰月事件，都按照"普通月→闰月（如果有）→明年"的顺序提醒
     */
    private fun calculateCrossYearLunarDays(
        currentDate: LocalDate,
        eventMonth: Int,
        eventDay: Int,
        isEventLeap: Boolean
    ): Long {
        val currentYear = currentDate.year

        return try {
            // 统一处理：普通月和闰月事件都按照相同逻辑
            // 先提醒普通月，普通月过后提醒闰月（如果有），都过后到明年
            calculateUnifiedLunarEventDays(currentDate, eventMonth, eventDay)
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "跨年计算失败", e)
            365L
        }
    }
    
    /**
     * 统一处理农历事件的倒计时逻辑
     * 无论用户添加的是普通月还是闰月，都按照"普通月→闰月（如果有）→明年"的顺序
     */
    private fun calculateUnifiedLunarEventDays(
        currentDate: LocalDate,
        eventMonth: Int,
        eventDay: Int
    ): Long {
        try {
            // 首先获取当前日期对应的农历年份（重要！农历年份可能与公历年份不同）
            val currentSolar = Solar(currentDate.year, currentDate.monthValue, currentDate.dayOfMonth)
            val currentLunar = currentSolar.lunar
            val currentLunarYear = currentLunar.year
            val currentLunarMonth = currentLunar.month
            val currentLunarDay = currentLunar.day
            
            Log.d("LunarCalendarHelper", "当前公历: $currentDate -> 农历: ${currentLunarYear}年${currentLunarMonth}月${currentLunarDay}日")

            // 步骤1: 检查当前农历年的普通月事件是否还没到
            val thisYearNormalEventDate = try {
                val solarDate = lunarToSolar(currentLunarYear, eventMonth, eventDay, false)
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                Log.e("LunarCalendarHelper", "当前农历年普通月转换失败", e)
                null
            }

            if (thisYearNormalEventDate != null && !thisYearNormalEventDate.isBefore(currentDate)) {
                // 当前农历年普通月事件还没到，直接返回到普通月的天数
                val daysToEvent = ChronoUnit.DAYS.between(currentDate, thisYearNormalEventDate)
                Log.d("LunarCalendarHelper", "✅ 当前农历年普通月未到: $thisYearNormalEventDate，还有${daysToEvent}天")
                return daysToEvent
            }

            Log.d("LunarCalendarHelper", "⚠️ 当前农历年普通月已过: $thisYearNormalEventDate，检查是否有闰月")

            // 步骤2: 普通月已过，检查当前农历年是否有对应的闰月
            val thisYearLeapMonth = getLeapMonth(currentLunarYear)
            Log.d("LunarCalendarHelper", "📅 当前农历年闰月: ${if (thisYearLeapMonth > 0) "${thisYearLeapMonth}月" else "无"}")

            if (thisYearLeapMonth == eventMonth) {
                // 当前农历年有对应的闰月，检查闰月日期是否还没到
                val thisYearLeapEventDate = try {
                    val solarDate = lunarToSolar(currentLunarYear, eventMonth, eventDay, true)
                    LocalDate.parse(solarDate)
                } catch (e: Exception) {
                    Log.e("LunarCalendarHelper", "当前农历年闰月转换失败", e)
                    null
                }

                if (thisYearLeapEventDate != null && !thisYearLeapEventDate.isBefore(currentDate)) {
                    // 闰月日期还没到，返回到闰月的天数
                    val daysToLeapEvent = ChronoUnit.DAYS.between(currentDate, thisYearLeapEventDate)
                    Log.d("LunarCalendarHelper", "✅ 当前农历年闰月未到: $thisYearLeapEventDate，还有${daysToLeapEvent}天")
                    return daysToLeapEvent
                } else {
                    Log.d("LunarCalendarHelper", "⚠️ 当前农历年闰月已过: $thisYearLeapEventDate")
                }
            } else {
                Log.d("LunarCalendarHelper", "⚠️ 当前农历年没有对应的闰${eventMonth}月")
            }

            // 步骤3: 普通月和闰月都过了（或当前农历年没有闰月），计算到下一个农历年普通月的天数
            Log.d("LunarCalendarHelper", "📆 开始计算到下一个农历年普通月的天数")

            val nextLunarYear = currentLunarYear + 1
            val nextYearEventDate = try {
                val solarDate = lunarToSolar(nextLunarYear, eventMonth, eventDay, false)
                LocalDate.parse(solarDate)
            } catch (e: Exception) {
                Log.e("LunarCalendarHelper", "下一农历年普通月转换失败", e)
                LocalDate.of(currentDate.year + 1, eventMonth.coerceIn(1, 12), eventDay.coerceIn(1, 28))
            }

            // 简化计算：直接计算当前日期到下一农历年事件日期的天数差
            val totalDays = ChronoUnit.DAYS.between(currentDate, nextYearEventDate)

            Log.d("LunarCalendarHelper", "✅ 跨年计算: 从$currentDate 到 $nextYearEventDate = ${totalDays}天")

            return totalDays

        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "统一农历计算失败", e)
            return 365L
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

    /**
     * 获取农历月份的实际天数（大月30天，小月29天）
     * @param year 农历年份
     * @param month 农历月份（1-12）
     * @param isLeap 是否为闰月
     * @return 该月的天数（29或30）
     */
    fun getLunarMonthDays(year: Int, month: Int, isLeap: Boolean): Int {
        return try {
            val lunarYear = com.nlf.calendar.LunarYear.fromYear(year)
            val months = lunarYear.months

            // 查找对应的月份
            for (lunarMonth in months) {
                val monthValue = lunarMonth.month
                val isMonthLeap = lunarMonth.isLeap

                if (kotlin.math.abs(monthValue) == month && isMonthLeap == isLeap) {
                    return lunarMonth.dayCount
                }
            }

            // 如果没找到，返回默认值30
            Log.w("LunarCalendarHelper", "未找到农历${year}年${if (isLeap) "闰" else ""}${month}月，使用默认30天")
            30
        } catch (e: Exception) {
            Log.e("LunarCalendarHelper", "获取农历月份天数失败: ${year}年${if (isLeap) "闰" else ""}${month}月", e)
            30 // 出错时返回默认值
        }
    }
}
