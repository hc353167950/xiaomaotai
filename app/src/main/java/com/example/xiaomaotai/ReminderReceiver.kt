package com.example.xiaomaotai

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 触发适配器：闹钟到点后只做三件事——
 * 验证事件仍存在、凭台账去重、委托 [ReminderNotifier] 发通知。
 * 开机广播则重设全部提醒。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.xiaomaotai.REMINDER" -> handleReminder(context, intent, isBackup = false)
            "com.example.xiaomaotai.BACKUP_REMINDER" -> handleReminder(context, intent, isBackup = true)
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
        }
    }

    private fun handleReminder(context: Context, intent: Intent, isBackup: Boolean) {
        val eventName = intent.getStringExtra("event_name") ?: "纪念日"
        val eventId = intent.getStringExtra("event_id") ?: ""
        val daysRemaining = intent.getIntExtra("days_remaining", 0)
        val reminderHour = intent.getIntExtra("reminder_hour", 0)
        val eventDate = intent.getStringExtra("event_date") ?: ""
        // 「稍后提醒」重发的通知：不写台账，避免影响第二天的正常调度
        val isSnooze = intent.getBooleanExtra("is_snooze", false)

        // 纪念日本身：闰月年有两个（普通六月十六 / 闰六月十六），去重必须带上它
        val anniversary = intent.getStringExtra("anniversary")
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()

        Log.d(
            TAG,
            "处理${if (isBackup) "备份" else ""}提醒: $eventName, " +
                "纪念日=$anniversary, 提前${daysRemaining}天, ${reminderHour}:00"
        )

        val reminderManager = ReminderManager(context)

        // 备份提醒只在原始提醒未发出时兜底
        if (isBackup && reminderManager.hasReminderSentToday(
                eventId, daysRemaining, reminderHour, anniversary
            )
        ) {
            Log.d(TAG, "原始提醒已发送，跳过备份提醒: $eventName @ $anniversary")
            return
        }

        // 需要完整事件才能拿到语气、备注、提醒开关
        val storedEvent = if (eventId != "test_notification") {
            XiaoMaoTaiApp.eventStore(context).persistedEvents().firstOrNull { it.id == eventId }
        } else {
            null
        }

        // 事件已被删除则清理残留通知（测试通知除外）
        if (eventId != "test_notification" && storedEvent == null) {
            Log.d(TAG, "事件 $eventId 已被删除，取消通知")
            ReminderNotifier.cancelAll(context, eventId)
            return
        }

        // 统一闸门：事件存在、提醒开启、非正数模式、未被「今年不再提醒」静音。
        // 稍后提醒的重发同样要走这里——用户点了静音后，已排定的那一次重发
        // 必须一并拦下，否则「今年后面都不再提醒」就破了。
        // （稍后提醒仍然不写台账：它消耗的只是那一次，不影响后面的固定时间点。）
        if (eventId != "test_notification" &&
            !reminderManager.shouldDeliver(storedEvent, anniversary)
        ) {
            Log.d(TAG, "闸门拦截（已删除/关闭提醒/正数模式/今年已静音）: $eventName @ $anniversary")
            return
        }

        try {
            ReminderNotifier.sendReminder(
                context,
                storedEvent ?: Event(id = eventId, eventName = eventName, eventDate = eventDate),
                daysRemaining,
                reminderHour,
                anniversary = anniversary
            )
            if (eventId != "test_notification" && !isSnooze) {
                reminderManager.markReminderSentToday(eventId, daysRemaining, reminderHour, anniversary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败: ${e.message}")
        }
    }

    /** 开机后重新设置所有事件的提醒（AlarmManager 的闹钟重启后全部丢失） */
    private fun handleBootCompleted(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开机启动，开始重新设置提醒")
                kotlinx.coroutines.delay(5000) // 等待系统完全启动

                val reminderManager = ReminderManager(context)
                if (!reminderManager.canScheduleAlarms()) {
                    Log.w(TAG, "没有精确闹钟权限，无法重新设置提醒")
                    return@launch
                }

                val allEvents = XiaoMaoTaiApp.eventStore(context).persistedEvents()
                if (allEvents.isEmpty()) {
                    Log.d(TAG, "没有需要设置提醒的事件")
                    return@launch
                }

                var successCount = 0
                allEvents.forEach { event ->
                    var retryCount = 0
                    var success = false
                    while (retryCount < 3 && !success) {
                        try {
                            reminderManager.scheduleReminder(event)
                            successCount++
                            success = true
                        } catch (e: Exception) {
                            retryCount++
                            Log.w(TAG, "设置提醒失败(重试${retryCount}/3): ${event.eventName}, ${e.message}")
                            if (retryCount < 3) kotlinx.coroutines.delay(1000)
                        }
                    }
                }
                Log.d(TAG, "开机后成功重新设置了${successCount}/${allEvents.size}个事件的提醒")
            } catch (e: Exception) {
                Log.e(TAG, "开机后重新设置提醒失败: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
