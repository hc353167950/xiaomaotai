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

        // 检查事件是否仍然存在，如果已被删除则不发送通知
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

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 根据剩余天数生成不同的通知内容
        val (title, content) = when (daysRemaining) {
            7 -> "纪念日提醒" to "还有7天就是「$eventName」了，记得准备哦！"
            1 -> "纪念日提醒" to "明天就是「$eventName」了，别忘记了！"
            0 -> "纪念日到了！" to "今天是「$eventName」，祝你开心！"
            else -> "纪念日提醒" to "$reminderLabel：$eventName"
        }

        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content)) // 支持长文本显示
            .build()

        // 使用不同的通知ID避免覆盖
        val notificationId = "${eventId}_$daysRemaining".hashCode()
        notificationManager.notify(notificationId, notification)
    }
    
    private fun handleBootCompleted(context: Context) {
        // 开机后重新设置所有事件的提醒 - 支持APP被杀死后的提醒功能
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataManager = DataManager(context)
                val reminderManager = ReminderManager(context)
                
                // 获取所有事件（包括登录和未登录用户的事件）
                val allEvents = mutableListOf<Event>()
                
                // 添加本地离线事件（未登录用户的事件）
                allEvents.addAll(dataManager.getOfflineEvents())
                
                // 如果有登录用户，添加云端同步的事件
                dataManager.getCurrentUser()?.let { _ ->
                    allEvents.addAll(dataManager.getLocalEvents())
                }
                
                // 重新设置所有事件的提醒
                allEvents.forEach { event ->
                    reminderManager.scheduleReminder(event)
                }
                
                android.util.Log.d("ReminderReceiver", "开机后重新设置了${allEvents.size}个事件的提醒")
                
            } catch (e: Exception) {
                android.util.Log.e("ReminderReceiver", "开机后重新设置提醒失败: ${e.message}")
            }
        }
    }
}
