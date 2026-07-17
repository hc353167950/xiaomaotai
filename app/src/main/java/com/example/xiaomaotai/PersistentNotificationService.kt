package com.example.xiaomaotai

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 常驻通知保活适配器：在通知栏显示"下一个纪念日"常驻通知，
 * 并以15分钟轮询兜底检查到期提醒（AlarmManager 被厂商 ROM 限制时的最后防线）。
 *
 * 决策全部来自深模块：事件数据问 [EventStore]，倒计时问 [Countdown]，
 * "该不该发提醒"问 [ReminderSchedule.dueNow]，通知构建交给 [ReminderNotifier]。
 * 本服务只负责保活与轮询节奏。
 */
class PersistentNotificationService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private var serviceJob: Job? = null
    private var isServiceStarted = false
    private var timeChangeReceiver: BroadcastReceiver? = null
    private var lastUpdateTime = 0L // 防抖：记录上次更新时间

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
        registerTimeChangeReceiver()
        Log.d(TAG, "常驻通知服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
            }
            ACTION_UPDATE_NOTIFICATION -> {
                if (!XiaoMaoTaiApp.settings(this).isPersistentNotificationEnabled()) {
                    Log.d(TAG, "收到更新通知请求，但常驻通知未开启，忽略")
                    return START_NOT_STICKY
                }
                if (isServiceStarted) {
                    updatePersistentNotification()
                } else {
                    startAsForeground()
                }
            }
            ACTION_STOP -> {
                cancelKeepAliveAlarm()
                serviceJob?.cancel()
                isServiceStarted = false
                stopForeground(true)
                stopSelf()
                Log.d(TAG, "常驻通知已停止")
                return START_NOT_STICKY
            }
            else -> {
                // intent 为 null：服务被系统杀死后经 START_STICKY 重启
                if (!isServiceStarted) {
                    if (XiaoMaoTaiApp.settings(this).isPersistentNotificationEnabled()) {
                        startAsForeground()
                    } else {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = createPersistentNotification()
        startForeground(NOTIFICATION_ID, notification)
        isServiceStarted = true
        setupKeepAliveAlarm()
        startReminderCheckTask()
        Log.d(TAG, "常驻通知已显示，保活守护与提醒轮询已启动")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务移除APP时，若常驻通知仍开启则重启服务
        if (XiaoMaoTaiApp.settings(this).isPersistentNotificationEnabled()) {
            Log.d(TAG, "任务被移除，重启常驻通知服务")
            restartSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        unregisterTimeChangeReceiver()
        Log.d(TAG, "常驻通知服务已销毁")

        if (XiaoMaoTaiApp.settings(this).isPersistentNotificationEnabled()) {
            Log.d(TAG, "服务被销毁但常驻通知仍开启，尝试重启")
            restartSelf()
        }
    }

    private fun restartSelf() {
        try {
            val restartIntent = Intent(this, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败: ${e.message}")
        }
    }

    /** 常驻通知专用低优先级渠道（与提醒渠道分离，不打扰用户） */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "常驻通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持应用在后台运行"
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** 构建常驻通知：正文是"下一个纪念日"的动态文案 */
    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = generateDynamicNotificationText()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小茅台纪念日提醒")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .build()
    }

    /** AlarmManager 保活守护：30分钟后自唤醒，防止服务被杀后不再恢复 */
    private fun setupKeepAliveAlarm() {
        try {
            val pendingIntent = keepAlivePendingIntent()
            val triggerTime = System.currentTimeMillis() + 30 * 60 * 1000
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d(TAG, "已设置AlarmManager保活守护，30分钟后检查")
        } catch (e: Exception) {
            Log.e(TAG, "设置保活守护失败: ${e.message}")
        }
    }

    private fun cancelKeepAliveAlarm() {
        try {
            alarmManager.cancel(keepAlivePendingIntent())
        } catch (e: Exception) {
            Log.e(TAG, "取消保活守护失败: ${e.message}")
        }
    }

    private fun keepAlivePendingIntent(): PendingIntent {
        val intent = Intent(this, PersistentNotificationService::class.java).apply {
            action = ACTION_START
        }
        return PendingIntent.getService(
            this, KEEP_ALIVE_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 监听系统时间变化（手动改时间/跨零点），立即刷新常驻通知内容 */
    private fun registerTimeChangeReceiver() {
        if (timeChangeReceiver != null) return

        timeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "收到系统时间变化广播: ${intent?.action}")
                updatePersistentNotification()
            }
        }
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeChangeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeChangeReceiver, intentFilter)
        }
    }

    private fun unregisterTimeChangeReceiver() {
        timeChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "注销时间变化广播接收器失败: ${e.message}")
            }
        }
        timeChangeReceiver = null
    }

    /**
     * 最近的即将到来的事件（同一天可能有多个）。
     * 事件来源 [EventStore.persistedEvents]，天数来自 [Countdown]。
     */
    private fun getNextUpcomingEvents(): Pair<List<Event>, Long>? {
        return try {
            val allEvents = XiaoMaoTaiApp.eventStore(this).persistedEvents()
            if (allEvents.isEmpty()) return null

            val withDays = allEvents.mapNotNull { event ->
                Countdown.of(event.eventDate)?.let { event to it.days }
            }.filter { it.second >= 0 }

            val minDays = withDays.minOfOrNull { it.second } ?: return null
            val nearest = withDays.filter { it.second == minDays }.map { it.first }
            Pair(nearest, minDays)
        } catch (e: Exception) {
            Log.e(TAG, "获取最近事件失败: ${e.message}")
            null
        }
    }

    /**
     * 生成动态通知文案，两行排版：
     * 时间：农历六月十八（还有225天）
     * 事件：好吃巨巨巨、金币不够过一会吧
     */
    private fun generateDynamicNotificationText(): String {
        val upcoming = getNextUpcomingEvents() ?: return "时间：暂无事件\n事件：添加纪念日，让重要时刻不被遗忘"
        val (events, daysUntil) = upcoming

        val firstEvent = events.first()
        val countdown = Countdown.of(firstEvent.eventDate)
        val dateText = if (countdown != null) {
            val parsedDate = DateParser.parse(firstEvent.eventDate)
            when (parsedDate?.type) {
                DateParser.DateType.LUNAR -> DateParser.formatLunarForDisplay(parsedDate)
                else -> DateParser.formatSolarForDisplay(countdown.nextDate.monthValue, countdown.nextDate.dayOfMonth)
            }
        } else ""

        val eventNamesText = if (events.size <= 3) {
            events.joinToString("、") { it.eventName }
        } else {
            events.take(3).joinToString("、") { it.eventName } + "..."
        }

        val line1 = when (daysUntil) {
            0L -> "时间：今天（$dateText）"
            1L -> "时间：明天（$dateText）"
            else -> "时间：$dateText（还有${daysUntil}天）"
        }
        return "$line1\n事件：$eventNamesText"
    }

    /** 更新常驻通知内容（1秒防抖） */
    private fun updatePersistentNotification() {
        try {
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime < 1000) return
            lastUpdateTime = now

            if (!XiaoMaoTaiApp.settings(this).isPersistentNotificationEnabled()) return

            notificationManager.notify(NOTIFICATION_ID, createPersistentNotification())
            Log.d(TAG, "已更新常驻通知内容")
        } catch (e: Exception) {
            Log.e(TAG, "更新常驻通知失败: ${e.message}")
        }
    }

    /**
     * 15分钟轮询：AlarmManager 不可靠时的提醒兜底。
     * "该不该发"由 [ReminderSchedule.dueNow] 判定，与 AlarmManager 路径共享台账去重。
     */
    private fun startReminderCheckTask() {
        serviceJob?.cancel()
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    checkAndSendReminders()
                    if (!XiaoMaoTaiApp.eventStore(this@PersistentNotificationService).hasAnyEvents()) {
                        Log.d(TAG, "没有任何事件，暂停提醒轮询")
                        break
                    }
                    delay(REMINDER_CHECK_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "提醒轮询任务异常: ${e.message}")
            }
        }
    }

    private fun checkAndSendReminders() {
        try {
            val reminderManager = ReminderManager(this)
            val allEvents = XiaoMaoTaiApp.eventStore(this).persistedEvents()
            val now = LocalDateTime.now()

            allEvents.forEach { event ->
                try {
                    ReminderSchedule.dueNow(event.eventDate, now).forEach { occ ->
                        if (!reminderManager.hasReminderSentToday(event.id, occ.daysBefore, occ.hour)) {
                            ReminderNotifier.sendReminder(this, event, occ.daysBefore, occ.hour)
                            reminderManager.markReminderSentToday(event.id, occ.daysBefore, occ.hour)
                            Log.d(TAG, "轮询发送提醒: ${event.eventName}, ${occ.label}, ${occ.hour}:00")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理事件提醒失败: ${event.eventName}, ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查提醒失败: ${e.message}")
        }
    }

    companion object {
        const val CHANNEL_ID = "persistent_notification_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START = "START_PERSISTENT_NOTIFICATION"
        const val ACTION_STOP = "STOP_PERSISTENT_NOTIFICATION"
        const val ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION"

        private const val TAG = "PersistentNotification"
        private const val KEEP_ALIVE_REQUEST_CODE = 9999
        private const val REMINDER_CHECK_INTERVAL = 15 * 60 * 1000L // 15分钟

        fun startService(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        /** CRUD 后触发通知内容刷新（常驻通知未开启时为空操作） */
        fun updateNotification(context: Context) {
            try {
                if (!XiaoMaoTaiApp.settings(context).isPersistentNotificationEnabled()) return
                val intent = Intent(context, PersistentNotificationService::class.java).apply {
                    action = ACTION_UPDATE_NOTIFICATION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "触发常驻通知更新失败: ${e.message}")
            }
        }
    }
}
