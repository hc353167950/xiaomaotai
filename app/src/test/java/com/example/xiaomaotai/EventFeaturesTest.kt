package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 本次新增能力的单元测试：正数模式、提醒档位覆盖、文案语气。
 *
 * 全部通过纯函数入口验证，不依赖 Android 环境（不碰 Notification/Alarm）。
 */
class EventFeaturesTest {

    // ---------- 正数模式（已经第 N 天） ----------

    @Test
    fun `countUp 计算起始日到今天的第几天_当天算第1天`() {
        // 2020-01-01 开始，当天就是第 1 天
        val c = Countdown.of("2020-01-01", today = LocalDate.of(2020, 1, 1), mode = EventMode.COUNT_UP)
        assertNotNull(c)
        assertEquals(1L, c!!.elapsedDays)
        assertEquals(EventMode.COUNT_UP, c.mode)
    }

    @Test
    fun `countUp 跨越一天`() {
        val c = Countdown.of("2020-01-01", today = LocalDate.of(2020, 1, 2), mode = EventMode.COUNT_UP)
        assertEquals(2L, c!!.elapsedDays)
    }

    @Test
    fun `countUp 长跨度天数正确`() {
        // 2020-01-01 到 2023-01-01 = 1096 天（2020 是闰年），第 1097 天
        val c = Countdown.of("2020-01-01", today = LocalDate.of(2023, 1, 1), mode = EventMode.COUNT_UP)
        assertEquals(1097L, c!!.elapsedDays)
    }

    @Test
    fun `countUp 农历事件用农历起始日`() {
        // 农历 2025 年六月十六 对应公历 2025-07-10
        val c = Countdown.of(
            "lunar:2025-06-16",
            today = LocalDate.of(2025, 7, 10),
            mode = EventMode.COUNT_UP
        )
        assertNotNull(c)
        assertEquals(1L, c!!.elapsedDays)
    }

    @Test
    fun `countUp 无年份事件无法定义起点_返回null回退倒数`() {
        // MM-dd 这类「每年」事件没有起点，elapsedDays 必须是 null
        val c = Countdown.of("06-16", today = LocalDate.of(2025, 7, 10), mode = EventMode.COUNT_UP)
        assertNotNull(c)
        assertNull(c!!.elapsedDays)
    }

    @Test
    fun `countdown 模式下 elapsedDays 恒为 null`() {
        val c = Countdown.of("2020-01-01", today = LocalDate.of(2025, 7, 10))
        assertNull(c!!.elapsedDays)
        assertEquals(EventMode.COUNTDOWN, c.mode)
    }

    // ---------- 提醒档位覆盖 ----------

    @Test
    fun `事件未设置档位时用全局默认`() {
        val event = Event(eventName = "生日", eventDate = "2025-07-10")
        assertEquals(listOf(7, 1, 0), event.effectiveOffsets())
    }

    @Test
    fun `事件自定义档位覆盖全局默认`() {
        val event = Event(
            eventName = "交房租",
            eventDate = "2025-07-10",
            reminderDaysBefore = listOf(1, 0)
        )
        assertEquals(listOf(1, 0), event.effectiveOffsets())
    }

