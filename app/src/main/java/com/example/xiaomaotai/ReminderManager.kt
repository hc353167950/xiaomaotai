package com.example.xiaomaotai

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 提醒管理器
 * 负责设置和管理事件提醒
 */
class ReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "memory_day_reminders"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val sharedPreferences = context.getSharedPreferences("reminder_history", Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
        cleanupOldReminderHistory()
        // 启动WorkManager周期性检查任务
        initializeWorkManagerTasks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "纪念日提醒",
                NotificationManager.IMPORTANCE_HIGH // 提高重要性确保通知显示
            ).apply {
                description = "纪念日提醒通知"
                enableVibration(true) // 启用震动
                enableLights(true) // 启用指示灯
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null) // 设置默认声音
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC // 锁屏显示
                setShowBadge(true) // 显示角标
                
                // 优化2：提升通知系统级优先级
                setBypassDnd(true) // 绕过勿扰模式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true) // 允许气泡通知
                }
            }
            notificationManager.createNotificationChannel(channel)
            Log.d("ReminderManager", "通知渠道已创建，重要性级别: HIGH，已启用系统级优先级（绕过勿扰模式）")
        }
    }
    
    /**
     * 检查是否可以设置精确闹钟
     * 针对vivo等国产手机优化权限检测
     */
    fun canScheduleAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = alarmManager.canScheduleExactAlarms()
            if (!hasPermission) {
                Log.w("ReminderManager", "精确闹钟权限未授权，设备制造商: ${Build.MANUFACTURER}")
                // 对于vivo设备，提供额外的日志信息
                if (Build.MANUFACTURER.lowercase().contains("vivo") || 
                    Build.MANUFACTURER.lowercase().contains("iqoo")) {
                    Log.w("ReminderManager", "检测到vivo设备，请在系统设置中手动授权精确闹钟权限")
                }
            }
            hasPermission
        } else {
            true // Android 12以下默认有权限
        }
    }
    
    /**
     * 设置事件提醒 - 支持7天前、1天前、当天的多重提醒
     * 支持未登录用户，支持APP被杀死后提醒
     * 增强版：使用多重保障机制（AlarmManager + WorkManager + 前台服务）
     */
    fun scheduleReminder(event: Event) {
        try {
            // 检查权限
            if (!canScheduleAlarms()) {
                Log.w("ReminderManager", "没有精确闹钟权限，无法设置提醒: ${event.eventName}")
                // 对于vivo设备，提供更详细的错误信息
                if (Build.MANUFACTURER.lowercase().contains("vivo") || 
                    Build.MANUFACTURER.lowercase().contains("iqoo")) {
                    Log.w("ReminderManager", "vivo设备权限提示：请前往 设置 → 应用与权限 → 权限管理 → 闹钟 中添加小茅台")
                }
                return
            }
            
            // 确保通知渠道存在
            createNotificationChannel()
            
            // 解析事件日期，获取下次提醒时间
            val nextReminderDate = getNextReminderDate(event.eventDate)
            if (nextReminderDate == null) {
                Log.e("ReminderManager", "无法解析事件日期: ${event.eventDate}")
                return
            }
            
            val eventCalendar = Calendar.getInstance()
            eventCalendar.time = nextReminderDate
            eventCalendar.set(Calendar.HOUR_OF_DAY, 9)
            eventCalendar.set(Calendar.MINUTE, 0)
            eventCalendar.set(Calendar.SECOND, 0)
            eventCalendar.set(Calendar.MILLISECOND, 0)
            
            val now = Calendar.getInstance()
            
            // 设置多个提醒：7天前、1天前、当天
            val reminderDays = listOf(7, 1, 0) // 提前天数
            val reminderLabels = listOf("还有7天", "明天就是", "就是今天")
            
            reminderDays.forEachIndexed { index, daysBefore ->
                val reminderCalendar = eventCalendar.clone() as Calendar
                reminderCalendar.add(Calendar.DAY_OF_YEAR, -daysBefore)
                
                // 检查今天是否已经发送过这个提醒（防止所有类型的重复提醒）
                if (hasReminderSentToday(event.id, daysBefore)) {
                    Log.d("ReminderManager", "${reminderLabels[index]}提醒已发送过，跳过: ${event.eventName}")
                    return@forEachIndexed
                }
                
                // 精确的立即提醒逻辑：只对特定情况下的提醒进行立即处理
                val isReminderTimePassed = reminderCalendar.timeInMillis <= now.timeInMillis
                
                // 修复天数计算精度问题：使用日期比较而不是时间比较
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
                val daysBetweenEventAndNow = ((eventDateOnly.timeInMillis - nowDateOnly.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                
                // 调试日志：检查天数计算
                Log.d("ReminderManager", "🔍 天数计算调试: ${event.eventName}, eventCalendar: ${eventCalendar.time} (${eventCalendar.timeInMillis}), now: ${now.time} (${now.timeInMillis}), daysBetweenEventAndNow: $daysBetweenEventAndNow, daysBefore: $daysBefore")
                Log.d("ReminderManager", "🔍 日期比较: 事件日期: ${eventDateOnly.time}, 今天日期: ${nowDateOnly.time}")
                
                // 只对符合条件的情况进行立即提醒：
                // 1. 当天事件（daysBefore=0 && daysBetweenEventAndNow=0）
                // 2. 明天事件今天创建（daysBefore=1 && daysBetweenEventAndNow=1）
                // 3. 7天后事件今天创建（daysBefore=7 && daysBetweenEventAndNow=7）
                val shouldImmediateRemind = isReminderTimePassed && 
                    ((daysBefore == 0 && daysBetweenEventAndNow == 0) ||  // 当天事件
                     (daysBefore == 1 && daysBetweenEventAndNow == 1) ||  // 明天事件
                     (daysBefore == 7 && daysBetweenEventAndNow == 7))    // 7天后事件
                
                Log.d("ReminderManager", "检查提醒时间: ${event.eventName}, 提醒类型: ${reminderLabels[index]} (daysBefore=$daysBefore), 提醒时间: ${reminderCalendar.time}, 当前时间: ${now.time}, 时间已过: $isReminderTimePassed, 事件距离天数: $daysBetweenEventAndNow, 需要立即提醒: $shouldImmediateRemind")
                
                if (shouldImmediateRemind) {
                    // 符合条件的提醒时间已过，设置30秒后立即提醒
                    reminderCalendar.timeInMillis = now.timeInMillis + 30 * 1000
                    Log.d("ReminderManager", "⚙️ 精确立即提醒: ${event.eventName} (${reminderLabels[index]}), 符合条件且原时间已过，设置30秒后提醒，新提醒时间: ${reminderCalendar.time}")
                }
                
                // 设置未来的提醒或当天的立即提醒
                Log.d("ReminderManager", "检查是否需要设置提醒: ${event.eventName}, 提醒时间: ${reminderCalendar.time} (${reminderCalendar.timeInMillis}), 当前时间: ${now.time} (${now.timeInMillis}), 时间差: ${(reminderCalendar.timeInMillis - now.timeInMillis) / 1000}秒")
                
                if (reminderCalendar.timeInMillis > now.timeInMillis) {
                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        action = "com.example.xiaomaotai.REMINDER"
                        putExtra("event_id", event.id)
                        putExtra("event_name", event.eventName)
                        putExtra("event_date", event.eventDate)
                        putExtra("days_remaining", daysBefore)
                        putExtra("reminder_label", reminderLabels[index])
                    }
                    
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        "${event.id}_$daysBefore".hashCode(), // 每个提醒使用不同的ID
                        intent,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                    
                    // 设置精确闹钟 - 确保APP被杀死后也能提醒
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            // Android 6.0+ 使用setExactAndAllowWhileIdle确保在Doze模式下也能触发
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                reminderCalendar.timeInMillis,
                                pendingIntent
                            )
                        } else {
                            // Android 6.0以下使用setExact
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                reminderCalendar.timeInMillis,
                                pendingIntent
                            )
                        }
                        Log.d("ReminderManager", "✅ 已成功设置${reminderLabels[index]}提醒: ${event.eventName} at ${reminderCalendar.time}, PendingIntent ID: ${"${event.id}_$daysBefore".hashCode()}")
                        
                        // 优化1：增加多重备份AlarmManager（1-3分钟后的备份提醒）
                        setupBackupAlarms(event, reminderCalendar, daysBefore, reminderLabels[index])
                        
                    } catch (e: SecurityException) {
                        Log.e("ReminderManager", "❌ 设置精确闹钟权限被拒绝: ${e.message}")
                    } catch (e: Exception) {
                        Log.e("ReminderManager", "❌ 设置闹钟失败: ${e.message}", e)
                    }
                }
            }
            
            // 增强功能：检查是否需要启动前台服务（24小时内的提醒）
            checkAndStartForegroundService(event, eventCalendar)
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "设置提醒失败: ${e.message}")
        }
    }
    
    /**
     * 取消事件提醒 - 取消所有相关的多重提醒（7天前、1天前、当天）
     */
    fun cancelReminder(eventId: String) {
        try {
            val reminderDays = listOf(7, 1, 0)
            reminderDays.forEach { daysBefore ->
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = "com.example.xiaomaotai.REMINDER"
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${eventId}_$daysBefore".hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
                Log.d("ReminderManager", "已取消事件提醒: $eventId (提前${daysBefore}天)")
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "取消提醒失败: $eventId, ${e.message}")
        }
    }
    
    /**
     * 安全设置提醒 - 先取消现有提醒再设置新的，避免重复
     */
    fun safeScheduleReminder(event: Event) {
        try {
            // 先取消现有的提醒
            cancelReminder(event.id)
            // 再设置新的提醒
            scheduleReminder(event)
        } catch (e: Exception) {
            Log.e("ReminderManager", "安全设置提醒失败: ${event.eventName}, ${e.message}")
        }
    }
    
    /**
     * 更新事件提醒
     */
    fun updateReminder(event: Event) {
        cancelReminder(event.id)
        scheduleReminder(event)
    }
    
    /**
     * 获取下次提醒日期
     * 支持公历、农历和忽略年份格式
     */
    fun getNextReminderDate(eventDate: String): Date? {
        return try {
            when {
                // 农历事件
                eventDate.startsWith("lunar:") -> {
                    val lunarDatePart = eventDate.removePrefix("lunar:")
                    val parts = lunarDatePart.split("-")
                    if (parts.size >= 3) {
                        val monthPart = parts[1]
                        val lunarDay = parts[2].toInt()
                        
                        // 检查是否为闰月（格式：L06 表示闰六月）
                        val (lunarMonth, isLeap) = if (monthPart.startsWith("L")) {
                            val actualMonth = monthPart.substring(1).toInt()
                            Pair(actualMonth, true)
                        } else {
                            Pair(monthPart.toInt(), false)
                        }
                        
                        // 使用统一的农历倒计时计算逻辑（与EventItem保持一致）
                        val today = Calendar.getInstance()
                        val currentDate = LocalDate.now()
                        val currentYear = today.get(Calendar.YEAR)
                        
                        Log.d("ReminderManager", "🔍 农历提醒计算: ${lunarMonth}月${lunarDay}日(闰月:$isLeap), 当前日期: $currentDate")
                        
                        // 使用LunarCalendarHelper的统一农历倒计时计算
                        val daysUntilEvent = LunarCalendarHelper.calculateLunarCountdown(
                            currentYear, lunarMonth, lunarDay, isLeap, currentDate
                        )
                        
                        Log.d("ReminderManager", "🔍 农历倒计时结果: ${daysUntilEvent}天")
                        
                        // 根据倒计时天数计算目标日期
                        val targetDate: Date = if (daysUntilEvent == 0L) {
                            // 今天就是事件日期
                            Log.d("ReminderManager", "✅ 农历事件就是今天")
                            today.time
                        } else {
                            // 计算未来的事件日期
                            val targetCal = Calendar.getInstance()
                            targetCal.add(Calendar.DAY_OF_YEAR, daysUntilEvent.toInt())
                            Log.d("ReminderManager", "✅ 农历事件在${daysUntilEvent}天后: ${targetCal.time}")
                            targetCal.time
                        }
                        
                        targetDate
                    } else null
                }
                
                // 忽略年份格式 (MM-dd)
                eventDate.matches(Regex("\\d{2}-\\d{2}")) -> {
                    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                    val parsedDate = sdf.parse(eventDate)
                    parsedDate?.let {
                        val cal = Calendar.getInstance()
                        val currentYear = cal.get(Calendar.YEAR)
                        val eventCal = Calendar.getInstance()
                        eventCal.time = it
                        eventCal.set(Calendar.YEAR, currentYear)
                        
                        val today = Calendar.getInstance()
                        
                        // 比较日期而不是具体时间，避免当天事件被错误判断
                        val eventDayOfYear = eventCal.get(Calendar.DAY_OF_YEAR)
                        val todayDayOfYear = today.get(Calendar.DAY_OF_YEAR)
                        val eventYear = eventCal.get(Calendar.YEAR)
                        val todayYear = today.get(Calendar.YEAR)
                        
                        val isEventDatePassed = (eventYear < todayYear) || 
                                               (eventYear == todayYear && eventDayOfYear < todayDayOfYear)
                        
                        Log.d("ReminderManager", "📅 忽略年份解析 - 当前年: $currentYear, 事件日期: ${eventCal.time}, 今天: ${today.time}")
                        Log.d("ReminderManager", "📅 日期比较 - 事件天数: $eventDayOfYear, 今天天数: $todayDayOfYear, 日期已过: $isEventDatePassed")
                        
                        // 只有日期真正过去了才设置为明年（不包括当天）
                        if (isEventDatePassed) {
                            eventCal.add(Calendar.YEAR, 1)
                            Log.d("ReminderManager", "📅 日期已过，设置为明年: ${eventCal.time}")
                        }
                        
                        Log.d("ReminderManager", "📅 忽略年份最终结果: ${eventCal.time}")
                        eventCal.time
                    }
                }
                
                // 公历格式 (yyyy-MM-dd)
                eventDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.parse(eventDate)
                }
                
                else -> {
                    Log.w("ReminderManager", "不支持的日期格式: $eventDate")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "解析日期失败: $eventDate, ${e.message}")
            null
        }
    }
    
    /**
     * 判断两个日历是否是同一天
     */
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 解析事件日期（保留原方法用于兼容）
     * 支持 yyyy-MM-dd 和 MM-dd 格式
     */
    private fun parseEventDate(eventDate: String): Date? {
        return try {
            if (eventDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.parse(eventDate)
            } else if (eventDate.matches(Regex("\\d{2}-\\d{2}"))) {
                val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                val parsedDate = sdf.parse(eventDate)
                parsedDate?.let {
                    val cal = Calendar.getInstance()
                    val currentYear = cal.get(Calendar.YEAR)
                    val eventCal = Calendar.getInstance()
                    eventCal.time = it
                    eventCal.set(Calendar.YEAR, currentYear)
                    
                    // 比较日期而不是具体时间，避免当天事件被错误判断
                    val today = Calendar.getInstance()
                    val eventDayOfYear = eventCal.get(Calendar.DAY_OF_YEAR)
                    val todayDayOfYear = today.get(Calendar.DAY_OF_YEAR)
                    val eventYear = eventCal.get(Calendar.YEAR)
                    val todayYear = today.get(Calendar.YEAR)
                    
                    val isEventDatePassed = (eventYear < todayYear) || 
                                           (eventYear == todayYear && eventDayOfYear < todayDayOfYear)
                    
                    // 只有日期真正过去了才设置为明年（不包括当天）
                    if (isEventDatePassed) {
                        eventCal.add(Calendar.YEAR, 1)
                    }
                    eventCal.time
                }
            } else {
                Log.w("ReminderManager", "不支持的日期格式，无法设置提醒: $eventDate")
                null
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "解析日期失败: $eventDate, ${e.message}")
            null
        }
    }
    

    
    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Android 6.0以下版本没有电池优化
        }
    }
    
    /**
     * 获取请求电池优化白名单的Intent
     */
    fun getBatteryOptimizationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * 检查通知系统的完整状态
     */
    fun checkNotificationSystemStatus(): NotificationSystemStatus {
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13以下默认有通知权限
        }
        
        return NotificationSystemStatus(
            hasNotificationPermission = hasNotificationPermission,
            canScheduleExactAlarms = canScheduleAlarms(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()
        )
    }
    
    /**
     * 通知系统状态数据类
     */
    data class NotificationSystemStatus(
        val hasNotificationPermission: Boolean,
        val canScheduleExactAlarms: Boolean,
        val isIgnoringBatteryOptimizations: Boolean
    ) {
        val isFullyConfigured: Boolean
            get() = hasNotificationPermission && canScheduleExactAlarms && isIgnoringBatteryOptimizations
    }
    
    /**
     * 检查今天是否已经为某个事件的特定提醒类型发送过通知
     */
    fun hasReminderSentToday(eventId: String, daysRemaining: Int): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val reminderKey = "${eventId}_${daysRemaining}_$today"
        return sharedPreferences.getBoolean(reminderKey, false)
    }
    
    /**
     * 记录今天已经为某个事件的特定提醒类型发送过通知
     */
    fun markReminderSentToday(eventId: String, daysRemaining: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val reminderKey = "${eventId}_${daysRemaining}_$today"
        sharedPreferences.edit().putBoolean(reminderKey, true).apply()
        Log.d("ReminderManager", "标记提醒已发送: $reminderKey")
    }
    
    /**
     * 清理过期的提醒历史记录（保留最近7天的记录）
     */
    fun cleanupOldReminderHistory() {
        try {
            val sevenDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }
            val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(sevenDaysAgo.time)
            
            val editor = sharedPreferences.edit()
            val allKeys = sharedPreferences.all.keys
            var removedCount = 0
            
            allKeys.forEach { key ->
                if (key.contains("_") && key.split("_").size >= 3) {
                    val datePart = key.split("_").last()
                    if (datePart < cutoffDate) {
                        editor.remove(key)
                        removedCount++
                    }
                }
            }
            
            if (removedCount > 0) {
                editor.apply()
                Log.d("ReminderManager", "清理了${removedCount}条过期提醒历史记录")
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "清理提醒历史记录失败: ${e.message}")
        }
    }
    
    /**
     * 发送立即测试通知 - 用于调试通知系统
     */
    fun sendImmediateTestNotification() {
        try {
            // 确保通知渠道存在
            createNotificationChannel()
            
            // 直接发送通知，不通过AlarmManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("测试通知")
                .setContentText("如果你看到这条通知，说明通知系统工作正常")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()
            
            val notificationId = "immediate_test".hashCode()
            notificationManager.notify(notificationId, notification)
            
            Log.d("ReminderManager", "立即测试通知已发送，ID: $notificationId")
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "发送立即测试通知失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 初始化WorkManager周期性任务
     * 每30分钟检查一次提醒状态，确保不会遗漏
     */
    private fun initializeWorkManagerTasks() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // 不需要网络
                .setRequiresBatteryNotLow(false) // 低电量时也要工作
                .setRequiresCharging(false) // 不需要充电
                .setRequiresDeviceIdle(false) // 设备使用时也要工作
                .build()
            
            val reminderWorkRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
                30, TimeUnit.MINUTES // 每30分钟执行一次
            )
                .setConstraints(constraints)
                .addTag("reminder_check")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保持现有任务
                reminderWorkRequest
            )
            
            Log.d("ReminderManager", "WorkManager周期性任务已启动，每30分钟检查一次")
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "启动WorkManager任务失败: ${e.message}")
        }
    }
    

    
    /**
     * 停止所有增强功能
     * 用于清理资源或用户禁用功能时调用
     */
    fun stopEnhancedFeatures() {
        try {
            // 停止WorkManager任务
            WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.WORK_NAME)
            
            // 停止前台服务
            ReminderForegroundService.stopService(context)
            
            Log.d("ReminderManager", "已停止所有增强功能")
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "停止增强功能失败: ${e.message}")
        }
    }

    /**
     * 优化1：设置多重备份AlarmManager（1-3分钟后的备份提醒）
     * 在原有功能基础上增加备份机制，确保提醒可靠性
     */
    private fun setupBackupAlarms(event: Event, originalReminderTime: Calendar, daysBefore: Int, reminderLabel: String) {
        try {
            // 设置1分钟、2分钟、3分钟后的备份提醒
            val backupDelays = listOf(1, 2, 3) // 分钟
            
            backupDelays.forEach { delayMinutes ->
                val backupTime = originalReminderTime.clone() as Calendar
                backupTime.add(Calendar.MINUTE, delayMinutes)
                
                val backupIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = "com.example.xiaomaotai.BACKUP_REMINDER"
                    putExtra("event_id", event.id)
                    putExtra("event_name", event.eventName)
                    putExtra("event_date", event.eventDate)
                    putExtra("days_remaining", daysBefore)
                    putExtra("reminder_label", reminderLabel)
                    putExtra("backup_delay", delayMinutes)
                    putExtra("original_time", originalReminderTime.timeInMillis)
                }
                
                val backupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${event.id}_${daysBefore}_backup_${delayMinutes}".hashCode(),
                    backupIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            backupTime.timeInMillis,
                            backupPendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            backupTime.timeInMillis,
                            backupPendingIntent
                        )
                    }
                    Log.d("ReminderManager", "✅ 备份提醒已设置: ${event.eventName} (${delayMinutes}分钟后) at ${backupTime.time}")
                } catch (e: Exception) {
                    Log.e("ReminderManager", "❌ 设置备份提醒失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "设置备份提醒失败: ${e.message}")
        }
    }

    
    /**
     * 优化3：检查并启动前台服务（24小时内提醒时启动保活）
     * 当事件在24小时内有提醒时，启动前台服务确保APP存活
     */
    private fun checkAndStartForegroundService(event: Event, eventCalendar: Calendar) {
        try {
            val now = Calendar.getInstance()
            val timeDiffHours = (eventCalendar.timeInMillis - now.timeInMillis) / (1000 * 60 * 60)
            
            Log.d("ReminderManager", "检查前台服务需求: ${event.eventName}, 距离事件还有 ${timeDiffHours} 小时")
            
            // 如果事件在24小时内，启动前台服务保活
            if (timeDiffHours in 0..24) {
                Log.d("ReminderManager", "✅ 事件 ${event.eventName} 在24小时内，启动前台服务保活")
                startForegroundServiceForReminder(event, timeDiffHours)
            } else if (timeDiffHours < 0) {
                // 事件已过期，检查是否有立即提醒需要前台服务
                Log.d("ReminderManager", "⚠️ 事件 ${event.eventName} 已过期，检查立即提醒需求")
                // 对于当天的立即提醒，也启动前台服务确保可靠性
                startForegroundServiceForReminder(event, 0)
            } else {
                Log.d("ReminderManager", "ℹ️ 事件 ${event.eventName} 超过24小时，暂不启动前台服务")
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "检查前台服务失败: ${e.message}")
        }
    }
    
    /**
     * 启动前台服务用于关键提醒保活
     * 优化3的核心实现：智能启动前台服务
     */
    private fun startForegroundServiceForReminder(event: Event, hoursUntilEvent: Long) {
        try {
            val serviceIntent = Intent(context, ReminderForegroundService::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_name", event.eventName)
                putExtra("event_date", event.eventDate)
                putExtra("hours_until_event", hoursUntilEvent)
                putExtra("service_duration_hours", if (hoursUntilEvent <= 1) 2 else 24) // 1小时内事件保活2小时，其他保活24小时
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d("ReminderManager", "✅ 已启动前台服务保活: ${event.eventName}, 保活时长: ${if (hoursUntilEvent <= 1) 2 else 24}小时")
            } else {
                context.startService(serviceIntent)
                Log.d("ReminderManager", "✅ 已启动后台服务保活: ${event.eventName} (Android < 8.0)")
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "启动前台服务失败: ${e.message}")
        }
    }
    
    /**
     * 检查前台服务是否正在运行
     */
    fun isForegroundServiceRunning(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            services.any { it.service.className == ReminderForegroundService::class.java.name }
        } catch (e: Exception) {
            Log.e("ReminderManager", "检查前台服务状态失败: ${e.message}")
            false
        }
    }

}
