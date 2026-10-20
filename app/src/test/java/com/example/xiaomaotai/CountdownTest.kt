package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Countdown 深模块的单元测试：全部通过 Countdown.of(eventDate, today) 这一个接口进入，
 * 固定注入 today，覆盖公历/农历/忽略年份/闰月顺延/2月29日/跨年循环等边界。
 *
 * 农历闰月语义（本次变更）：
 * - 闰月事件只在真正存在该闰月的年份发生，当年没有则**顺延**到最近的闰月年；
 * - 绝不降级为普通月。
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
    }

    // ---------- 闰月年「两次」语义（本次核心变更） ----------

    @Test
    fun `闰月年-普通月事件先命中普通月那天`() {
        // 2025 农历年有闰六月：普通六月十六 = 2025-07-10
        val today = LocalDate.of(2025, 7, 5)
        val c = Countdown.of("lunar:06-16", today)!!
        assertEquals(5L, c.days)
        assertEquals(LocalDate.of(2025, 7, 10), c.nextDate)
        assertTrue("这一次不是闰月", !c.isLeapOccurrence)
        // 该年有两个候选日期
        assertEquals(
            listOf(LocalDate.of(2025, 7, 10), LocalDate.of(2025, 8, 9)),
            c.occurrences
        )
    }

    @Test
    fun `闰月年-普通月那天过后接着命中闰月那天`() {
        // 这是修复前的 Bug：普通六月十六已过时，旧实现直接跳到 2026 年，
        // 漏掉了闰六月十六（2025-08-09）
        val today = LocalDate.of(2025, 7, 15)
        val c = Countdown.of("lunar:06-16", today)!!
        assertEquals(25L, c.days)
        assertEquals(LocalDate.of(2025, 8, 9), c.nextDate)
        assertTrue("这一次命中闰月", c.isLeapOccurrence)
    }

    @Test
    fun `闰月年-两次都过完才到下一农历年`() {
        val today = LocalDate.of(2025, 8, 10)
        val c = Countdown.of("lunar:06-16", today)!!
        // 闰六月十六已过 -> 2026 年普通六月十六
        assertEquals(LocalDate.of(2026, 7, 29), c.nextDate)
        assertEquals(353L, c.days)
        assertTrue("下一年只有一次", !c.isLeapOccurrence)
    }

    @Test
    fun `非闰月年-普通月事件只有一次`() {
        // 2026 农历年无闰六月
        val today = LocalDate.of(2026, 7, 17)
        val c = Countdown.of("lunar:06-16", today)!!
        assertEquals(12L, c.days)
        assertEquals(LocalDate.of(2026, 7, 29), c.nextDate)
        assertEquals(1, c.occurrences.size)
    }

    // ---------- 闰月事件：无闰月年份退回普通月 ----------

    @Test
    fun `闰月事件-当年有此闰月用闰月那天`() {
        // 2025 有闰六月，闰六月十六 = 2025-08-09
        val today = LocalDate.of(2025, 3, 1)
        val c = Countdown.of("lunar:L06-16", today)!!
        assertEquals(LocalDate.of(2025, 8, 9), c.nextDate)
        assertEquals(161L, c.days)
        assertTrue(c.isLeapOccurrence)
    }

    @Test
    fun `闰月事件-当年无此闰月退回普通月而非顺延几十年`() {
        // 2026 无闰六月：应退回普通六月十六（2026-07-29），
        // 而不是旧实现的「顺延到 2036 年」
        val today = LocalDate.of(2026, 3, 1)
        val c = Countdown.of("lunar:L06-16", today)!!
        assertEquals(LocalDate.of(2026, 7, 29), c.nextDate)
        assertEquals(150L, c.days)
        assertTrue("退回普通月，不算闰月命中", !c.isLeapOccurrence)
    }

    @Test
    fun `闰月事件-带年份在无闰月年份退回普通月`() {
        val today = LocalDate.of(2026, 3, 1)
        val c = Countdown.of("lunar:2025-L06-16", today)!!
        assertEquals(LocalDate.of(2026, 7, 29), c.nextDate)
        assertEquals(150L, c.days)
    }

    @Test
    fun `闰正月-历史上不存在退回普通正月初一`() {
        // 闰正月在 1900-2200 不存在，应退回普通正月初一，而不是「日期无效」
        val today = LocalDate.of(2026, 3, 1)
        val c = Countdown.of("lunar:L01-01", today)
        assertNotNull("应退回普通月而非返回 null", c)
        assertEquals(LocalDate.of(2027, 2, 6), c!!.nextDate)
        assertEquals(342L, c.days)
    }

    // ---------- 2023 闰二月（另一个闰月年验证） ----------

    @Test
    fun `闰二月年-两次提醒`() {
        // 2023 普通二月十六 = 2023-03-07，闰二月十六 = 2023-04-06
        val beforeNormal = Countdown.of("lunar:02-16", LocalDate.of(2023, 3, 5))!!
        assertEquals(LocalDate.of(2023, 3, 7), beforeNormal.nextDate)
        assertEquals(2L, beforeNormal.days)

        val beforeLeap = Countdown.of("lunar:02-16", LocalDate.of(2023, 4, 1))!!
        assertEquals(LocalDate.of(2023, 4, 6), beforeLeap.nextDate)
        assertEquals(5L, beforeLeap.days)
        assertTrue(beforeLeap.isLeapOccurrence)
    }

    // ---------- 农历三十遇小月（顺延） ----------

    @Test
    fun `农历三十-遇29天月份顺延到有三十的年份`() {
        // 2025 农历二月只有 29 天，下一个二月有 30 天是 2027 年
        val today = LocalDate.of(2025, 1, 1)
        val c = Countdown.of("lunar:02-30", today)!!
        assertEquals(LocalDate.of(2027, 4, 6), c.nextDate)
        assertEquals(825L, c.days)
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
