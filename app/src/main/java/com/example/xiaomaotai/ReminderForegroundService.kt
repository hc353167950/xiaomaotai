package com.example.xiaomaotai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import android.app.AlarmManager
import android.app.PendingIntent

/**
 * 提醒前台服务
 * 用于在Android 15等高版本上保证APP被杀死后仍能发送提醒
 * 只在有24小时内的提醒时启动，提醒发送后自动停止
 */
class ReminderForegroundService : Service() {
    
    companion object {
        const val CHANNEL_ID = "reminder_foreground_service"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_SERVICE = "START_REMINDER_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_REMINDER_SERVICE"
        
        /**
         * 启动前台服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d("ReminderForegroundService", "启动前台服务")
        }
        
        /**
         * 停止前台服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.stopService(intent)
            Log.d("ReminderForegroundService", "停止前台服务")
        }
    }
    
    private var serviceJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
        Log.d("ReminderForegroundService", "前台服务已创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                // 优化3：智能前台服务启动，支持自定义保活时长
                val eventId = intent.getStringExtra("event_id")
                val eventName = intent.getStringExtra("event_name")
                val hoursUntilEvent = intent.getLongExtra("hours_until_event", 24)
                val serviceDurationHours = intent.getLongExtra("service_duration_hours", 24)
                
                Log.d("ReminderForegroundService", "启动智能前台服务: $eventName, 距离事件${hoursUntilEvent}小时, 保活${serviceDurationHours}小时")
                
                startForegroundServiceWithDuration(eventId, eventName, serviceDurationHours)
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // 兼容旧版本调用
                startForegroundService()
            }
        }
        
        // 增强保活机制：START_STICKY + START_REDELIVER_INTENT
        // 确保服务被杀死后能够重启，并重新传递Intent
        return START_STICKY or START_REDELIVER_INTENT
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d("ReminderForegroundService", "前台服务已销毁")
        
        // 检查是否还有事件需要提醒
        val stillHasEvents = hasAnyEvents()
        
        if (stillHasEvents) {
            // 保持AlarmManager守护继续运行
            Log.d("ReminderForegroundService", "服务被销毁但仍有事件，保持AlarmManager守护运行")
            
            // 在服务被销毁时，尝试重新启动（针对国产ROM优化）
            try {
                Log.d("ReminderForegroundService", "尝试立即重启前台服务")
                val restartIntent = Intent(this, ReminderForegroundService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent)
                } else {
                    startService(restartIntent)
                }
            } catch (e: Exception) {
                Log.e("ReminderForegroundService", "重启服务失败: ${e.message}")
            }
        } else {
            // 没有事件时取消守护闹钟
            cancelKeepAliveAlarm()
            Log.d("ReminderForegroundService", "没有事件，已取消AlarmManager守护")
        }
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 启动提醒检查任务
        startReminderCheckTask()
        
        // 设置AlarmManager保活守护（针对国产ROM）
        setupKeepAliveAlarm()
        
        Log.d("ReminderForegroundService", "前台服务已启动")
    }
    
    /**
     * 优化3：启动智能前台服务，支持自定义保活时长
     * 根据事件紧急程度智能调整服务运行时间
     */
    private fun startForegroundServiceWithDuration(eventId: String?, eventName: String?, durationHours: Long) {
        val notification = createSmartForegroundNotification(eventName, durationHours)
        startForeground(NOTIFICATION_ID, notification)
        
        // 设置AlarmManager保活守护（针对国产ROM）
        setupKeepAliveAlarm()
        
        // 使用智能停止服务的定时器替代原有的提醒检查任务
        // 避免新旧逻辑冲突导致协程取消异常
        setupSmartServiceStop(durationHours)
        
        Log.d("ReminderForegroundService", "智能前台服务已启动: $eventName, 将运行${durationHours}小时")
    }
    
    /**
     * 创建智能前台服务通知，显示保活信息
     */
    private fun createSmartForegroundNotification(eventName: String?, durationHours: Long): Notification {
        val title = "小茅台 - 智能保活"
        val content = if (eventName != null) {
            "为「$eventName」保活${durationHours}小时，确保提醒送达"
        } else {
            "提醒服务运行中，确保重要日期不会错过"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级，不打扰用户
            .setOngoing(true) // 持续通知，用户无法滑动删除
            .setAutoCancel(false)
            .setSilent(true) // 静默通知
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()
    }
    
    /**
     * 设置智能服务停止定时器
     * 优化3：根据事件紧急程度自动停止服务，节省资源
     */
    private fun setupSmartServiceStop(durationHours: Long) {
        serviceJob?.cancel() // 取消之前的定时器
        
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val durationMillis = durationHours * 60 * 60 * 1000
                Log.d("ReminderForegroundService", "设置${durationHours}小时后自动停止服务")
                
                delay(durationMillis)
                
                // 检查是否还有24小时内的事件
                val hasUrgentEvents = hasEventsWithin24Hours()
                if (!hasUrgentEvents) {
                    Log.d("ReminderForegroundService", "保活时间到期且无紧急事件，自动停止服务")
                    stopSelf()
                } else {
                    Log.d("ReminderForegroundService", "保活时间到期但仍有紧急事件，继续运行")
                    // 继续运行，但缩短检查间隔
                    setupSmartServiceStop(2) // 再运行2小时后重新检查
                }
            } catch (e: Exception) {
                Log.e("ReminderForegroundService", "智能停止服务失败: ${e.message}")
            }
        }
    }
    
