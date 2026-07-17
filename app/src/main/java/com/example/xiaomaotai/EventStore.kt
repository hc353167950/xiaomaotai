package com.example.xiaomaotai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.xiaomaotai.ui.components.CardStyleManager
import com.google.gson.Gson

/**
 * 事件仓库深模块：整个应用中"现在有哪些事件"的唯一答案来源。
 *
 * 接口收窄为 currentEvents() + add/update/delete/reorder 五个动词。
 * 登录/离线双缓存的分支、SharedPreferences 持久化、Supabase 同步、
 * 提醒联动、常驻通知刷新全部是实现细节——此前每个后台组件都要手写
 * "offline + local 合并"（≥6 处复制），现在一个查询搞定。
 *
 * 与 [Account] 的衔接：构造时注册 onLogin/onLogout 回调，
 * 登录后拉取云端并同步离线事件，登出时清空缓存。
 */
class EventStore(
    private val context: Context,
    private val account: Account,
    private val network: NetworkDataManager = NetworkDataManager(),
    private val reminderManager: ReminderManager = ReminderManager(context)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val settings = AppSettings(context)

    // 内存缓存：登录用户的云端事件 / 未登录的离线事件
    private var localEvents: List<Event> = emptyList()
    private var offlineEvents: List<Event> = emptyList()

    init {
        account.onLogin = { user ->
            loadUserData(user.id)
            syncOfflineData(user.id)
        }
        account.onLogout = {
            prefs.edit()
                .remove("local_events")
                .remove("offline_events")
                .commit()
            localEvents = emptyList()
            offlineEvents = emptyList()
            updatePersistentNotificationIfEnabled()
        }
    }

    // ---------- 查询（唯一入口，内部消化登录/离线分支） ----------

    /**
     * 当前全部事件，按 sortOrder 降序（用户手动排序优先）。
     * 登录走云端缓存，未登录走离线缓存——调用方无需再感知这条分支。
     */
    fun currentEvents(): List<Event> {
        return if (account.isLoggedIn()) {
            localEvents.sortedByDescending { it.sortOrder }
        } else {
            offlineEvents.sortedByDescending { it.sortOrder }
        }
    }

    /** 后台组件（广播/服务/Worker）冷启动时直接读持久化缓存，不依赖内存状态 */
    fun persistedEvents(): List<Event> {
        val merged = mutableListOf<Event>()
        merged.addAll(loadFromPrefs("offline_events"))
        if (account.isLoggedIn()) {
            merged.addAll(loadFromPrefs("local_events"))
        }
        return merged
    }

    fun hasAnyEvents(): Boolean = persistedEvents().isNotEmpty()

    // ---------- 变更 ----------

    suspend fun addEvent(event: Event) {
        if (account.isLoggedIn()) {
            val userId = account.getCurrentUserId()!!
            try {
                val eventToSave = event.copy(
                    status = EventStatus.NORMAL,
                    backgroundId = assignBackgroundId(event)
                ).withAutoDetectedType()
                val result = network.saveEvent(eventToSave, userId)
                if (result.isSuccess) {
                    // 先立即更新本地缓存，通知刷新时才能看到新事件；再异步对齐服务器
                    localEvents = localEvents + eventToSave
                    persist("local_events", localEvents)
                    reminderManager.scheduleReminder(eventToSave)
                    updatePersistentNotificationIfEnabled()
                    loadUserData(userId)
                } else {
                    Log.e(TAG, "保存事件到云端失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加事件异常: ${e.message}")
            }
        } else {
            val offlineEvent = event.copy(
                id = "offline_${System.currentTimeMillis()}",
                status = EventStatus.NORMAL,
                backgroundId = assignBackgroundId(event)
            ).withAutoDetectedType()
            offlineEvents = offlineEvents + offlineEvent
            persist("offline_events", offlineEvents)
            reminderManager.scheduleReminder(offlineEvent)
            updatePersistentNotificationIfEnabled()
        }
    }

    suspend fun updateEvent(event: Event) {
        if (account.isLoggedIn()) {
            try {
                val eventToUpdate = event.copy(status = EventStatus.UPDATED).withAutoDetectedType()
                val result = network.updateEvent(eventToUpdate)
                if (result.isSuccess) {
                    localEvents = localEvents.map { if (it.id == eventToUpdate.id) eventToUpdate else it }
                    persist("local_events", localEvents)
                    reminderManager.updateReminder(eventToUpdate)
                    updatePersistentNotificationIfEnabled()
                } else {
                    Log.e(TAG, "更新云端事件失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新事件异常: ${e.message}")
            }
        } else {
            val updatedEvent = event.withAutoDetectedType()
            offlineEvents = offlineEvents.map { if (it.id == updatedEvent.id) updatedEvent else it }
            persist("offline_events", offlineEvents)
            reminderManager.updateReminder(updatedEvent)
            updatePersistentNotificationIfEnabled()
        }
    }

    suspend fun deleteEvent(eventId: String) {
        if (account.isLoggedIn()) {
            try {
                val result = network.updateEventStatus(eventId, EventStatus.DELETED)
                if (!result.isSuccess) {
                    Log.e(TAG, "云端标记删除失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除事件异常: ${e.message}")
            }
            // 无论云端成败，本地立即删除（下次 loadUserData 会对齐）
            localEvents = localEvents.filter { it.id != eventId }
            persist("local_events", localEvents)
        } else {
            offlineEvents = offlineEvents.filter { it.id != eventId }
            persist("offline_events", offlineEvents)
        }
        reminderManager.cancelReminder(eventId)
        updatePersistentNotificationIfEnabled()
    }

    /** 批量更新排序（拖拽排序页保存） */
    suspend fun updateEventOrder(events: List<Event>) {
        if (account.isLoggedIn()) {
            val userId = account.getCurrentUserId()!!
            try {
                val result = network.updateEventOrder(events, userId)
                if (result.isSuccess) {
                    localEvents = events
                    persist("local_events", localEvents)
                } else {
                    Log.e(TAG, "更新排序失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新排序异常: ${e.message}")
            }
        } else {
            offlineEvents = events
            persist("offline_events", offlineEvents)
        }
    }

    // ---------- 生命周期 ----------

    /** App 启动时初始化：恢复缓存、补设提醒、登录用户拉取云端 */
    suspend fun initialize() {
        localEvents = loadFromPrefs("local_events")
        offlineEvents = loadFromPrefs("offline_events")

        offlineEvents.forEach { event ->
            try {
                reminderManager.safeScheduleReminder(event)
            } catch (e: Exception) {
                Log.e(TAG, "为离线事件设置提醒失败: ${event.eventName}, ${e.message}")
            }
        }

        account.getCurrentUser()?.let { loadUserData(it.id) }
    }

    /** 从云端刷新登录用户的事件（含提醒补设与通知刷新） */
    suspend fun loadUserData(userId: String) {
        try {
            val result = network.getEvents(userId)
            if (result.isSuccess) {
                localEvents = result.getOrNull() ?: emptyList()
                persist("local_events", localEvents)
                localEvents.forEach { event ->
                    try {
                        reminderManager.safeScheduleReminder(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "设置提醒失败: ${event.eventName}, ${e.message}")
                    }
                }
                updatePersistentNotificationIfEnabled()
            } else {
                Log.e(TAG, "拉取云端事件失败: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载用户数据异常: ${e.message}")
        }
    }

    suspend fun refreshEvents() {
        if (account.isLoggedIn()) {
            account.getCurrentUserId()?.let { loadUserData(it) }
        } else {
            offlineEvents = loadFromPrefs("offline_events")
        }
    }

    /** 登录后把离线期间创建的事件推到云端，排在最上面 */
    private suspend fun syncOfflineData(userId: String) {
        val offlineEventsToSync = loadFromPrefs("offline_events")
        if (offlineEventsToSync.isEmpty()) return

        val currentMaxSortOrder = localEvents.maxOfOrNull { it.sortOrder } ?: -1
        var syncSortOrder = currentMaxSortOrder + 1

        for (event in offlineEventsToSync) {
            try {
                val syncEvent = event.copy(
                    status = EventStatus.NORMAL,
                    sortOrder = syncSortOrder++
                )
                val result = network.saveEvent(syncEvent, userId)
                if (!result.isSuccess) {
                    Log.e(TAG, "同步离线事件失败 ${event.id}: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步离线事件异常 ${event.id}: ${e.message}")
            }
        }
        prefs.edit().remove("offline_events").commit()
        offlineEvents = emptyList()
        loadUserData(userId)
    }

    // ---------- 实现细节 ----------

    private fun persist(key: String, events: List<Event>) {
        prefs.edit().putString(key, gson.toJson(events)).commit()
    }

    private fun loadFromPrefs(key: String): List<Event> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Event>>() {}.type
            gson.fromJson<List<Event>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "读取事件缓存失败($key): ${e.message}")
            emptyList()
        }
    }

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
    }
}
