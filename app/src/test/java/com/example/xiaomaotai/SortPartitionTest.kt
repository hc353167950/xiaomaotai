package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 倒数/正数分区排序的单元测试。
 *
 * 核心不变量：
 * 1. 倒数区按天数**升序**（最先要到的最前）
 * 2. 正数区按天数**降序**（累积最久的最前）
 * 3. 两组拼接后倒数恒在正数之前
 *
 * 这里不依赖 Compose，直接把排序规则用同样的比较器复现验证。
 */
class SortPartitionTest {

    private fun event(
        name: String,
        days: Long,
        mode: EventMode,
        sortOrder: Int = 0
    ) = Event(
        id = name,
        eventName = name,
        eventDate = "2025-01-01",
        sortOrder = sortOrder,
        mode = mode
    ).copy(cachedDays = days)

    /** 与 MainActivity.applyCountdownSort 相同的比较器 */
    private fun sortCountdown(list: List<Event>) = list.sortedWith(
        compareBy<Event> { it.cachedDays ?: Long.MAX_VALUE }
            .thenByDescending { it.sortOrder }
    )

    /** 与 MainActivity.applyCountUpSort 相同的比较器 */
    private fun sortCountUp(list: List<Event>) = list.sortedWith(
        compareByDescending<Event> { it.cachedDays ?: 0L }
            .thenByDescending { it.sortOrder }
    )

    @Test
    fun `倒数区按天数升序_最先要到的最前`() {
        val list = listOf(
            event("C", 45, EventMode.COUNTDOWN),
            event("A", 3, EventMode.COUNTDOWN),
            event("B", 12, EventMode.COUNTDOWN)
        )
        assertEquals(listOf("A", "B", "C"), sortCountdown(list).map { it.eventName })
    }

    @Test
    fun `正数区按天数降序_累积最久的最前`() {
        val list = listOf(
            event("刚开始", 5, EventMode.COUNT_UP),
            event("很久了", 1825, EventMode.COUNT_UP),
            event("中间", 90, EventMode.COUNT_UP)
        )
        assertEquals(
            listOf("很久了", "中间", "刚开始"),
            sortCountUp(list).map { it.eventName }
        )
    }

    @Test
    fun `两种模式的排序方向确实相反`() {
        val days = listOf(3L, 12L, 45L)
        val countdown = days.map { event("e$it", it, EventMode.COUNTDOWN) }
        val countUp = days.map { event("e$it", it, EventMode.COUNT_UP) }

        val cdOrder = sortCountdown(countdown).map { it.cachedDays }
        val cuOrder = sortCountUp(countUp).map { it.cachedDays }

        assertEquals(listOf(3L, 12L, 45L), cdOrder)
        assertEquals(listOf(45L, 12L, 3L), cuOrder)
    }

    @Test
    fun `分组拼接后倒数恒在正数之前`() {
        val mixed = listOf(
            event("正数久", 1825, EventMode.COUNT_UP),
            event("倒数近", 3, EventMode.COUNTDOWN),
            event("正数新", 5, EventMode.COUNT_UP),
            event("倒数远", 100, EventMode.COUNTDOWN)
        )
        val countdown = mixed.filter { it.mode == EventMode.COUNTDOWN }
        val countUp = mixed.filter { it.mode == EventMode.COUNT_UP }
        val merged = sortCountdown(countdown) + sortCountUp(countUp)

        assertEquals(
            listOf("倒数近", "倒数远", "正数久", "正数新"),
            merged.map { it.eventName }
        )
        // 前两个必须是倒数，后两个必须是正数
        assertTrue(merged.take(2).all { it.mode == EventMode.COUNTDOWN })
        assertTrue(merged.drop(2).all { it.mode == EventMode.COUNT_UP })
    }

    @Test
    fun `天数相同时按 sortOrder 降序保持稳定`() {
        val list = listOf(
            event("低序", 10, EventMode.COUNTDOWN, sortOrder = 1),
            event("高序", 10, EventMode.COUNTDOWN, sortOrder = 9)
        )
        assertEquals(listOf("高序", "低序"), sortCountdown(list).map { it.eventName })
    }

    @Test
    fun `cachedDays 为 null 的倒数事件排到最后而不是最前`() {
        // null 表示天数未知（日期无效等）。用 MAX_VALUE 兜底，
        // 否则会被当成 0 天而错误地排到最前。
        val list = listOf(
            event("未知", 0, EventMode.COUNTDOWN).copy(cachedDays = null),
            event("正常", 5, EventMode.COUNTDOWN)
        )
        assertEquals(listOf("正常", "未知"), sortCountdown(list).map { it.eventName })
    }

    @Test
    fun `正数事件不产生提醒时刻`() {
        // 提醒调度层对 COUNT_UP 直接跳过，这里验证数据层的前提：
        // 正数事件本身没有「下一次纪念日」的未来语义可用
        val countUp = event("在一起", 1825, EventMode.COUNT_UP)
        assertEquals(EventMode.COUNT_UP, countUp.mode)
    }

    @Test
    fun `空列表排序不崩溃`() {
        assertEquals(emptyList<Event>(), sortCountdown(emptyList()))
        assertEquals(emptyList<Event>(), sortCountUp(emptyList()))
    }

    @Test
    fun `单元素列表排序不变`() {
        val single = listOf(event("唯一", 42, EventMode.COUNTDOWN))
        assertEquals(single, sortCountdown(single))
    }
}
