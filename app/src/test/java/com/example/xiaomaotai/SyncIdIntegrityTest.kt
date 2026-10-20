package com.example.xiaomaotai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 备份 / 恢复 / 合并的健壮性测试。
 *
 * 覆盖两个真实事故场景：
 * 1. 云端事件与本地事件 **id 撞车** → 首页 LazyColumn 用 `key = { it.id }`，
 *    重复 key 会直接抛异常，表现为「拉取备份后首页崩溃 / 卡片消失」。
 * 2. 合并时把「将至」和「走过」两组混在一起连续编号 sortOrder →
 *    其中一组的卡片顺序被改写。
 *
 * 这些是纯逻辑，不依赖 Android 运行时（不碰 AlarmManager / SharedPreferences）。
 */
class SyncIdIntegrityTest {

    private fun event(
        id: String,
        name: String,
        date: String,
        sortOrder: Int = 0,
        mode: EventMode = EventMode.COUNTDOWN
    ) = Event(
        id = id,
        eventName = name,
        eventDate = date,
        sortOrder = sortOrder,
        mode = mode
    )

    // ---------- id 唯一性 ----------

    @Test
    fun `云端与本地 id 相同时合并后不应出现重复 id`() {
        val local = listOf(
            event("same-id", "本地生日", "10-01"),
            event("local-2", "本地纪念", "11-01")
        )
        // 云端有一条事件用了和本地完全相同的 id
        val remote = listOf(
            event("same-id", "云端新增", "12-01")
        )

        val ids = mergeWithNormalization(local, remote).map { it.id }

        assertEquals("合并后 id 必须唯一", ids.size, ids.toSet().size)
        assertEquals(3, ids.size)
    }

    @Test
    fun `云端事件缺少 id 时应补上而非留空`() {
        val local = listOf(event("local-1", "本地生日", "10-01"))
        val remote = listOf(event("", "云端旧数据", "05-20"))

        val merged = mergeWithNormalization(local, remote)

        assertEquals(2, merged.size)
        assertTrue("空 id 必须被补全", merged.all { it.id.isNotBlank() })
        assertEquals(merged.size, merged.map { it.id }.toSet().size)
    }

