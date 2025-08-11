package com.example.xiaomaotai

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.util.*

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
        val monthName = getLunarMonthName(lunar.month.coerceIn(1, 12))
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

    fun getDayName(day: Int): String {
        return getLunarDayName(day)
    }
}
