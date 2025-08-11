package com.example.xiaomaotai

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.xiaomaotai.REMINDER" -> {
                // 处理纪念日提醒
                handleReminderNotification(context, intent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 处理开机重启后的提醒重新设置
                handleBootCompleted(context)
            }
        }
    }
    
    private fun handleReminderNotification(context: Context, intent: Intent) {
        val eventName = intent.getStringExtra("event_name") ?: "纪念日"
        val eventId = intent.getStringExtra("event_id") ?: ""
        val daysRemaining = intent.getIntExtra("days_remaining", 0)
        val reminderLabel = intent.getStringExtra("reminder_label") ?: "就是今天"

        android.util.Log.d("ReminderReceiver", "处理提醒通知: $eventName, 剩余天数: $daysRemaining")

        // 检查事件是否仍然存在，如果已被删除则不发送通知（测试通知除外）
        if (eventId != "test_notification") {
            val dataManager = DataManager(context)
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let { _ ->
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            val eventExists = allEvents.any { it.id == eventId }
            if (!eventExists) {
                android.util.Log.d("ReminderReceiver", "事件 $eventId 已被删除，取消通知")
                // 清理可能存在的残留通知
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationId = "${eventId}_$daysRemaining".hashCode()
                notificationManager.cancel(notificationId)
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道存在
        val reminderManager = ReminderManager(context)
        
        // 根据剩余天数生成不同的通知内容
        val (title, content) = when (daysRemaining) {
            7 -> "纪念日提醒" to "还有7天就是「$eventName」了，记得准备哦！"
            1 -> "纪念日提醒" to "明天就是「$eventName」了，别忘记了！"
            0 -> "纪念日到了！" to "今天是「$eventName」，祝你开心！"
            else -> "纪念日提醒" to "$reminderLabel：$eventName"
        }

        try {
            val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 提高优先级确保显示
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // 添加默认声音、震动等
                .setStyle(NotificationCompat.BigTextStyle().bigText(content)) // 支持长文本显示
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()

            // 使用不同的通知ID避免覆盖
            val notificationId = "${eventId}_$daysRemaining".hashCode()
            notificationManager.notify(notificationId, notification)
            
            // 记录提醒已发送（避免重复提醒）
            if (eventId != "test_notification") {
                val reminderManager = ReminderManager(context)
                reminderManager.markReminderSentToday(eventId, daysRemaining)
            }
            
            android.util.Log.d("ReminderReceiver", "通知已发送: $title - $content, ID: $notificationId")
            
        } catch (e: Exception) {
            android.util.Log.e("ReminderReceiver", "发送通知失败: ${e.message}")
        }
    }
    
    private fun handleBootCompleted(context: Context) {
        // 开机后重新设置所有事件的提醒 - 支持APP被杀死后的提醒功能
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("ReminderReceiver", "开机启动，开始重新设置提醒")
                
                val dataManager = DataManager(context)
                val reminderManager = ReminderManager(context)
                
                // 检查权限
                if (!reminderManager.canScheduleAlarms()) {
                    android.util.Log.w("ReminderReceiver", "没有精确闹钟权限，无法重新设置提醒")
                    return@launch
                }
                
                // 获取所有事件（包括登录和未登录用户的事件）
                val allEvents = mutableListOf<Event>()
                
                // 添加本地离线事件（未登录用户的事件）
                val offlineEvents = dataManager.getOfflineEvents()
                allEvents.addAll(offlineEvents)
                android.util.Log.d("ReminderReceiver", "加载离线事件: ${offlineEvents.size}个")
                
                // 如果有登录用户，添加云端同步的事件
                dataManager.getCurrentUser()?.let { user ->
                    val localEvents = dataManager.getLocalEvents()
                    allEvents.addAll(localEvents)
                    android.util.Log.d("ReminderReceiver", "加载登录用户事件: ${localEvents.size}个")
                }
                
                // 重新设置所有事件的提醒
                var successCount = 0
                allEvents.forEach { event ->
                    try {
                        reminderManager.scheduleReminder(event)
                        successCount++
                    } catch (e: Exception) {
                        android.util.Log.e("ReminderReceiver", "重新设置事件提醒失败: ${event.eventName}, ${e.message}")
                    }
                }
                
                android.util.Log.d("ReminderReceiver", "开机后成功重新设置了${successCount}/${allEvents.size}个事件的提醒")
                
            } catch (e: Exception) {
                android.util.Log.e("ReminderReceiver", "开机后重新设置提醒失败: ${e.message}")
            }
        }
    }
}