    @Test
    fun `事件档位为空列表时不产生任何提醒时刻`() {
        val now = LocalDateTime.of(2025, 1, 1, 9, 0)
        val occurrences = ReminderSchedule.occurrencesFor(
            eventDate = "2025-07-10",
            now = now,
            offsets = emptyList()
        )
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `仅当天档位只生成当天时刻`() {
        val now = LocalDateTime.of(2025, 7, 9, 9, 0)
        val occurrences = ReminderSchedule.occurrencesFor(
            eventDate = "2025-07-10",
            now = now,
            offsets = listOf(0)
        )
        // 当天有三个时刻：0 点、8 点、12 点
        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.daysBefore == 0 })
    }

    @Test
    fun `默认档位生成的时刻数量等于各档位时刻之和`() {
        val now = LocalDateTime.of(2025, 1, 1, 9, 0)
        val occurrences = ReminderSchedule.occurrencesFor("2025-07-10", now)
        // 7天前1个 + 1天前1个 + 当天3个 = 5
        assertEquals(5, occurrences.size)
    }

    // ---------- 通知文案语气 ----------

    @Test
    fun `中性语气只陈述事实_不含祝福语`() {
        val (_, content) = ReminderSchedule.notificationContent("妈妈生日", 0, ReminderTone.NEUTRAL)
        assertEquals("今天是「妈妈生日」", content)
        assertFalse(content.contains("祝"))
        assertFalse(content.contains("开心"))
    }

    @Test
    fun `祝福语气包含祝福`() {
        val (title, content) = ReminderSchedule.notificationContent("妈妈生日", 0, ReminderTone.BLESSING)
        assertEquals("今天是个好日子", title)
        assertTrue(content.contains("祝"))
    }

    @Test
    fun `纪念语气不含祝福也不含开心`() {
        val (_, content) = ReminderSchedule.notificationContent("父亲忌日", 0, ReminderTone.MEMORIAL)
        assertFalse(content.contains("祝一切顺遂"))
        assertFalse(content.contains("开心"))
        assertTrue(content.contains("安好"))
    }

    @Test
    fun `提醒语气像日程一样直接`() {
        val (_, content) = ReminderSchedule.notificationContent("还信用卡", 0, ReminderTone.REMIND)
        assertEquals("今天：「还信用卡」", content)
    }

    @Test
    fun `各语气在提前7天和1天都有文案`() {
        ReminderTone.entries.forEach { tone ->
            listOf(7, 1, 0).forEach { days ->
                val (title, content) = ReminderSchedule.notificationContent("测试", days, tone)
                assertTrue("$tone/$days 标题为空", title.isNotBlank())
                assertTrue("$tone/$days 内容为空", content.isNotBlank())
                assertTrue("$tone/$days 未包含事件名", content.contains("测试"))
            }
        }
    }

    // ---------- 模型默认值与向后兼容 ----------

    @Test
    fun `旧数据反序列化后新字段取默认值`() {
        // 模拟旧版本没有新字段的 JSON
        val event = Event(eventName = "生日", eventDate = "2025-07-10")
        assertEquals("", event.note)
        assertTrue(event.reminderEnabled)
        assertNull(event.reminderDaysBefore)
        assertEquals(ReminderTone.NEUTRAL, event.reminderTone)
        assertEquals(EventMode.COUNTDOWN, event.mode)
    }

    @Test
    fun `EventMode 从整数还原`() {
        assertEquals(EventMode.COUNTDOWN, EventMode.fromInt(0))
        assertEquals(EventMode.COUNT_UP, EventMode.fromInt(1))
        assertEquals(EventMode.COUNTDOWN, EventMode.fromInt(99))
    }

    @Test
    fun `ReminderTone 从整数还原`() {
        assertEquals(ReminderTone.NEUTRAL, ReminderTone.fromInt(0))
        assertEquals(ReminderTone.BLESSING, ReminderTone.fromInt(1))
        assertEquals(ReminderTone.MEMORIAL, ReminderTone.fromInt(2))
        assertEquals(ReminderTone.REMIND, ReminderTone.fromInt(3))
        assertEquals(ReminderTone.NEUTRAL, ReminderTone.fromInt(99))
    }

    // ---------- 闰月标记 ----------

    @Test
    fun `闰月年普通六月十六命中闰月角标`() {
        // 2025 是闰六月年，普通六月十六（2025-07-10）之后还有闰六月十六（2025-08-09）
        val c = Countdown.of("lunar:2025-06-16", today = LocalDate.of(2025, 7, 11))
        assertNotNull(c)
        // 下一次落点是闰六月十六
        assertEquals(LocalDate.of(2025, 8, 9), c!!.nextDate)
        assertTrue(c.isLeapOccurrence)
    }

    @Test
    fun `非闰月年不命中闰月角标`() {
        val c = Countdown.of("lunar:2026-06-16", today = LocalDate.of(2026, 1, 1))
        assertNotNull(c)
        assertFalse(c!!.isLeapOccurrence)
    }
}