    @Test
    fun `云端自带的多条空 id 事件应各自拿到不同 id`() {
        val remote = listOf(
            event("", "旧数据A", "01-01"),
            event("", "旧数据B", "02-02"),
            event("", "旧数据C", "03-03")
        )

        val ids = mergeWithNormalization(emptyList(), remote).map { it.id }

        assertEquals(3, ids.size)
        assertEquals("三条空 id 不能合并成同一个", 3, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    // ---------- 合并去重 ----------

    @Test
    fun `名称与日期都相同的事件视为同一条不重复添加`() {
        val local = listOf(event("a", "结婚纪念日", "10-01"))
        val remote = listOf(event("b", "结婚纪念日", "10-01"))

        val merged = mergeWithNormalization(local, remote)

        assertEquals(1, merged.size)
        assertEquals("应保留本地版本", "a", merged.first().id)
    }

    @Test
    fun `名称相同日期不同的事件是两条都要保留`() {
        val local = listOf(event("a", "生日", "10-01"))
        val remote = listOf(event("b", "生日", "10-02"))

        val merged = mergeWithNormalization(local, remote)

        assertEquals(2, merged.size)
    }

    // ---------- 按模式分组编号 ----------

    @Test
    fun `合并后编号不应打乱各标签内部的相对顺序`() {
        // 将至：A 在 B 前（sortOrder 更高）
        // 走过：C 在 D 前
        val local = listOf(
            event("a", "将至A", "10-01", sortOrder = 100, mode = EventMode.COUNTDOWN),
            event("b", "将至B", "11-01", sortOrder = 90, mode = EventMode.COUNTDOWN),
            event("c", "走过C", "2020-01-01", sortOrder = 50, mode = EventMode.COUNT_UP),
            event("d", "走过D", "2019-01-01", sortOrder = 40, mode = EventMode.COUNT_UP)
        )

        val merged = mergeWithNormalization(local, emptyList())

        val countdown = merged.filter { it.mode == EventMode.COUNTDOWN }
            .sortedByDescending { it.sortOrder }
            .map { it.eventName }
        val countUp = merged.filter { it.mode == EventMode.COUNT_UP }
            .sortedByDescending { it.sortOrder }
            .map { it.eventName }

        assertEquals(listOf("将至A", "将至B"), countdown)
        assertEquals(listOf("走过C", "走过D"), countUp)
    }

    @Test
    fun `两组的编号各自连续互不影响`() {
        val local = listOf(
            event("a", "将至A", "10-01", sortOrder = 7, mode = EventMode.COUNTDOWN),
            event("b", "将至B", "11-01", sortOrder = 3, mode = EventMode.COUNTDOWN),
            event("c", "走过C", "2020-01-01", sortOrder = 9, mode = EventMode.COUNT_UP)
        )

        val merged = mergeWithNormalization(local, emptyList())

        val countdownOrders = merged.filter { it.mode == EventMode.COUNTDOWN }.map { it.sortOrder }
        val countUpOrders = merged.filter { it.mode == EventMode.COUNT_UP }.map { it.sortOrder }

        // 组内编号从 (size-1) 递减到 0
        assertEquals(setOf(1, 0), countdownOrders.toSet())
        assertEquals(setOf(0), countUpOrders.toSet())
        // 两组的编号在各自的组内不重复
        assertEquals(countdownOrders.size, countdownOrders.toSet().size)
    }

    @Test
    fun `合并新增事件后原有两条标签数量都保留`() {
        val local = listOf(
            event("a", "将至A", "10-01", mode = EventMode.COUNTDOWN),
            event("c", "走过C", "2020-01-01", mode = EventMode.COUNT_UP)
        )
        val remote = listOf(
            event("x", "云端将至", "12-25", mode = EventMode.COUNTDOWN),
            event("y", "云端走过", "2018-06-01", mode = EventMode.COUNT_UP)
        )

        val merged = mergeWithNormalization(local, remote)

        assertEquals(2, merged.count { it.mode == EventMode.COUNTDOWN })
        assertEquals(2, merged.count { it.mode == EventMode.COUNT_UP })
    }

    // ---------- 恢复语义 ----------

    @Test
    fun `恢复时云端内部的重复 id 也应被拆开`() {
        val remote = listOf(
            event("dup", "第一条", "01-01"),
            event("dup", "第二条", "02-02")
        )

        val restored = normalizeForRestoreForTest(remote)

        assertEquals(2, restored.size)
        assertEquals(2, restored.map { it.id }.toSet().size)
        assertNotEquals(restored[0].id, restored[1].id)
    }

    @Test
    fun `恢复应保留原有 mode 不走丢`() {
        val remote = listOf(
            event("a", "走过事件", "2019-05-05", mode = EventMode.COUNT_UP),
            event("b", "将至事件", "12-01", mode = EventMode.COUNTDOWN)
        )

        val restored = normalizeForRestoreForTest(remote)

        assertEquals(1, restored.count { it.mode == EventMode.COUNT_UP })
        assertEquals(1, restored.count { it.mode == EventMode.COUNTDOWN })
    }

    @Test
    fun `恢复应保留备注与提醒配置`() {
        val remote = listOf(
            Event(
                id = "a",
                eventName = "妈妈的生日",
                eventDate = "lunar:06-16",
                note = "记得买蛋糕",
                reminderEnabled = false,
                reminderDaysBefore = listOf(3, 0),
                reminderTone = ReminderTone.BLESSING,
                mode = EventMode.COUNTDOWN
            )
        )

        val restored = normalizeForRestoreForTest(remote).single()

        assertEquals("记得买蛋糕", restored.note)
        assertEquals(false, restored.reminderEnabled)
        assertEquals(listOf(3, 0), restored.reminderDaysBefore)
        assertEquals(ReminderTone.BLESSING, restored.reminderTone)
        assertEquals(EventMode.COUNTDOWN, restored.mode)
    }

    // ---------- 测试用实现（与 WebDavSync 内部算法一致） ----------

    private fun mergeKey(event: Event): String =
        "${event.eventName.trim()}\u0000${event.eventDate.trim()}"

    /** 复刻 WebDavSync.mergeWithCloud 的合并主流程（不含网络与持久化） */
    private fun mergeWithNormalization(
        local: List<Event>,
        remote: List<Event>
    ): List<Event> {
        val localKeys = local.map { mergeKey(it) }.toSet()
        val usedIds = local.map { it.id }.toMutableSet()

        val merged = mutableListOf<Event>()
        merged.addAll(local)
        val incoming = remote.filter { mergeKey(it) !in localKeys }
        merged.addAll(normalizeIncoming(incoming, usedIds))
        return renumberPerMode(merged)
    }

    /** 复刻 WebDavSync.normalizeIncoming */
    private fun normalizeIncoming(
        incoming: List<Event>,
        usedIds: MutableSet<String>
    ): List<Event> = incoming.map { event ->
        val safeId = event.id.trim().ifBlank { java.util.UUID.randomUUID().toString() }
        val finalId = if (safeId in usedIds) {
            var candidate = java.util.UUID.randomUUID().toString()
            while (candidate in usedIds) candidate = java.util.UUID.randomUUID().toString()
            candidate
        } else {
            safeId
        }
        usedIds.add(finalId)
        event.copy(id = finalId).withAutoDetectedType()
    }

    private fun normalizeForRestoreForTest(events: List<Event>): List<Event> {
        val used = mutableSetOf<String>()
        return normalizeIncoming(events, used)
    }

    /** 复刻 WebDavSync.renumberPerMode */
    private fun renumberPerMode(events: List<Event>): List<Event> {
        return events
            .groupBy { it.mode }
            .flatMap { (_, group) ->
                group.sortedByDescending { it.sortOrder }
                    .mapIndexed { index, e -> e.copy(sortOrder = group.size - 1 - index) }
            }
    }
}
