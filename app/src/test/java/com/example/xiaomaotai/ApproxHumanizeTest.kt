package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Countdown.humanizeApprox] 的单元测试。
 *
 * 这个函数直接决定卡片上「约 N 年 N 月」那行附属文字，
 * 算错会让用户看到「约 1 年 12 月」这种明显不合理的值。
 */
class ApproxHumanizeTest {

    private fun approx(days: Long) = Countdown.humanizeApprox(days)

    @Test
    fun `不足一个月返回null不显示注释`() {
        assertNull(approx(0))
        assertNull(approx(1))
        assertNull(approx(15))
        assertNull(approx(29))
    }

    @Test
    fun `满30天开始显示约N月`() {
        assertEquals("约1月", approx(30))
        assertEquals("约1月", approx(36))
        assertEquals("约2月", approx(60))
        assertEquals("约3月", approx(90))
    }

    @Test
    fun `满一年显示约N年而不是约12月`() {
        assertEquals("约1年", approx(365))
        assertEquals("约2年", approx(730))
        assertEquals("约3年", approx(1095))
        assertEquals("约5年", approx(1825))
        assertEquals("约10年", approx(3650))
    }

    @Test
    fun `整年带余数时同时显示年月`() {
        assertEquals("约1年1月", approx(400))
        assertEquals("约1年4月", approx(500))
        assertEquals("约2年9月", approx(1000))
        assertEquals("约5年6月", approx(2000))
    }

    @Test
    fun `永远不出现N年12月这种丑结果`() {
        // 全量扫描：换算逻辑用总月数取整，12 个月会进位成年，不该出现「年12月」
        for (d in 0L..20000L) {
            val text = approx(d)
            if (text != null) {
                assertFalse("$d 天算出了「$text」", text.contains("年12月"))
            }
        }
    }

    @Test
    fun `负数按绝对值处理`() {
        assertEquals("约5年", approx(-1825))
        assertEquals("约1月", approx(-36))
        assertNull(approx(-29))
    }

    @Test
    fun `文案始终以约字开头表示近似`() {
        listOf(30L, 100L, 365L, 400L, 1825L).forEach { d ->
            val text = approx(d)
            assertNotNull("$d 天应有注释", text)
            assertTrue("$d 天的注释应以「约」开头：$text", text!!.startsWith("约"))
        }
    }

    @Test
    fun `月数始终落在1到11之间`() {
        // 「约N年M月」里的 M 必须是 1..11，否则说明进位逻辑有误
        for (d in 30L..20000L) {
            val text = approx(d) ?: continue
            val yearMonthMatch = Regex("约(\\d+)年(\\d+)月").find(text)
            if (yearMonthMatch != null) {
                val month = yearMonthMatch.groupValues[2].toInt()
                assertTrue("$d 天算出月份 $month 越界：$text", month in 1..11)
            }
        }
    }
}
