import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import com.nlf.calendar.Lunar

fun main() {
    // 测试2025年闰月信息
    println("=== 测试2025年闰月信息 ===")
    
    try {
        val lunarYear2025 = LunarYear.fromYear(2025)
        val leapMonth = lunarYear2025.leapMonth
        println("2025年闰月: $leapMonth")
        
        if (leapMonth > 0) {
            println("2025年有闰${leapMonth}月")
            
            // 测试闰月的具体日期
            try {
                val leapLunar = Lunar(2025, -leapMonth, 1) // 负数表示闰月
                val solar = leapLunar.solar
                println("闰${leapMonth}月初一对应阳历: ${solar.year}-${solar.month}-${solar.day}")
            } catch (e: Exception) {
                println("闰月日期转换失败: ${e.message}")
            }
        } else {
            println("2025年没有闰月")
        }
        
    } catch (e: Exception) {
        println("获取2025年闰月信息失败: ${e.message}")
        e.printStackTrace()
    }
    
    // 测试其他已知有闰月的年份
    val testYears = listOf(2023, 2025, 2028, 2031)
    println("\n=== 测试多个年份的闰月信息 ===")
    
    for (year in testYears) {
        try {
            val lunarYear = LunarYear.fromYear(year)
            val leapMonth = lunarYear.leapMonth
            if (leapMonth > 0) {
                println("$year 年有闰${leapMonth}月")
            } else {
                println("$year 年没有闰月")
            }
        } catch (e: Exception) {
            println("$year 年获取闰月信息失败: ${e.message}")
        }
    }
    
    // 测试当前Solar转Lunar是否能识别闰月
    println("\n=== 测试Solar转Lunar闰月识别 ===")
    try {
        // 2025年闰六月的一些日期
        val testDates = listOf(
            Triple(2025, 7, 25), // 可能是闰六月的日期
            Triple(2025, 8, 23), // 可能是闰六月的日期
            Triple(2025, 9, 21)  // 可能是闰六月的日期
        )
        
        for ((year, month, day) in testDates) {
            try {
                val solar = Solar(year, month, day)
                val lunar = solar.lunar
                val isLeap = lunar.month < 0
                val actualMonth = if (isLeap) -lunar.month else lunar.month
                println("阳历 $year-$month-$day -> 农历 ${lunar.year}-${if (isLeap) "闰" else ""}${actualMonth}-${lunar.day}")
            } catch (e: Exception) {
                println("日期 $year-$month-$day 转换失败: ${e.message}")
            }
        }
    } catch (e: Exception) {
        println("Solar转Lunar测试失败: ${e.message}")
    }
}
