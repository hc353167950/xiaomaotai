package com.example.xiaomaotai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.example.xiaomaotai.ui.components.CardStyleManager

class DataManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val networkDataManager = NetworkDataManager()
    private val reminderManager = ReminderManager(context)

    // 本地缓存的事件列表
    private var localEvents: List<Event> = emptyList()
    // 未登录时的本地事件列表
    private var offlineEvents: List<Event> = emptyList()

    // 注册用户
    suspend fun registerUser(username: String, nickname: String, email: String, password: String): Result<User> {
        return networkDataManager.registerUser(username, nickname, email, password)
    }

    // 登录用户
    suspend fun loginUser(username: String, password: String): Result<User> {
        val result = networkDataManager.loginUser(username, password)
        if (result.isSuccess) {
            val user = result.getOrNull()!!
            saveUserLocally(user)
            // 保存上次登录的用户名
            saveLastUsername(username)
            // 登录成功后拉取用户数据并同步本地数据
            loadUserData(user.id)
            // 同步离线数据到数据库
            syncOfflineData(user.id)
        }
        return result
    }

    suspend fun loadUserData(userId: String) {
        try {
            Log.d(TAG, "Loading user data for userId: $userId")
            val result = networkDataManager.getEvents(userId)
            if (result.isSuccess) {
                localEvents = result.getOrNull() ?: emptyList()
                Log.d(TAG, "Successfully loaded ${localEvents.size} events.")
                saveEventsLocally(localEvents)
                
                // 为所有加载的事件设置提醒
                localEvents.forEach { event ->
                    try {
                        reminderManager.safeScheduleReminder(event)
                        Log.d(TAG, "设置提醒成功: ${event.eventName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "设置提醒失败: ${event.eventName}, ${e.message}")
                    }
                }
                Log.d(TAG, "已为${localEvents.size}个事件设置提醒")
            } else {
                Log.e(TAG, "Failed to get events: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading user data: ${e.message}")
        }
    }

    private suspend fun syncOfflineData(userId: String) {
        val offlineEventsToSync = loadOfflineEventsLocally()
        if (offlineEventsToSync.isNotEmpty()) {
            Log.d(TAG, "Syncing ${offlineEventsToSync.size} offline events.")
            // 同步离线事件到服务器，并设置更高的sortOrder让它们排在最上面
            val currentMaxSortOrder = localEvents.maxOfOrNull { it.sortOrder } ?: -1
            var syncSortOrder = currentMaxSortOrder + 1
            
            for (event in offlineEventsToSync) {
                try {
                    val syncEvent = event.copy(
                        status = EventStatus.NORMAL,
                        sortOrder = syncSortOrder++
                    )
                    val result = networkDataManager.saveEvent(syncEvent, userId)
                    if (!result.isSuccess) {
                        Log.e(TAG, "Failed to sync offline event ${event.id}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while syncing offline event ${event.id}: ${e.message}")
                }
            }
            clearOfflineEvents()
            loadUserData(userId) // Refresh data after sync
        }
    }

    fun getEvents(): List<Event> {
        return if (isLoggedIn()) {
            Log.d(TAG, "Getting events for logged in user. Count: ${localEvents.size}")
            // 直接按sortOrder降序排序，保持用户设置的顺序
            // 不进行自动过期事件排序，完全尊重用户的手动排序
            localEvents.sortedByDescending { it.sortOrder }
        } else {
            Log.d(TAG, "Getting events for offline user. Count: ${offlineEvents.size}")
            // 直接按sortOrder降序排序，保持用户设置的顺序
            offlineEvents.sortedByDescending { it.sortOrder }
        }
    }

    suspend fun addEvent(event: Event) {
        Log.d(TAG, "Adding event: $event")
        if (isLoggedIn()) {
            val currentUser = getCurrentUser()!!
            try {
                val eventToSave = event.copy(
                    status = EventStatus.NORMAL,
                    backgroundId = assignBackgroundId(event)
                ).withAutoDetectedType() // 自动检测并设置事件类型
                val result = networkDataManager.saveEvent(eventToSave, currentUser.id)
                if (result.isSuccess) {
                    Log.d(TAG, "Event saved to network.")
                    // 关键修复：先立即将新事件添加到本地缓存
                    localEvents = localEvents + eventToSave
                    saveEventsLocally(localEvents)
                    reminderManager.scheduleReminder(eventToSave)
                    // 触发常驻通知更新（此时本地数据已包含新事件）
                    updatePersistentNotificationIfEnabled()
                    // 然后异步从服务器刷新最新数据（确保与服务器同步，包括服务器生成的ID）
                    loadUserData(currentUser.id)
                } else {
                    Log.e(TAG, "Failed to save event to network: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while adding event: ${e.message}")
            }
        } else {
            Log.d(TAG, "Adding event to offline cache.")
            val offlineEvent = event.copy(
                id = "offline_${System.currentTimeMillis()}",
                status = EventStatus.NORMAL,
                backgroundId = assignBackgroundId(event)
            ).withAutoDetectedType() // 自动检测并设置事件类型
            offlineEvents = offlineEvents + offlineEvent
            saveOfflineEventsLocally(offlineEvents)
            reminderManager.scheduleReminder(offlineEvent)
            // 触发常驻通知更新
            updatePersistentNotificationIfEnabled()
        }
    }

    suspend fun updateEvent(event: Event) {
        Log.d(TAG, "Updating event: ${event.id}")
        if (isLoggedIn()) {
            try {
                val eventToUpdate = event.copy(
                    status = EventStatus.UPDATED,
                    backgroundId = updateBackgroundId(event)
                ).withAutoDetectedType() // 自动检测并设置事件类型
                val result = networkDataManager.updateEvent(eventToUpdate)
                if (result.isSuccess) {
                    Log.d(TAG, "Event updated on network. Updating local cache.")
                    // 直接更新本地缓存，而不是重新加载所有数据
                    localEvents = localEvents.map { if (it.id == eventToUpdate.id) eventToUpdate else it }
                    saveEventsLocally(localEvents) // 保存到SharedPreferences
                    reminderManager.updateReminder(eventToUpdate)
                    // 触发常驻通知更新
                    updatePersistentNotificationIfEnabled()
                } else {
                    Log.e(TAG, "Failed to update event on network: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while updating event: ${e.message}")
            }
        } else {
            Log.d(TAG, "Updating event in offline cache.")
            val updatedEvent = event.copy(backgroundId = updateBackgroundId(event)).withAutoDetectedType()
            offlineEvents = offlineEvents.map { if (it.id == updatedEvent.id) updatedEvent else it }
            saveOfflineEventsLocally(offlineEvents)
            reminderManager.updateReminder(updatedEvent)
            // 触发常驻通知更新
            updatePersistentNotificationIfEnabled()
        }
    }

    suspend fun deleteEvent(eventId: String) {
        Log.d(TAG, "Deleting event: $eventId")
        if (isLoggedIn()) {
            try {
                // 直接使用数据库语法更新状态为已删除
                val result = networkDataManager.updateEventStatus(eventId, EventStatus.DELETED)
                if (result.isSuccess) {
                    Log.d(TAG, "Event marked as deleted in database.")
                    // 关键修复：先立即从本地缓存中删除，确保通知更新时能获取到正确的数据
                    localEvents = localEvents.filter { it.id != eventId }
                    saveEventsLocally(localEvents)
                    reminderManager.cancelReminder(eventId)
                    // 触发常驻通知更新（此时本地数据已更新）
                    updatePersistentNotificationIfEnabled()
                    // 然后异步从服务器刷新最新数据（确保与服务器同步）
                    getCurrentUser()?.let { loadUserData(it.id) }
                } else {
                    Log.e(TAG, "Failed to mark event as deleted: ${result.exceptionOrNull()?.message}")
                    // 如果网络更新失败，直接从本地列表移除
                    localEvents = localEvents.filter { it.id != eventId }
                    saveEventsLocally(localEvents)
                    reminderManager.cancelReminder(eventId)
                    // 触发常驻通知更新
                    updatePersistentNotificationIfEnabled()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while deleting event: ${e.message}")
                // 异常情况下直接从本地列表移除
                localEvents = localEvents.filter { it.id != eventId }
                saveEventsLocally(localEvents)
                reminderManager.cancelReminder(eventId)
                // 触发常驻通知更新
                updatePersistentNotificationIfEnabled()
            }
        } else {
            Log.d(TAG, "Deleting event from offline cache.")
            offlineEvents = offlineEvents.filter { it.id != eventId }
            saveOfflineEventsLocally(offlineEvents)
            reminderManager.cancelReminder(eventId)
            // 触发常驻通知更新
            updatePersistentNotificationIfEnabled()
        }
    }

    suspend fun refreshEvents() {
        Log.d(TAG, "Refreshing events.")
        if (isLoggedIn()) {
            getCurrentUser()?.let { loadUserData(it.id) }
        } else {
            offlineEvents = loadOfflineEventsLocally()
            Log.d(TAG, "Refreshed offline events. Count: ${offlineEvents.size}")
        }
    }

    fun forceRefreshEvents() {
        Log.d(TAG, "Force refreshing events from local storage.")
        localEvents = loadEventsLocally()
        offlineEvents = loadOfflineEventsLocally()
        Log.d(TAG, "Force refresh complete. Online count: ${localEvents.size}, Offline count: ${offlineEvents.size}")
    }

    // 本地方法
    private fun saveUserLocally(user: User) {
        val userJson = gson.toJson(user)
        sharedPreferences.edit().putString("current_user", userJson).commit()
        // 保存上次登录的账号
        sharedPreferences.edit().putString("last_login_username", user.username).commit()
    }

    private fun saveEventsLocally(events: List<Event>) {
        val eventsJson = gson.toJson(events)
        sharedPreferences.edit().putString("local_events", eventsJson).commit()
    }

    private fun saveOfflineEventsLocally(events: List<Event>) {
        val eventsJson = gson.toJson(events)
        sharedPreferences.edit().putString("offline_events", eventsJson).commit()
    }

    private fun loadEventsLocally(): List<Event> {
        val eventsJson = sharedPreferences.getString("local_events", null)
        return if (eventsJson != null && eventsJson.isNotEmpty()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<Event>>() {}.type
                val events = gson.fromJson<List<Event>>(eventsJson, type)
                events ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events locally: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun loadOfflineEventsLocally(): List<Event> {
        val eventsJson = sharedPreferences.getString("offline_events", null)
        return if (eventsJson != null && eventsJson.isNotEmpty()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<Event>>() {}.type
                val events = gson.fromJson<List<Event>>(eventsJson, type)
                events ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading offline events locally: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun clearOfflineEvents() {
        sharedPreferences.edit().remove("offline_events").commit()
        offlineEvents = emptyList()
    }

    fun getCurrentUser(): User? {
        val userJson = sharedPreferences.getString("current_user", null)
        return if (userJson != null && userJson.isNotEmpty()) {
            try {
                gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user data: ${e.message}")
                null
            }
        } else null
    }

    fun logout() {
        Log.d(TAG, "Logging out.")
        sharedPreferences.edit()
            .remove("current_user")
            .remove("local_events")
            .remove("offline_events") // 同时清除离线事件缓存
            .commit()
        localEvents = emptyList()
        offlineEvents = emptyList() // 清空离线事件列表
    }

    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * 获取本地缓存的事件（登录用户）
     */
    fun getLocalEvents(): List<Event> {
        return loadEventsLocally()
    }

    /**
     * 获取离线事件（未登录用户）
     */
    fun getOfflineEvents(): List<Event> {
        return loadOfflineEventsLocally()
    }

    suspend fun initializeLocalData() {
        Log.d(TAG, "Initializing local data.")
        localEvents = loadEventsLocally()
        offlineEvents = loadOfflineEventsLocally()
        
        // 为本地缓存的离线事件设置提醒
        offlineEvents.forEach { event ->
            try {
                reminderManager.safeScheduleReminder(event)
                Log.d(TAG, "为离线事件设置提醒成功: ${event.eventName}")
            } catch (e: Exception) {
                Log.e(TAG, "为离线事件设置提醒失败: ${event.eventName}, ${e.message}")
            }
        }
        
        getCurrentUser()?.let {
            Log.d(TAG, "User is logged in. Refreshing data from network.")
            loadUserData(it.id)
        } ?: run {
            // 如果未登录，为本地缓存的离线事件设置提醒
            Log.d(TAG, "User not logged in. 已为${offlineEvents.size}个离线事件设置提醒")
        }
    }

    /**
     * 获取上次登录的账号
     */
    fun getLastLoginUsername(): String? {
        return sharedPreferences.getString("last_login_username", null)
    }

    /**
     * 记录通知权限提示时间
     */
    fun recordNotificationPromptTime() {
        sharedPreferences.edit().putLong("last_notification_prompt", System.currentTimeMillis()).commit()
    }

    /**
     * 检查是否需要显示通知权限提示（每7天一次）
     */
    fun shouldShowNotificationPrompt(): Boolean {
        val lastPromptTime = sharedPreferences.getLong("last_notification_prompt", 0)
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastPromptTime > sevenDaysInMillis
    }

    // 发送验证码
    suspend fun sendVerificationCode(username: String, email: String): Result<String> {
        return networkDataManager.sendVerificationCode(username, email)
    }
    
    // 验证验证码并重置密码
    suspend fun resetPasswordWithCode(username: String, email: String, code: String, newPassword: String): Result<String> {
        return networkDataManager.resetPasswordWithCode(username, email, code, newPassword)
    }
    
    // 验证验证码（不重置密码，仅验证）
    suspend fun verifyCode(username: String, email: String, code: String): Result<String> {
        return networkDataManager.verifyCode(username, email, code)
    }

    /**
     * 为新事件分配背景ID - 统一使用随机10张背景图
     */
    private fun assignBackgroundId(event: Event): Int {
        return when {
            event.backgroundId != 0 -> event.backgroundId // 如果已经有背景ID，保持不变
            else -> {
                // 所有事件统一从10张背景图中随机分配 (1-10)
                CardStyleManager.getRandomStyleId()
            }
        }
    }

    /**
     * 更新事件时的背景ID处理 - 统一使用随机10张背景图
     */
    private fun updateBackgroundId(event: Event): Int {
        // 更新时保持原有背景，不进行特殊处理
        return event.backgroundId
    }

    /**
     * 更新事件排序
     */
    suspend fun updateEventOrder(events: List<Event>) {
        Log.d(TAG, "Updating event order for ${events.size} events")
        if (isLoggedIn()) {
            val currentUser = getCurrentUser()!!
            try {
                // 批量更新排序
                val result = networkDataManager.updateEventOrder(events, currentUser.id)
                if (result.isSuccess) {
                    Log.d(TAG, "Event order updated successfully")
                    // 更新本地缓存
                    localEvents = events
                    saveEventsLocally(localEvents)
                } else {
                    Log.e(TAG, "Failed to update event order: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while updating event order: ${e.message}")
            }
        } else {
            Log.d(TAG, "Updating offline event order")
            offlineEvents = events
            saveOfflineEventsLocally(offlineEvents)
        }
    }

    // 获取当前用户ID
    fun getCurrentUserId(): String? {
        return getCurrentUser()?.id
    }

    // 隐藏最近任务设置
    fun isHideRecentTaskEnabled(): Boolean {
        return sharedPreferences.getBoolean("hide_recent_task", false)
    }

    fun setHideRecentTask(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("hide_recent_task", enabled).commit()
    }

    // 常驻通知设置
    fun isPersistentNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("persistent_notification", false)
    }

    fun setPersistentNotification(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("persistent_notification", enabled).commit()
    }

    // 自动排序过期事件设置
    fun isAutoSortExpiredEventsEnabled(): Boolean {
        return sharedPreferences.getBoolean("auto_sort_expired_events", true) // 默认开启
    }

    fun setAutoSortExpiredEvents(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_sort_expired_events", enabled).commit()
    }

    // 保存上次登录的用户名
    fun saveLastUsername(username: String) {
        sharedPreferences.edit().putString("last_login_username", username).commit()
    }

    // 获取上次登录的用户名
    fun getLastUsername(): String {
        return sharedPreferences.getString("last_login_username", "") ?: ""
    }

    // 麻将计分云端同步方法
    suspend fun saveMahjongScoreToCloud(
        recordTime: String,
        winnerPosition: String,
        winnerFan: Double,
        positionData: String,
        calculationDetail: String,
        finalAmounts: String
    ) {
        try {
            val currentUser = getCurrentUser()
            if (currentUser != null) {
                networkDataManager.saveMahjongScore(
                    userId = currentUser.id,
                    recordTime = recordTime,
                    winnerPosition = winnerPosition,
                    winnerFan = winnerFan,
                    positionData = positionData,
                    calculationDetail = calculationDetail,
                    finalAmounts = finalAmounts
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mahjong score to cloud", e)
        }
    }

    /**
     * 如果常驻通知已开启，则触发通知内容更新
     * 用于 CRUD 操作后立即反映数据变化
     */
    private fun updatePersistentNotificationIfEnabled() {
        try {
            if (isPersistentNotificationEnabled()) {
                PersistentNotificationService.updateNotification(context)
                Log.d(TAG, "已触发常驻通知更新")
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发常驻通知更新失败: ${e.message}")
        }
    }

companion object {
private const val TAG = "DataManager"
}
}