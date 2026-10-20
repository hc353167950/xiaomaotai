package com.example.xiaomaotai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.xiaomaotai.ui.components.CardStyleManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 事件仓库深模块：整个应用中"现在有哪些事件"的唯一答案来源。
 *
 * 纯本地存储版本：不再有登录/离线双缓存分支，所有事件都存放在同一个
 * SharedPreferences 键 [KEY_EVENTS] 里。云端同步由 [WebDavSync] 以
 * "整体快照"的方式完成，不介入本模块的读写路径。
 *
 * 接口仍然是五个动词：currentEvents / add / update / delete / reorder。
 */
class EventStore(
    private val context: Context,
    private val reminderManager: ReminderManager = ReminderManager(context)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val settings = AppSettings(context)

    // 内存缓存 + 可观察状态（UI 与同步模块据此刷新）
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val eventsFlow: StateFlow<List<Event>> = _events.asStateFlow()

    init {
        migrateLegacyKeys()
        _events.value = loadFromPrefs(KEY_EVENTS)
    }

    // ---------- 查询 ----------

    /**
     * 当前全部事件，按 sortOrder 降序（用户手动排序优先）。
     */
    fun currentEvents(): List<Event> = _events.value.sortedByDescending { it.sortOrder }

    /** 后台组件（广播/服��/Worker）冷启动时直接读持久化缓存，不依赖内存状态 */
    fun persistedEvents(): List<Event> = loadFromPrefs(KEY_EVENTS)

    fun hasAnyEvents(): Boolean = persistedEvents().isNotEmpty()

    // ---------- 变更 ----------

    suspend fun addEvent(event: Event) {
        val eventToSave = event.copy(
            id = event.id.ifBlank { newId() },
            status = EventStatus.NORMAL,
            backgroundId = assignBackgroundId(event)
        ).withAutoDetectedType()

        commitList(_events.value + eventToSave)
        reminderManager.scheduleReminder(eventToSave)
        updatePersistentNotificationIfEnabled()
    }

    suspend fun updateEvent(event: Event) {
        val eventToUpdate = event.withAutoDetectedType()
        commitList(_events.value.map { if (it.id == eventToUpdate.id) eventToUpdate else it })
        reminderManager.updateReminder(eventToUpdate)
        updatePersistentNotificationIfEnabled()
    }

    suspend fun deleteEvent(eventId: String) {
        // 先取出被删事件，用于彻底清理它带纪念日日期的全部闹钟
        val target = _events.value.firstOrNull { it.id == eventId }
        commitList(_events.value.filter { it.id != eventId })
        if (target != null) {
            reminderManager.cancelAllForEvent(target)
        } else {
            reminderManager.cancelReminder(eventId)
        }
        updatePersistentNotificationIfEnabled()
    }

    /**
     * 批量更新排序（排序页保存）。
     *
     * **按 id 合并而不是整表替换**：首页现在是「将至 / 走过」双标签，
     * 排序页每次只拿到当前标签的事件。若在这里直接 commitList(events)，
     * 另一个标签的事件会被整体清掉——数据丢失。
     * 只覆盖传入的事件（取新的 sortOrder），其余事件原样保留。
     */
    suspend fun updateEventOrder(events: List<Event>) {
        val byId = events.associateBy { it.id }
        commitList(_events.value.map { existing ->
            byId[existing.id] ?: existing
        })
        updatePersistentNotificationIfEnabled()
    }

    /**
     * 整体替换事件列表（WebDAV 恢复 / 合并结果写回用）。
     * 会重新补设提醒并刷新常驻通知。
     *
     * **id 兜底去重**：调用方（云端数据）可能带来重复或空 id，
     * 而 UI 的 LazyColumn 用 `key = { it.id }`，重复 key 会直接崩溃。
     * 这里作为储层最后一道防线再规范一次，保证落盘数据 id 一定唯一非空。
     */
    suspend fun replaceAll(events: List<Event>) {
        val safe = ensureUniqueIds(events)
        commitList(safe)
        safe.forEach { event ->
            try {
                reminderManager.safeScheduleReminder(event)
            } catch (e: Exception) {
                Log.e(TAG, "替换后设置提醒失败: ${event.eventName}, ${e.message}")
            }
        }
        updatePersistentNotificationIfEnabled()
    }

    /** 保证 id 非空且唯一：冲突的就重新发一个 */
    private fun ensureUniqueIds(events: List<Event>): List<Event> {
        val used = mutableSetOf<String>()
        var changed = false
        val result = events.map { event ->
            val raw = event.id.trim()
            val id = when {
                raw.isEmpty() -> {
                    changed = true
                    UUID.randomUUID().toString()
                }
                raw in used -> {
                    changed = true
                    var candidate = UUID.randomUUID().toString()
                    while (candidate in used) candidate = UUID.randomUUID().toString()
                    candidate
                }
                else -> raw
            }
            used.add(id)
            if (id != event.id) event.copy(id = id) else event
        }
        if (changed) Log.w(TAG, "检测到重复/空 id，已重新分配（${events.size} 条）")
        return result
    }

    // ---------- 生命周期 ----------

    /** App 启动时初始化：恢复缓存、补设提醒 */
    suspend fun initialize() {
        _events.value = loadFromPrefs(KEY_EVENTS)
        _events.value.forEach { event ->
            try {
                reminderManager.safeScheduleReminder(event)
            } catch (e: Exception) {
                Log.e(TAG, "设置提醒失败: ${event.eventName}, ${e.message}")
            }
        }
    }

    suspend fun refreshEvents() {
        _events.value = loadFromPrefs(KEY_EVENTS)
    }

    // ---------- 实现细节 ----------

    /** 统一写入路径：更新内存缓存、落盘、通知订阅方 */
    private fun commitList(events: List<Event>) {
        _events.value = events
        persist(KEY_EVENTS, events)
    }

    private fun persist(key: String, events: List<Event>) {
        prefs.edit().putString(key, EventJson.encode(events)).apply()
    }

    /**
     * 读取事件列表。
     *
     * 解析统一走 [EventJson]，它会对缺失字段逐项兜底。
     * 若用裸 Gson，旧数据里没有 `mode` / `reminderTone` 会被反序列化成 null，
     * 渲染卡片时一访问就 NPE —— 表现是「升级后卡片全没了」。
     */
    private fun loadFromPrefs(key: String): List<Event> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val events = EventJson.decodeList(json)
        if (events.isEmpty() && json.isNotBlank() && json.trim() != "[]") {
            Log.e(TAG, "事件缓存解析为空($key)，原始长度=${json.length}")
        }
        return events
    }

    /**
     * 一次性迁移：旧版本把事件拆成 local_events（登录用户）与 offline_events
     * （未登录）两个键。升级后合并到单一 events 键，避免用户误以为数据丢失。
     */
    private fun migrateLegacyKeys() {
        if (prefs.contains(KEY_EVENTS)) return

        val legacy = loadFromPrefs(LEGACY_LOCAL) + loadFromPrefs(LEGACY_OFFLINE)
        if (legacy.isEmpty()) return

        // 旧数据可能缺 id，补上稳定 id 后再落盘
        val migrated = legacy.map { it.copy(id = it.id.ifBlank { newId() }) }
        persist(KEY_EVENTS, migrated)
        prefs.edit()
            .remove(LEGACY_LOCAL)
            .remove(LEGACY_OFFLINE)
            .apply()
        Log.d(TAG, "已迁移旧事件数据 ${migrated.size} 条到 $KEY_EVENTS")
    }

    /** 生成事件唯一标识：UUID，避免时间戳在批量导入时碰撞 */
    private fun newId(): String = UUID.randomUUID().toString()

    /** 新事件分配背景样式：避开已使用的颜色 */
    private fun assignBackgroundId(event: Event): Int {
        if (event.backgroundId != 0) return event.backgroundId
        val usedBackgroundIds = currentEvents().map { it.backgroundId }
        return CardStyleManager.getSmartRandomStyleId(usedBackgroundIds)
    }

    /** CRUD 后立即刷新常驻通知内容 */
    private fun updatePersistentNotificationIfEnabled() {
        try {
            if (settings.isPersistentNotificationEnabled()) {
                PersistentNotificationService.updateNotification(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发常驻通知更新失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "EventStore"

        /** 当前使用的唯一事件存储键 */
        const val KEY_EVENTS = "events"

        /** 旧版本遗留键（仅用于一次性迁移） */
        private const val LEGACY_LOCAL = "local_events"
        private const val LEGACY_OFFLINE = "offline_events"
    }
}
