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
            "com.example.xiaomaotai.BACKUP_REMINDER" -> {
                // 处理备份提醒
                handleBackupReminderNotification(context, intent)
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
        val reminderHour = intent.getIntExtra("reminder_hour", 0)
        val reminderLabel = intent.getStringExtra("reminder_label") ?: "就是今天"

        android.util.Log.d("ReminderReceiver", "处理提醒通知: $eventName, 剩余天数: $daysRemaining, 提醒时间: ${reminderHour}:00")

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
                // 清理可能存在的残留通知（所有3个时间段）
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val reminderHours = listOf(0, 8, 12)
                reminderHours.forEach { hour ->
                    val notificationId = "${eventId}_${daysRemaining}_${hour}".hashCode()
                    notificationManager.cancel(notificationId)
                    android.util.Log.d("ReminderReceiver", "已取消通知: $notificationId (${hour}:00)")
                }
                // 同时清理旧格式的通知（兼容性）
                val oldNotificationId = "${eventId}_$daysRemaining".hashCode()
                notificationManager.cancel(oldNotificationId)
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道存在
        val reminderManager = ReminderManager(context)

        // 在Android 8.0+上必须先创建通知渠道
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                ReminderManager.CHANNEL_ID,
                "纪念日提醒",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "纪念日提醒通知"
                enableVibration(true)
                enableLights(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
            android.util.Log.d("ReminderReceiver", "通知渠道已创建")
        }

        // 根据剩余天数生成不同的通知内容
        val (title, content) = when (daysRemaining) {
            7 -> "纪念日提醒" to "还有7天就是「$eventName」了，记得准备哦！"
            1 -> "纪念日提醒" to "明天就是「$eventName」了，别忘记了！"
            0 -> "纪念日到了！" to "今天是「$eventName」，祝你开心！"
            else -> "纪念日提醒" to "$reminderLabel：$eventName"
        }

        try {
            // 创建点击通知后打开APP的Intent
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_notification", true)
                putExtra("event_id", eventId)
                putExtra("event_name", eventName)
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                eventId.hashCode(), // 使用eventId的hashCode作为requestCode
                openAppIntent,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 提高优先级确保显示
                .setCategory(NotificationCompat.CATEGORY_ALARM) // 优化2：系统级闹钟类别
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // 添加默认声音、震动等
                .setStyle(NotificationCompat.BigTextStyle().bigText(content)) // 支持长文本显示
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏显示
                .setOnlyAlertOnce(false) // 允许重复提醒
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentIntent(pendingIntent) // 添加点击打开APP的Intent
                .build()

            // 使用不同的通知ID避免覆盖
            val notificationId = "${eventId}_${daysRemaining}_${reminderHour}".hashCode()
            notificationManager.notify(notificationId, notification)

            // 记录提醒已发送（避免重复提醒）
            if (eventId != "test_notification") {
                val reminderManager = ReminderManager(context)
                reminderManager.markReminderSentToday(eventId, daysRemaining, reminderHour)
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
                
                // 延迟5秒等待系统完全启动
                kotlinx.coroutines.delay(5000)
                
                val dataManager = DataManager(context)
                val reminderManager = ReminderManager(context)
                
                // 检查权限
                if (!reminderManager.canScheduleAlarms()) {
                    android.util.Log.w("ReminderReceiver", "没有精确闹钟权限，无法重新设置提醒")
                    return@launch
                }
                
                // 检查电池优化状态
                if (!reminderManager.isIgnoringBatteryOptimizations()) {
                    android.util.Log.w("ReminderReceiver", "APP未加入电池优化白名单，提醒可能不稳定")
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
                
                if (allEvents.isEmpty()) {
                    android.util.Log.d("ReminderReceiver", "没有需要设置提醒的事件")
                    return@launch
                }
                
                // 重新设置所有事件的提醒，添加重试机制
                var successCount = 0
                allEvents.forEach { event ->
                    var retryCount = 0
                    var success = false
                    
                    while (retryCount < 3 && !success) {
                        try {
                            reminderManager.scheduleReminder(event)
                            successCount++
                            success = true
                            android.util.Log.d("ReminderReceiver", "成功设置提醒: ${event.eventName}")
                        } catch (e: Exception) {
                            retryCount++
                            android.util.Log.w("ReminderReceiver", "设置事件提醒失败(重试${retryCount}/3): ${event.eventName}, ${e.message}")
                            if (retryCount < 3) {
                                kotlinx.coroutines.delay(1000) // 等待1秒后重试
                            }
                        }
                    }
                }
                
                android.util.Log.d("ReminderReceiver", "开机后成功重新设置了${successCount}/${allEvents.size}个事件的提醒")
                
                // 记录开机重启恢复状态到SharedPreferences
                val prefs = context.getSharedPreferences("boot_recovery", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("last_boot_recovery", System.currentTimeMillis())
                    .putInt("recovered_events", successCount)
                    .putInt("total_events", allEvents.size)
                    .apply()
                
            } catch (e: Exception) {
                android.util.Log.e("ReminderReceiver", "开机后重新设置提醒失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理备份提醒通知
     * 优化1的一部分：处理1-3分钟后的备份提醒
     */
    private fun handleBackupReminderNotification(context: Context, intent: Intent) {
        val eventName = intent.getStringExtra("event_name") ?: "纪念日"
        val eventId = intent.getStringExtra("event_id") ?: ""
        val daysRemaining = intent.getIntExtra("days_remaining", 0)
        val reminderHour = intent.getIntExtra("reminder_hour", 0)
        val reminderLabel = intent.getStringExtra("reminder_label") ?: "就是今天"
        val backupDelay = intent.getIntExtra("backup_delay", 0)
        val originalTime = intent.getLongExtra("original_time", 0)

        android.util.Log.d("ReminderReceiver", "处理备份提醒通知: $eventName, 备份延迟: ${backupDelay}分钟, 提醒时间: ${reminderHour}:00")

        // 检查原始提醒是否已经发送过，如果已发送则跳过备份提醒
        val sharedPreferences = context.getSharedPreferences("reminder_history", Context.MODE_PRIVATE)
        val today = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
        val originalReminderKey = "${eventId}_${daysRemaining}_${reminderHour}_$today"

        if (sharedPreferences.getBoolean(originalReminderKey, false)) {
            android.util.Log.d("ReminderReceiver", "原始提醒已发送，跳过备份提醒: $eventName")
            return
        }

        // 检查事件是否仍然存在
        val dataManager = DataManager(context)
        val allEvents = mutableListOf<Event>()
        allEvents.addAll(dataManager.getOfflineEvents())
        dataManager.getCurrentUser()?.let { _ ->
            allEvents.addAll(dataManager.getLocalEvents())
        }

        val eventExists = allEvents.any { it.id == eventId }
        if (!eventExists) {
            android.util.Log.d("ReminderReceiver", "事件 $eventId 已被删除，取消备份通知")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道存在
        val reminderManager = ReminderManager(context)

        // 创建点击通知后打开APP的Intent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("event_id", eventId)
            putExtra("event_name", eventName)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openAppIntent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // 创建备份通知，使用系统级优先级
        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 $reminderLabel")
            .setContentText("$eventName 🎉")
            .setSubText("备份提醒 #$backupDelay") // 标明备份序号
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 最高优先级
            .setCategory(NotificationCompat.CATEGORY_ALARM) // 优化2：系统级闹钟类别
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 声音、震动、灯光
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏显示
            .setOnlyAlertOnce(false) // 允许重复提醒
            .setFullScreenIntent(null, true) // 全屏显示（高优先级）
            .setContentIntent(pendingIntent) // 添加点击打开APP的Intent
            .build()

        val notificationId = "${eventId}_${daysRemaining}_${reminderHour}_backup_$backupDelay".hashCode()
        notificationManager.notify(notificationId, notification)

        // 记录备份提醒已发送
        val backupReminderKey = "${eventId}_${daysRemaining}_${reminderHour}_backup_${backupDelay}_$today"
        sharedPreferences.edit().putBoolean(backupReminderKey, true).apply()

        android.util.Log.d("ReminderReceiver", "✅ 备份提醒 #$backupDelay 发送成功: $eventName (${reminderHour}:00), 通知ID: $notificationId")
    }
}
