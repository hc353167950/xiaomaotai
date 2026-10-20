package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧数据兼容性测试。
 *
 * 背景：Gson 反序列化 Kotlin data class 会绕过构造器，Kotlin 的默认值不生效。
 * 一旦旧版本 JSON 缺少 3.2.0 新增字段，读出来就是 null，
 * 渲染卡片时一访问就 NPE —— 用户看到的是「升级后卡片全没了」。
 *
 * 这些用例把各历史版本的 JSON 原样喂给 [EventJson]，确认都能安全解析。
 */
class EventJsonCompatTest {

    @Test
    fun `旧版无任何新字段的JSON能解析且新字段取默认值`() {
        // 3.1.0 及更早版本的典型结构：没有 note / reminderEnabled / reminderDaysBefore / reminderTone / mode
        val legacy = """
            [{"id":"abc-123","eventName":"妈妈生日","eventDate":"2025-07-10",
              "sortOrder":3,"status":0,"eventType":0,"backgroundId":2}]
        """.trimIndent()

        val events = EventJson.decodeList(legacy)
        assertEquals(1, events.size)

        val e = events.first()
        assertEquals("abc-123", e.id)
        assertEquals("妈妈生日", e.eventName)
        assertEquals("2025-07-10", e.eventDate)
        assertEquals(3, e.sortOrder)
        assertEquals(2, e.backgroundId)

        // 新增字段必须回落到默认值，而不是 null
        assertEquals("", e.note)
        assertTrue("旧数据必须默认开启提醒", e.reminderEnabled)
        assertNull(e.reminderDaysBefore)
        assertEquals(ReminderTone.NEUTRAL, e.reminderTone)
        assertEquals(EventMode.COUNTDOWN, e.mode)
    }

    @Test
    fun `解析出的旧数据可以直接访问新增属性而不崩溃`() {
        // 这一条专门防「卡片渲染 NPE」：把每条新增属性都访问一遍
        val legacy = """[{"id":"x","eventName":"测试","eventDate":"03-15"}]"""
        val e = EventJson.decodeList(legacy).single()

        assertNotNull(e.note)
        assertEquals(EventMode.COUNTDOWN, e.mode)
        assertEquals(ReminderTone.NEUTRAL, e.reminderTone)
        // effectiveOffsets 内部会读 reminderDaysBefore，旧数据为 null 时必须回落默认
        assertEquals(ReminderSchedule.OFFSETS, e.effectiveOffsets())
    }

    @Test
    fun `枚举以字符串形式存储时能正确还原`() {
        // Gson 默认把枚举写成名字
        val json = """
            [{"id":"1","eventName":"在一起","eventDate":"2020-01-01",
              "mode":"COUNT_UP","reminderTone":"BLESSING"}]
        """.trimIndent()
        val e = EventJson.decodeList(json).single()
        assertEquals(EventMode.COUNT_UP, e.mode)
        assertEquals(ReminderTone.BLESSING, e.reminderTone)
    }

    @Test
    fun `枚举以整数形式存储时也能还原`() {
        // 旧 WebDAV 数据可能是 kotlinx.serialization 写的整数
        val json = """
            [{"id":"1","eventName":"在一起","eventDate":"2020-01-01",
              "mode":1,"reminderTone":2}]
        """.trimIndent()
        val e = EventJson.decodeList(json).single()
        assertEquals(EventMode.COUNT_UP, e.mode)
        assertEquals(ReminderTone.MEMORIAL, e.reminderTone)
    }

    @Test
    fun `未知枚举值回落默认而不是抛异常`() {
        val json = """[{"id":"1","eventName":"x","eventDate":"2020-01-01","mode":99,"reminderTone":"NOPE"}]"""
        val e = EventJson.decodeList(json).single()
        assertEquals(EventMode.COUNTDOWN, e.mode)
        assertEquals(ReminderTone.NEUTRAL, e.reminderTone)
    }

    @Test
    fun `字段显式为null时回落默认值`() {
        val json = """
            [{"id":"1","eventName":"x","eventDate":"2020-01-01",
              "note":null,"mode":null,"reminderTone":null,"reminderEnabled":null}]
        """.trimIndent()
        val e = EventJson.decodeList(json).single()
        assertEquals("", e.note)
        assertEquals(EventMode.COUNTDOWN, e.mode)
        assertEquals(ReminderTone.NEUTRAL, e.reminderTone)
        assertTrue(e.reminderEnabled)
    }

    @Test
    fun `完整的3_2_0数据能无损往返`() {
        val original = Event(
            id = "id-1",
            eventName = "戒烟",
            eventDate = "2024-03-01",
            sortOrder = 5,
            eventType = EventType.SOLAR,
            backgroundId = 7,
            note = "从今天开始",
            reminderEnabled = false,
            reminderDaysBefore = listOf(1, 0),
            reminderTone = ReminderTone.REMIND,
            mode = EventMode.COUNT_UP
        )

        val roundTrip = EventJson.decodeList(EventJson.encode(listOf(original))).single()

        assertEquals(original.id, roundTrip.id)
        assertEquals(original.eventName, roundTrip.eventName)
        assertEquals(original.eventDate, roundTrip.eventDate)
        assertEquals(original.sortOrder, roundTrip.sortOrder)
        assertEquals(original.backgroundId, roundTrip.backgroundId)
        assertEquals(original.note, roundTrip.note)
        assertFalse(roundTrip.reminderEnabled)
        assertEquals(listOf(1, 0), roundTrip.reminderDaysBefore)
        assertEquals(ReminderTone.REMIND, roundTrip.reminderTone)
        assertEquals(EventMode.COUNT_UP, roundTrip.mode)
    }

    @Test
    fun `空的提醒档位列表会被保留而不是当成缺失`() {
        // 空列表语义是「不提醒」，不能和「跟随全局默认」混淆
        val json = """[{"id":"1","eventName":"x","eventDate":"2020-01-01","reminderDaysBefore":[]}]"""
        val e = EventJson.decodeList(json).single()
        assertEquals(emptyList<Int>(), e.reminderDaysBefore)
        assertTrue(e.effectiveOffsets().isEmpty())
    }

    @Test
    fun `空数组与损坏JSON都返回空列表不抛异常`() {
        assertTrue(EventJson.decodeList("").isEmpty())
        assertTrue(EventJson.decodeList(null).isEmpty())
        assertTrue(EventJson.decodeList("[]").isEmpty())
        assertTrue(EventJson.decodeList("{not json}").isEmpty())
    }

    @Test
    fun `旧数据字段顺序变化不影响解析`() {
        val json = """
            [{"eventDate":"2025-07-10","backgroundId":1,"eventName":"乱序","status":0,"id":"z"}]
        """.trimIndent()
        val e = EventJson.decodeList(json).single()
        assertEquals("乱序", e.eventName)
        assertEquals("2025-07-10", e.eventDate)
    }
}
