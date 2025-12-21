package com.example.xiaomaotai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * 无障碍服务 - 用于保活和实时更新通知栏
 *
 * 原理：
 * 1. 无障碍服务由系统 AccessibilityManagerService 管理，优先级极高
 * 2. 系统通过 bindService() 绑定，oom_adj 值很低（约 -700），几乎不会被杀
 * 3. 即使被杀，系统会在几秒内自动重新绑定启动
 * 4. 可以监听系统事件（如解锁屏幕），实现"用户看手机时立即更新"
 */
class NotificationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NotificationA11yService"
        // 复用 PersistentNotificationService 的通知ID和渠道，避免出现两个通知
        private const val CHANNEL_ID = PersistentNotificationService.CHANNEL_ID
        private const val NOTIFICATION_ID = PersistentNotificationService.NOTIFICATION_ID
        private const val UPDATE_INTERVAL = 60 * 60 * 1000L // 1小时检查一次

        /**
         * 检查无障碍服务是否已启用
         */
        fun isServiceEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${NotificationAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(serviceName) == true
        }

        /**
         * 跳转到无障碍设置页面
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateDate: String? = null
    private lateinit var notificationManager: NotificationManager

    private val updateRunnable = object : Runnable {
        override fun run() {
            checkAndUpdateNotification()
            // 继续下一次定时检查
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 配置服务（最小化权限请求，只监听窗口状态变化）
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }

        // 创建通知渠道
        createNotificationChannel()

        // 立即更新一次通知
        updateNotification()

        // 启动定时检查任务
        handler.post(updateRunnable)

        Log.d(TAG, "无障碍服务初始化完成，定时更新已启动")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听窗口状态变化（包括解锁屏幕、切换应用等）
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 检查是否跨天了，如果是则更新通知
                checkAndUpdateNotification()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        Log.d(TAG, "无障碍服务被销毁")
    }

    /**
     * 检查是否需要更新通知（日期变化时更新）
     */
    private fun checkAndUpdateNotification() {
        val today = getCurrentDateString()
        // 只有日期变化时才真正更新，避免频繁操作
        if (lastUpdateDate != today) {
            Log.d(TAG, "检测到日期变化: $lastUpdateDate -> $today，更新通知")
            updateNotification()
            lastUpdateDate = today
        }
    }

    /**
     * 更新通知栏内容
     */
    private fun updateNotification() {
        try {
            val dataManager = DataManager(this)

            // 检查用户是否开启了无障碍通知功能
            if (!dataManager.isAccessibilityNotificationEnabled()) {
                // 用户关闭了功能，移除通知
                notificationManager.cancel(NOTIFICATION_ID)
                Log.d(TAG, "无障碍通知功能已关闭，移除通知")
                return
            }

            val notificationText = generateDynamicNotificationText()
            showPersistentNotification(notificationText)
            Log.d(TAG, "通知已更新: $notificationText")

        } catch (e: Exception) {
            Log.e(TAG, "更新通知失败", e)
        }
    }

    /**
     * 生成动态通知文案
     */
    private fun generateDynamicNotificationText(): String {
        try {
            val dataManager = DataManager(this)
            val reminderManager = ReminderManager(this)

            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }

            if (allEvents.isEmpty()) {
                return "时间：暂无事件\n事件：添加纪念日，让重要时刻不被遗忘"
            }

            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 找到距离今天最近的所有事件
            val nearestEvents = mutableListOf<Event>()
            var minDays: Int = Int.MAX_VALUE

            allEvents.forEach { event ->
                try {
                    val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                    if (nextReminderDate != null) {
                        val eventCalendar = Calendar.getInstance().apply {
                            time = nextReminderDate
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val daysBetween = ((eventCalendar.timeInMillis - todayStart.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()

                        if (daysBetween >= 0) {
                            if (daysBetween < minDays) {
                                minDays = daysBetween
                                nearestEvents.clear()
                                nearestEvents.add(event)
                            } else if (daysBetween == minDays) {
                                nearestEvents.add(event)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理事件失败: ${event.eventName}", e)
                }
            }

            if (nearestEvents.isEmpty()) {
                return "时间：暂无事件\n事件：添加纪念日，让重要时刻不被遗忘"
            }

            // 获取日期显示文本
            val firstEvent = nearestEvents.first()
            val nextReminderDate = reminderManager.getNextReminderDate(firstEvent.eventDate)
            val dateText = if (nextReminderDate != null) {
                val parsedDate = DateParser.parse(firstEvent.eventDate)
                when (parsedDate?.type) {
                    DateParser.DateType.LUNAR -> {
                        DateParser.formatLunarForDisplay(parsedDate)
                    }
                    else -> {
                        val calendar = Calendar.getInstance().apply { time = nextReminderDate }
                        val month = calendar.get(Calendar.MONTH) + 1
                        val day = calendar.get(Calendar.DAY_OF_MONTH)
                        DateParser.formatSolarForDisplay(month, day)
                    }
                }
            } else {
                ""
            }

            // 处理多个事件名称显示
            val eventNamesText = when {
                nearestEvents.size <= 3 -> {
                    nearestEvents.joinToString("、") { it.eventName }
                }
                else -> {
                    nearestEvents.take(3).joinToString("、") { it.eventName } + "..."
                }
            }

            // 生成两行排版的通知文案
            val line1 = when {
                minDays == 0 -> "时间：今天（$dateText）"
                minDays == 1 -> "时间：明天（$dateText）"
                else -> "时间：$dateText（还有${minDays}天）"
            }
            val line2 = "事件：$eventNamesText"

            return "$line1\n$line2"

        } catch (e: Exception) {
            Log.e(TAG, "生成通知文案失败", e)
            return "时间：暂无事件\n事件：添加纪念日，让重要时刻不被遗忘"
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "纪念日提醒（无障碍）",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "通过无障碍服务保持通知栏实时更新"
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示常驻通知
     */
    private fun showPersistentNotification(content: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小茅台纪念日提醒")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 获取当前日期字符串
     */
    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
}
