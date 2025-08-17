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
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.d("ReminderForegroundService", "前台服务已创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundService()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        // START_STICKY: 被系统杀死后会自动重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d("ReminderForegroundService", "前台服务已销毁")
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 启动提醒检查任务
        startReminderCheckTask()
        
        Log.d("ReminderForegroundService", "前台服务已启动")
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
                    
                    // 检查是否还有24小时内的提醒，如果没有则停止服务
                    if (!hasUpcomingReminders()) {
                        Log.d("ReminderForegroundService", "没有即将到期的提醒，停止前台服务")
                        stopSelf()
                        break
                    }
                    
                    // 每10分钟检查一次
                    delay(10 * 60 * 1000)
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
                            // 发送提醒通知
                            sendReminderNotification(event, eventCalendar)
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
     * 检查是否有24小时内的提醒
     */
    private fun hasUpcomingReminders(): Boolean {
        return try {
            val dataManager = DataManager(this)
            val reminderManager = ReminderManager(this)
            
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            val now = Calendar.getInstance()
            val twentyFourHoursLater = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 24)
            }
            
            allEvents.any { event ->
                val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                if (nextReminderDate != null) {
                    val eventCalendar = Calendar.getInstance().apply {
                        time = nextReminderDate
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    eventCalendar.timeInMillis in now.timeInMillis..twentyFourHoursLater.timeInMillis
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderForegroundService", "检查即将到期提醒失败: ${e.message}")
            false
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
