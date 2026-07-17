package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Countdown 深模块的单元测试：全部通过 Countdown.of(eventDate, today) 这一个接口进入，
 * 固定注入 today，覆盖公历/农历/忽略年份/闰月/2月29日/跨年循环等边界。
 */
class CountdownTest {

    // ---------- 公历（yyyy-MM-dd） ----------

    @Test
    fun `公历-未来日期直接计算天数`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("2026-07-20", today)!!
        assertEquals(3L, c.days)
        assertEquals("还有3天", c.label)
        assertEquals(LocalDate.of(2026, 7, 20), c.nextDate)
    }

    @Test
    fun `公历-就是今天`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("2026-07-17", today)!!
        assertEquals(0L, c.days)
        assertEquals("就是今天", c.label)
    }

    @Test
    fun `公历-明天的文案`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("2026-07-18", today)!!
        assertEquals(1L, c.days)
        assertEquals("明天", c.label)
    }

    @Test
    fun `公历-过去日期循环到明年同月日`() {
        val today = LocalDate.of(2026, 7, 17)
        // 2020-07-16 已过，下一次是 2027-07-16
        val c = Countdown.of("2020-07-16", today)!!
        assertEquals(LocalDate.of(2027, 7, 16), c.nextDate)
        assertEquals(364L, c.days)
    }

    @Test
    fun `公历-今年生日已过取明年`() {
        val today = LocalDate.of(2026, 12, 31)
        val c = Countdown.of("1990-01-01", today)!!
        assertEquals(1L, c.days)
        assertEquals(LocalDate.of(2027, 1, 1), c.nextDate)
        assertEquals("明天", c.label)
    }

    // ---------- 忽略年份（MM-dd） ----------

    @Test
    fun `忽略年份-今年未到直接计算`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("12-18", today)!!
        assertEquals(LocalDate.of(2026, 12, 18), c.nextDate)
        assertEquals(154L, c.days)
    }

    @Test
    fun `忽略年份-今年已过循环到明年`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("01-01", today)!!
        assertEquals(LocalDate.of(2027, 1, 1), c.nextDate)
    }

    @Test
    fun `忽略年份-当天为零天`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("07-17", today)!!
        assertEquals(0L, c.days)
        assertEquals("就是今天", c.label)
    }

    // ---------- 2月29日边界 ----------

    @Test
    fun `闰日-非闰年降级为2月28日`() {
        // 2026 非闰年：2月29日的纪念日按 2月28日 计算
        val today = LocalDate.of(2026, 2, 1)
        val c = Countdown.of("02-29", today)!!
        assertEquals(LocalDate.of(2026, 2, 28), c.nextDate)
        assertEquals(27L, c.days)
    }

    @Test
    fun `闰日-闰年正常使用2月29日`() {
        val today = LocalDate.of(2028, 2, 1) // 2028 是闰年
        val c = Countdown.of("02-29", today)!!
        assertEquals(LocalDate.of(2028, 2, 29), c.nextDate)
        assertEquals(28L, c.days)
    }

    // ---------- 农历（lunar:yyyy-MM-dd） ----------

    @Test
    fun `农历-带年份事件未来直接计算`() {
        val today = LocalDate.of(2026, 7, 17)
        // 农历2026年六月十六 = 公历2026-07-29（lunar-java 转换）
        val c = Countdown.of("lunar:2026-06-16", today)!!
        assertTrue("农历事件应在未来且天数>0，实际 ${c.days}", c.days > 0)
        assertEquals(today.plusDays(c.days), c.nextDate)
    }

    @Test
    fun `农历-已过循环到下一个农历年`() {
        val today = LocalDate.of(2026, 7, 17)
        // 农历2020年正月初一早已过去，应循环到下一次正月初一
        val c = Countdown.of("lunar:2020-01-01", today)!!
        assertTrue("循环后的天数应>0，实际 ${c.days}", c.days > 0)
        assertTrue("距下个春节不会超过400天", c.days < 400)
    }

    @Test
    fun `农历-忽略年份按当前农历年循环`() {
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("lunar:06-16", today)!!
        assertTrue(c.days >= 0)
        assertEquals(today.plusDays(c.days), c.nextDate)
    }

    @Test
    fun `农历-忽略年份闰月格式可解析`() {
        val today = LocalDate.of(2026, 7, 17)
        // lunar:L06-16 = 忽略年份的闰六月十六
        val c = Countdown.of("lunar:L06-16", today)
        assertNotNull("闰月格式应能解析", c)
        assertTrue(c!!.days >= 0)
    }

    // ---------- 非法输入 ----------

    @Test
    fun `非法格式返回null`() {
        val today = LocalDate.of(2026, 7, 17)
        assertNull(Countdown.of("not-a-date", today))
        assertNull(Countdown.of("", today))
        assertNull(Countdown.of("2026/07/17", today))
    }

    // ---------- 文案统一性 ----------

    @Test
    fun `labelFor-统一文案约定`() {
        assertEquals("就是今天", Countdown.labelFor(0))
        assertEquals("明天", Countdown.labelFor(1))
        assertEquals("昨天", Countdown.labelFor(-1))
        assertEquals("还有30天", Countdown.labelFor(30))
        assertEquals("5天前", Countdown.labelFor(-5))
    }

    @Test
    fun `DateParser统一入口与Countdown结果一致`() {
        val today = LocalDate.of(2026, 7, 17)
        val (label, days) = DateParser.calculateDaysUntil("12-18", today)
        val c = Countdown.of("12-18", today)!!
        assertEquals(c.label, label)
        assertEquals(c.days, days)
    }
}
