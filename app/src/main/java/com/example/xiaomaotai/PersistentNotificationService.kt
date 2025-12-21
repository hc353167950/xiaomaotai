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
import java.util.Calendar

/**
 * 常驻通知服务 - 合并智能保活功能
 * 用于在通知栏显示常驻通知，点击可进入APP
 * 包含保活机制和智能提醒检查，确保后台运行稳定性和提醒准时送达
 * 方案B：合并智能保活功能，开启常驻通知 = 完整保活 + 提醒检查
 */
class PersistentNotificationService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private var serviceJob: Job? = null // 用于管理提醒检查协程任务
    private var isServiceStarted = false // 标记服务是否已启动为前台服务
    private var timeChangeReceiver: BroadcastReceiver? = null // 系统时间变化广播接收器
    private var lastUpdateTime = 0L // 防抖：记录上次更新时间

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
        // 注册系统时间变化广播接收器
        registerTimeChangeReceiver()
        Log.d(TAG, "常驻通知服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createPersistentNotification()
                startForeground(NOTIFICATION_ID, notification)
                isServiceStarted = true // 标记服务已启动为前台服务
                // 设置AlarmManager保活守护
                setupKeepAliveAlarm()
                // 启动智能提醒检查任务（方案B：合并智能保活功能）
                startReminderCheckTask()
                Log.d(TAG, "常驻通知已显示，保活守护已设置，智能提醒检查已启动")
            }
            ACTION_UPDATE_NOTIFICATION -> {
                // 处理外部触发的通知更新请求
                // 首先检查用户是否开启了常驻通知
                val dataManager = DataManager(this)
                if (!dataManager.isPersistentNotificationEnabled()) {
                    Log.d(TAG, "收到更新通知请求，但常驻通知未开启，忽略")
                    return START_NOT_STICKY
                }

                // 只有在服务已经启动为前台服务时才更新通知
                if (isServiceStarted) {
                    triggerNotificationUpdate()
                    Log.d(TAG, "收到更新通知请求，已触发更新")
                } else {
                    // 如果服务还未启动为前台服务，按照正常启动流程处理
                    Log.d(TAG, "收到更新通知请求，服务未启动，按正常流程启动")
                    val notification = createPersistentNotification()
                    startForeground(NOTIFICATION_ID, notification)
                    isServiceStarted = true
                    setupKeepAliveAlarm()
                    startReminderCheckTask()
                }
            }
            ACTION_STOP -> {
                // 取消保活守护
                cancelKeepAliveAlarm()
                // 取消提醒检查任务
                serviceJob?.cancel()
                isServiceStarted = false // 标记服务已停止
                stopForeground(true)
                stopSelf()
                Log.d(TAG, "常驻通知已停止")
                return START_NOT_STICKY
            }
            else -> {
                // 处理 intent 为 null 或 action 为 null 的情况
                // 这种情况通常发生在服务被系统杀死后通过 START_STICKY 重启
                Log.d(TAG, "服务重启（intent action 为空），检查是否需要启动前台服务")
                if (!isServiceStarted) {
                    // 检查用户是否开启了常驻通知
                    val dataManager = DataManager(this)
                    if (dataManager.isPersistentNotificationEnabled()) {
                        Log.d(TAG, "常驻通知已开启，启动前台服务")
                        val notification = createPersistentNotification()
                        startForeground(NOTIFICATION_ID, notification)
                        isServiceStarted = true
                        setupKeepAliveAlarm()
                        startReminderCheckTask()
                    } else {
                        // 用户未开启常驻通知，停止服务
                        Log.d(TAG, "常驻通知未开启，停止服务")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }
        }

        // 使用START_STICKY，让系统尽可能重启服务
        // 在APP后台被系统杀掉后，服务会尝试重启
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 当用户从最近任务列表移除APP时触发
        // 如果常驻通知仍然开启，则重启服务
        val dataManager = DataManager(this)
        if (dataManager.isPersistentNotificationEnabled()) {
            Log.d(TAG, "任务被移除，重启常驻通知服务")
            val restartIntent = Intent(this, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 取消提醒检查任务
        serviceJob?.cancel()
        // 注销系统时间变化广播接收器
        unregisterTimeChangeReceiver()
        Log.d(TAG, "常驻通知服务已销毁")

        // 检查是否应该重启服务
        val dataManager = DataManager(this)
        if (dataManager.isPersistentNotificationEnabled()) {
            Log.d(TAG, "服务被销毁但常驻通知仍开启，尝试重启")
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
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "常驻通知",
                NotificationManager.IMPORTANCE_LOW // 低重要性，不打扰用户
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

    /**
     * 创建常驻通知
     */
    private fun createPersistentNotification(): Notification {
        // 创建点击通知后打开APP的Intent
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

        // 获取动态文案
        val notificationText = generateDynamicNotificationText()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小茅台纪念日提醒")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级
            .setOngoing(true) // 持续通知，用户无法滑动删除
            .setAutoCancel(false) // 点击后不自动取消
            .setSilent(true) // 静默通知
            .setContentIntent(pendingIntent) // 设置点击事件
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(notificationText)) // 支持多行显示
            .build()
    }

    /**
     * 设置AlarmManager保活守护
     * 定期检查服务是否运行，如果被杀掉则重启
     */
    private fun setupKeepAliveAlarm() {
        try {
            val intent = Intent(this, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }

            val pendingIntent = PendingIntent.getService(
                this,
                KEEP_ALIVE_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // 每30分钟检查一次服务状态
            val triggerTime = System.currentTimeMillis() + 30 * 60 * 1000

            // 使用精准闹钟确保在厂商ROM下也能触发
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

            Log.d(TAG, "已设置AlarmManager保活守护，30分钟后检查")
        } catch (e: Exception) {
            Log.e(TAG, "设置保活守护失败: ${e.message}")
        }
    }

    /**
     * 取消AlarmManager保活守护
     */
    private fun cancelKeepAliveAlarm() {
        try {
            val intent = Intent(this, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }

            val pendingIntent = PendingIntent.getService(
                this,
                KEEP_ALIVE_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "已取消AlarmManager保活守护")
        } catch (e: Exception) {
            Log.e(TAG, "取消保活守护失败: ${e.message}")
        }
    }

    // ============ 智能提醒检查功能 (方案B：合并智能保活) ============

    /**
     * 注册系统时间变化广播接收器
     * 监听 ACTION_TIME_CHANGED、ACTION_DATE_CHANGED、ACTION_TIMEZONE_CHANGED
     * 当系统时间被手动修改时，立即更新常驻通知内容
     */
    private fun registerTimeChangeReceiver() {
        if (timeChangeReceiver != null) {
            Log.d(TAG, "时间变化广播接收器已注册，跳过")
            return
        }

        timeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "收到系统时间变化广播: ${intent?.action}")
                // 立即更新常驻通知内容
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
        Log.d(TAG, "已注册系统时间变化广播接收器")
    }

    /**
     * 注销系统时间变化广播接收器
     */
    private fun unregisterTimeChangeReceiver() {
        timeChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "已注销系统时间变化广播接收器")
            } catch (e: Exception) {
                Log.e(TAG, "注销时间变化广播接收器失败: ${e.message}")
            }
        }
        timeChangeReceiver = null
    }

    /**
     * 获取最近的即将到来的事件列表（支持同一天多个事件）
     * @return Pair<事件列表, 距离天数> 或 null（如果没有事件）
     */
    private fun getNextUpcomingEvent(): Pair<List<Event>, Int>? {
        try {
            val dataManager = DataManager(this)
            val reminderManager = ReminderManager(this)

            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }

            Log.d(TAG, "获取事件列表，共${allEvents.size}个事件")

            if (allEvents.isEmpty()) {
                Log.d(TAG, "事件列表为空，返回null")
                return null
            }

            val now = Calendar.getInstance()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 找到距离今天最近的所有事件（可能有多个同一天的）
            val nearestEvents = mutableListOf<Event>()
            var minDays: Int = Int.MAX_VALUE

            allEvents.forEach { event ->
                try {
                    val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                    Log.d(TAG, "事件: ${event.eventName}, 日期: ${event.eventDate}, nextReminderDate: $nextReminderDate")

                    if (nextReminderDate != null) {
                        val eventCalendar = Calendar.getInstance().apply {
                            time = nextReminderDate
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        // 计算距离天数
                        val daysBetween = ((eventCalendar.timeInMillis - todayStart.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()

                        Log.d(TAG, "事件: ${event.eventName}, 距离天数: $daysBetween, 当前最小天数: $minDays")

                        // 只考虑今天或未来的事件
                        if (daysBetween >= 0) {
                            if (daysBetween < minDays) {
                                // 发现更近的事件，清空列表并添加新事件
                                minDays = daysBetween
                                nearestEvents.clear()
                                nearestEvents.add(event)
                                Log.d(TAG, "更新最近事件: ${event.eventName}, 距离${daysBetween}天")
                            } else if (daysBetween == minDays) {
                                // 同一天的事件，添加到列表
                                nearestEvents.add(event)
                                Log.d(TAG, "添加同一天事件: ${event.eventName}, 距离${daysBetween}天")
                            }
                        }
                    } else {
                        Log.w(TAG, "事件 ${event.eventName} 的 nextReminderDate 为 null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理事件失败: ${event.eventName}, ${e.message}")
                    e.printStackTrace()
                }
            }

            // 返回结果
            return if (nearestEvents.isEmpty()) {
                Log.w(TAG, "未找到任何未来的事件")
                null
            } else {
                Log.d(TAG, "找到${nearestEvents.size}个最近事件, 距离${minDays}天: ${nearestEvents.joinToString(", ") { it.eventName }}")
                Pair(nearestEvents, minDays)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取最近事件失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * 生成动态通知文案
     * 两行排版结构：
     * 时间：农历六月十八（还有225天）
     * 事件：好吃巨巨巨、金币不够过一会吧
     */
    private fun generateDynamicNotificationText(): String {
        Log.d(TAG, "开始生成动态通知文案")
        val upcomingEvent = getNextUpcomingEvent()

        return if (upcomingEvent != null) {
            val (events, daysUntil) = upcomingEvent
            Log.d(TAG, "找到${events.size}个即将到来的事件, 距离${daysUntil}天")

            // 获取事件的具体日期用于显示（区分公历和农历，突出闰月）
            // 使用第一个事件的日期作为日期显示（同一天的事件日期相同）
            val firstEvent = events.first()
            val reminderManager = ReminderManager(this)
            val nextReminderDate = reminderManager.getNextReminderDate(firstEvent.eventDate)
            val dateText = if (nextReminderDate != null) {
                // 使用统一的DateParser解析事件日期
                val parsedDate = DateParser.parse(firstEvent.eventDate)
                when (parsedDate?.type) {
                    DateParser.DateType.LUNAR -> {
                        // 农历事件：使用DateParser格式化显示
                        DateParser.formatLunarForDisplay(parsedDate)
                    }
                    else -> {
                        // 公历/忽略年份事件：显示公历格式（如：公历12月18日）
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
                events.size <= 3 -> {
                    // 1-3个事件：显示所有事件名，用顿号分隔
                    events.joinToString("、") { it.eventName }
                }
                else -> {
                    // 4个及以上：显示前3个+省略号
                    events.take(3).joinToString("、") { it.eventName } + "..."
                }
            }

            // 生成两行排版的通知文案（标题已在createPersistentNotification中设置）
            val line1 = when {
                daysUntil == 0 -> "时间：今天（$dateText）"
                daysUntil == 1 -> "时间：明天（$dateText）"
                else -> "时间：$dateText（还有${daysUntil}天）"
            }
            val line2 = "事件：$eventNamesText"

            val notificationText = "$line1\n$line2"
            Log.d(TAG, "生成的通知文案: $notificationText")
            notificationText
        } else {
            // 没有事件时显示默认文案
            val defaultText = "时间：暂无事件\n事件：添加纪念日，让重要时刻不被遗忘"
            Log.d(TAG, "没有找到事件，使用默认文案: $defaultText")
            defaultText
        }
    }

    /**
     * 更新常驻通知内容
     * 只有在常驻通知开启时才执行更新
     * 包含防抖机制，1秒内不重复更新
     */
    private fun updatePersistentNotification() {
        try {
            // 防抖：1秒内不重复更新
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime < 1000) {
                Log.d(TAG, "防抖：跳过重复更新")
                return
            }
            lastUpdateTime = now

            // 检查常驻通知是否开启
            val dataManager = DataManager(this)
            if (!dataManager.isPersistentNotificationEnabled()) {
                Log.d(TAG, "常驻通知未开启，跳过更新")
                return
            }

            val notification = createPersistentNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "已更新常驻通知内容")
        } catch (e: Exception) {
            Log.e(TAG, "更新常驻通知失败: ${e.message}")
        }
    }

    /**
     * 启动提醒检查任务
     * 每15分钟检查一次是否有到期的提醒需要发送
     * 注意：常驻通知内容的更新由以下机制触发，不在此处处理：
     * - 用户增删改事件 → DataManager 调用 updateNotification()
     * - 系统时间变化（包括零点） → timeChangeReceiver 广播接收器
     */
    private fun startReminderCheckTask() {
        serviceJob?.cancel() // 取消之前的任务

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    // 检查并发送到期的提醒
                    checkAndSendReminders()

                    // 检查是否还有任何事件，如果没有则停止服务
                    if (!hasAnyEvents()) {
                        Log.d(TAG, "没有任何事件，停止提醒检查")
                        // 不停止整个服务，只是暂停提醒检查，因为用户可能开启了常驻通知
                        break
                    }

                    // 每15分钟检查一次
                    delay(REMINDER_CHECK_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "提醒检查任务异常: ${e.message}")
                e.printStackTrace()
            }
        }
        Log.d(TAG, "智能提醒检查任务已启动，每15分钟检查一次")
    }

    /**
     * 检查并发送到期的提醒
     * 修复：使用与ReminderManager一致的提醒时间
     * - 7天前：8:00
     * - 1天前：8:00
     * - 当天：0:00、8:00、12:00
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
            val currentHour = now.get(Calendar.HOUR_OF_DAY)

            // 定义提醒时间映射（与ReminderManager保持一致）
            val reminderHoursMap = mapOf(
                7 to listOf(8),        // 7天前：只在早上8点
                1 to listOf(8),        // 1天前：只在早上8点
                0 to listOf(0, 8, 12)  // 当天：凌晨00:00、上午08:00、中午12:00
            )

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

                        // 计算事件距离今天的天数
                        val eventDateOnly = Calendar.getInstance().apply {
                            time = eventCalendar.time
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val nowDateOnly = Calendar.getInstance().apply {
                            time = now.time
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val daysBetween = ((eventDateOnly.timeInMillis - nowDateOnly.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()

                        // 遍历所有可能的提醒时间点
                        val reminderDays = listOf(7, 1, 0) // 提前天数
                        reminderDays.forEach { daysBefore ->
                            if (daysBetween == daysBefore) {
                                // 当前日期符合提醒条件，检查具体时间
                                val reminderHours = reminderHoursMap[daysBefore] ?: listOf(8)

                                reminderHours.forEach { hour ->
                                    // 检查当前小时是否接近提醒时间（允许15分钟窗口期）
                                    val reminderTime = Calendar.getInstance().apply {
                                        time = now.time
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }

                                    val timeDiff = Math.abs(now.timeInMillis - reminderTime.timeInMillis)
                                    if (timeDiff <= 15 * 60 * 1000) { // 15分钟窗口期
                                        // 检查是否已经提醒过
                                        if (!reminderManager.hasReminderSentToday(event.id, daysBefore, hour)) {
                                            // 发送提醒通知
                                            sendReminderNotification(event, daysBefore, hour)
                                            // 标记提醒已发送
                                            reminderManager.markReminderSentToday(event.id, daysBefore, hour)
                                            Log.d(TAG, "发送提醒成功: ${event.eventName}, 提前${daysBefore}天, ${hour}:00")
                                        } else {
                                            Log.d(TAG, "事件已提醒过，跳过: ${event.eventName}, 提前${daysBefore}天, ${hour}:00")
                                        }
                                    }
                                }
                            }
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
            Log.e(TAG, "检查事件失败: ${e.message}")
            false
        }
    }

    /**
     * 发送提醒通知
     * 修复：支持不同时间点的提醒描述
     */
    private fun sendReminderNotification(event: Event, daysBefore: Int, hour: Int) {
        try {
            val reminderLabels = mapOf(
                7 to "还有7天",
                1 to "明天就是",
                0 to "就是今天"
            )

            val (title, content) = when {
                daysBefore == 7 -> "纪念日提醒" to "还有7天就是「${event.eventName}」了，记得准备哦！"
                daysBefore == 1 -> "纪念日提醒" to "明天就是「${event.eventName}」了，别忘记了！"
                daysBefore == 0 -> "纪念日到了！" to "今天是「${event.eventName}」，祝你开心！"
                else -> "纪念日提醒" to "还有${daysBefore}天就是「${event.eventName}」了！"
            }

            // 使用ReminderManager的通知渠道
            val notification = NotificationCompat.Builder(this, ReminderManager.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis()) // 设置通知时间戳
                .setShowWhen(true) // 显示时间，Android系统会自动处理相对时间格式
                .build()

            val notificationId = "${event.id}_${daysBefore}_${hour}".hashCode()
            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "常驻通知服务发送提醒: ${event.eventName}, 提前${daysBefore}天, ${hour}:00")

        } catch (e: Exception) {
            Log.e(TAG, "发送提醒通知失败: ${e.message}")
        }
    }

    /**
     * 提供公开的方法供外部触发常驻通知更新
     * 用于 DataManager 的 CRUD 操作后立即更新通知内容
     */
    fun triggerNotificationUpdate() {
        try {
            updatePersistentNotification()
            Log.d(TAG, "外部触发常驻通知更新")
        } catch (e: Exception) {
            Log.e(TAG, "外部触发常驻通知更新失败: ${e.message}")
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

        /**
         * 启动常驻通知服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "启动常驻通知服务")
        }

        /**
         * 停止常驻通知服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
            Log.d(TAG, "停止常驻通知服务")
        }

        /**
         * 触发更新常驻通知内容
         * 用于 DataManager CRUD 操作后立即更新通知
         * 只有在常驻通知开启时才会启动服务
         */
        fun updateNotification(context: Context) {
            try {
                // 先检查常驻通知是否开启，避免不必要的服务启动
                val dataManager = DataManager(context)
                if (!dataManager.isPersistentNotificationEnabled()) {
                    Log.d(TAG, "常驻通知未开启，跳过更新")
                    return
                }

                val intent = Intent(context, PersistentNotificationService::class.java).apply {
                    action = ACTION_UPDATE_NOTIFICATION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "触发常驻通知更新")
            } catch (e: Exception) {
                Log.e(TAG, "触发常驻通知更新失败: ${e.message}")
            }
        }
    }
}
