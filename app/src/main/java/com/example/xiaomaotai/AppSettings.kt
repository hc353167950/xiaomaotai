package com.example.xiaomaotai

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置模块：8 个开关/偏好的唯一读写出口，纯键值存取，无业务逻辑。
 * 从 DataManager 拆出——设置读写不该和事件 CRUD、账号混在一个接口里。
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)

    // 隐藏最近任务
    fun isHideRecentTaskEnabled(): Boolean = prefs.getBoolean("hide_recent_task", false)
    fun setHideRecentTask(enabled: Boolean) {
        prefs.edit().putBoolean("hide_recent_task", enabled).commit()
    }

    // 常驻通知
    fun isPersistentNotificationEnabled(): Boolean = prefs.getBoolean("persistent_notification", false)
    fun setPersistentNotification(enabled: Boolean) {
        prefs.edit().putBoolean("persistent_notification", enabled).commit()
    }

    // 自动排序过期事件（默认开启）
    fun isAutoSortExpiredEventsEnabled(): Boolean = prefs.getBoolean("auto_sort_expired_events", true)
    fun setAutoSortExpiredEvents(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sort_expired_events", enabled).commit()
    }

    // 无障碍通知保活
    fun isAccessibilityNotificationEnabled(): Boolean = prefs.getBoolean("accessibility_notification", false)
    fun setAccessibilityNotification(enabled: Boolean) {
        prefs.edit().putBoolean("accessibility_notification", enabled).commit()
    }

    // UI 视觉风格：soft_diary / glass_countdown（默认 A 渐变卡片）
    fun getUiStyleId(): String = prefs.getString("ui_style", "soft_diary") ?: "soft_diary"
    fun setUiStyleId(styleId: String) {
        prefs.edit().putString("ui_style", styleId).commit()
    }

    // 通知权限提示节流（每7天最多提示一次）
    fun recordNotificationPromptTime() {
        prefs.edit().putLong("last_notification_prompt", System.currentTimeMillis()).commit()
    }

    fun shouldShowNotificationPrompt(): Boolean {
        val lastPromptTime = prefs.getLong("last_notification_prompt", 0)
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastPromptTime > sevenDaysInMillis
    }
}