    /**
     * 检查是否有24小时内的事件
     */
    private fun hasEventsWithin24Hours(): Boolean {
        return try {
            val dataManager = DataManager(this)
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let { _ ->
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            val now = Calendar.getInstance()
            val in24Hours = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 24) }
            
            allEvents.any { event ->
                val reminderManager = ReminderManager(this)
                val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                nextReminderDate != null && 
                nextReminderDate.time in now.timeInMillis..in24Hours.timeInMillis
            }
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "检查24小时内事件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "提醒服务",
                NotificationManager.IMPORTANCE_LOW // 前台服务通知使用低重要性
            ).apply {
                description = "保持提醒功能在后台运行"
                enableVibration(false) // 前台服务通知不需要震动
                enableLights(false) // 不需要指示灯
                setSound(null, null) // 不需要声音
                setShowBadge(false) // 不显示角标
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建前台服务通知
     */
    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小茅台")
            .setContentText("提醒服务运行中，确保重要日期不会错过")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级，不打扰用户
            .setOngoing(true) // 持续通知，用户无法滑动删除
            .setAutoCancel(false)
            .setSilent(true) // 静默通知
            .build()
    }
    
    /**
     * 启动提醒检查任务
     */
    private fun startReminderCheckTask() {
        serviceJob?.cancel() // 取消之前的任务
        
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    checkAndSendReminders()
                    
                    // 检查是否还有任何事件，如果没有则停止服务
                    if (!hasAnyEvents()) {
                        Log.d("ReminderForegroundService", "没有任何事件，停止前台服务")
                        stopSelf()
                        break
                    }
                    
                    // 每15分钟检查一次
                    delay(15 * 60 * 1000)
                }
            } catch (e: Exception) {
                Log.e("ReminderForegroundService", "提醒检查任务异常: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 检查并发送到期的提醒
     */
    private suspend fun checkAndSendReminders() {
        try {
            val dataManager = DataManager(this)
            val reminderManager = ReminderManager(this)
            
            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            val now = Calendar.getInstance()
            
            allEvents.forEach { event ->
                try {
                    val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                    if (nextReminderDate != null) {
                        val eventCalendar = Calendar.getInstance().apply {
                            time = nextReminderDate
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        // 检查是否到了提醒时间（允许5分钟误差）
                        val timeDiff = Math.abs(now.timeInMillis - eventCalendar.timeInMillis)
                        if (timeDiff <= 5 * 60 * 1000) { // 5分钟内
                            // 检查是否已经提醒过
                            val daysDiff = ((eventCalendar.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            val reminderKey = "${event.id}_${daysDiff}_$today"
                            val sharedPrefs = getSharedPreferences("reminder_history", Context.MODE_PRIVATE)
                            
                            if (!sharedPrefs.getBoolean(reminderKey, false)) {
                                // 发送提醒通知
                                sendReminderNotification(event, eventCalendar)
                            } else {
                                Log.d("ReminderForegroundService", "事件已提醒过，跳过: ${event.eventName}, 剩余天数: $daysDiff")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReminderForegroundService", "处理事件提醒失败: ${event.eventName}, ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "检查提醒失败: ${e.message}")
        }
    }
    
    /**
     * 检查是否有任何事件
     */
    private fun hasAnyEvents(): Boolean {
        return try {
            val dataManager = DataManager(this)
            
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            allEvents.isNotEmpty()
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "检查事件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 设置AlarmManager保活守护机制
     * 专门应对VIVO等国产手机的严格后台管理
     */
    private fun setupKeepAliveAlarm() {
        try {
            val intent = Intent(this, ReminderForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            
            val pendingIntent = PendingIntent.getService(
                this,
                9999, // 使用固定ID避免重复创建
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            // 设置30分钟后的守护闹钟
            val triggerTime = System.currentTimeMillis() + 30 * 60 * 1000
            
            // 使用精确闹钟确保在VIVO等设备上也能触发
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            Log.d("ReminderForegroundService", "已设置AlarmManager保活守护，30分钟后检查")
            
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "设置保活守护失败: ${e.message}")
        }
    }
    
    /**
     * 取消AlarmManager保活守护
     */
    private fun cancelKeepAliveAlarm() {
        try {
            val intent = Intent(this, ReminderForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            
            val pendingIntent = PendingIntent.getService(
                this,
                9999,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d("ReminderForegroundService", "已取消AlarmManager保活守护")
            
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "取消保活守护失败: ${e.message}")
        }
    }
    
    /**
     * 发送提醒通知
     */
    private fun sendReminderNotification(event: Event, eventCalendar: Calendar) {
        try {
            val now = Calendar.getInstance()
            val daysDiff = ((eventCalendar.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
            
            val (title, content) = when {
                daysDiff > 1 -> "纪念日提醒" to "还有${daysDiff}天就是「${event.eventName}」了，记得准备哦！"
                daysDiff == 1 -> "纪念日提醒" to "明天就是「${event.eventName}」了，别忘记了！"
                else -> "纪念日到了！" to "今天是「${event.eventName}」，祝你开心！"
            }
            
            // 确保提醒通知渠道存在
            val reminderManager = ReminderManager(this)
            
            val notification = NotificationCompat.Builder(this, ReminderManager.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()
            
            val notificationId = event.id.hashCode()
            notificationManager.notify(notificationId, notification)
            
            // 标记提醒已发送
            val sharedPrefs = getSharedPreferences("reminder_history", Context.MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val reminderKey = "${event.id}_${daysDiff}_$today"
            sharedPrefs.edit().putBoolean(reminderKey, true).apply()
            
            Log.d("ReminderForegroundService", "前台服务发送提醒: ${event.eventName}, 剩余天数: $daysDiff")
            
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "发送提醒通知失败: ${e.message}")
        }
    }
}
