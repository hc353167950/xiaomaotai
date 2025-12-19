package com.example.xiaomaotai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 系统时间变化广播接收器
 * 监听系统时间/日期/时区变化，及时更新常驻通知栏和列表天数
 *
 * 监听的广播：
 * - ACTION_TIME_CHANGED: 用户手动修改系统时间
 * - ACTION_DATE_CHANGED: 日期发生变化（自然过零点或手动修改）
 * - ACTION_TIMEZONE_CHANGED: 时区变化
 */
class TimeChangedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeChangedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED -> {
                Log.d(TAG, "系统时间已变化（用户手动修改）")
                handleTimeChange(context, "TIME_CHANGED")
            }
            Intent.ACTION_DATE_CHANGED -> {
                Log.d(TAG, "系统日期已变化（过零点或手动修改）")
                handleTimeChange(context, "DATE_CHANGED")
            }
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "系统时区已变化")
                handleTimeChange(context, "TIMEZONE_CHANGED")
            }
        }
    }

    private fun handleTimeChange(context: Context, reason: String) {
        try {
            Log.d(TAG, "处理时间变化: $reason")

            // 1. 更新常驻通知栏内容
            val dataManager = DataManager(context)
            if (dataManager.isPersistentNotificationEnabled()) {
                Log.d(TAG, "常驻通知已开启，触发更新")
                PersistentNotificationService.updateNotification(context)
            }

            // 2. 发送广播通知MainActivity刷新列表
            // 使用自定义广播，让MainActivity的生命周期观察者处理刷新
            val refreshIntent = Intent("com.example.xiaomaotai.ACTION_REFRESH_EVENTS")
            refreshIntent.setPackage(context.packageName)
            context.sendBroadcast(refreshIntent)
            Log.d(TAG, "已发送事件刷新广播")

            // 3. 重新调度所有提醒（时间变化可能影响提醒触发）
            rescheduleAllReminders(context)

        } catch (e: Exception) {
            Log.e(TAG, "处理时间变化失败: ${e.message}", e)
        }
    }

    /**
     * 重新调度所有事件的提醒
     * 时间变化后，之前设置的AlarmManager可能不再准确
     */
    private fun rescheduleAllReminders(context: Context) {
        try {
            val dataManager = DataManager(context)
            val reminderManager = ReminderManager(context)

            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }

            Log.d(TAG, "重新调度 ${allEvents.size} 个事件的提醒")

            // 重新调度每个事件的提醒
            allEvents.forEach { event ->
                try {
                    reminderManager.safeScheduleReminder(event)
                } catch (e: Exception) {
                    Log.e(TAG, "重新调度事件提醒失败: ${event.eventName}", e)
                }
            }

            Log.d(TAG, "所有提醒已重新调度")
        } catch (e: Exception) {
            Log.e(TAG, "重新调度提醒失败: ${e.message}", e)
        }
    }
}
