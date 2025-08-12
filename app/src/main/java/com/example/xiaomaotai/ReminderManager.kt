package com.example.xiaomaotai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

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
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "纪念日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "纪念日提醒通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 设置事件提醒 - 支持7天前、1天前、当天的多重提醒
     * 支持未登录用户，支持APP被杀死后提醒
     */
    fun scheduleReminder(event: Event) {
        try {
            // 检查权限
            if (!canScheduleAlarms()) {
                Log.w("ReminderManager", "没有精确闹钟权限，无法设置提醒: ${event.eventName}")
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
                
                // 检查今天是否已经发送过这个提醒
                if (daysBefore == 0 && hasReminderSentToday(event.id, daysBefore)) {
                    Log.d("ReminderManager", "当天提醒已发送过，跳过: ${event.eventName}")
                    return@forEachIndexed
                }
                
                // 特殊处理当天事件：如果是当天且时间已过，立即触发通知
                if (daysBefore == 0 && isSameDay(reminderCalendar, now)) {
                    if (reminderCalendar.timeInMillis <= now.timeInMillis) {
                        // 设置为30秒后提醒（立即提醒）
                        reminderCalendar.timeInMillis = now.timeInMillis + 30 * 1000
                        Log.d("ReminderManager", "当天事件时间已过，设置立即提醒: ${event.eventName}")
                    }
                }
                
                // 设置未来的提醒或当天的立即提醒
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
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                        Log.d("ReminderManager", "已设置${reminderLabels[index]}提醒: ${event.eventName} at ${reminderCalendar.time}")
                    } catch (e: SecurityException) {
                        Log.e("ReminderManager", "设置精确闹钟权限被拒绝: ${e.message}")
                    }
                }
            }
            
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
    private fun getNextReminderDate(eventDate: String): Date? {
        return try {
            when {
                // 农历事件
                eventDate.startsWith("lunar:") -> {
                    val lunarDatePart = eventDate.removePrefix("lunar:")
                    val parts = lunarDatePart.split("-")
                    if (parts.size >= 3) {
                        val lunarMonth = parts[1].toInt()
                        val lunarDay = parts[2].toInt()
                        
                        // 使用LunarCalendarHelper转换农历到公历
                        val today = Calendar.getInstance()
                        val currentYear = today.get(Calendar.YEAR)
                        
                        // 先尝试当年的农历日期
                        var targetDate: Date = try {
                            // LunarCalendarHelper.lunarToSolar返回LocalDate，需要转换为Date
                            val localDate = LunarCalendarHelper.lunarToSolar(currentYear, lunarMonth, lunarDay)
                            java.sql.Date.valueOf(localDate)
                        } catch (e: Exception) {
                            // 如果转换失败，使用近似计算
                            val cal = Calendar.getInstance()
                            cal.set(currentYear, lunarMonth - 1, lunarDay.coerceIn(1, 28))
                            cal.time
                        }
                        
                        // 如果今年的农历日期已过，计算明年的
                        val targetCal = Calendar.getInstance()
                        targetCal.time = targetDate
                        if (targetCal.before(today) || isSameDay(targetCal, today)) {
                            targetDate = try {
                                val localDate = LunarCalendarHelper.lunarToSolar(currentYear + 1, lunarMonth, lunarDay)
                                java.sql.Date.valueOf(localDate)
                            } catch (e: Exception) {
                                val cal = Calendar.getInstance()
                                cal.set(currentYear + 1, lunarMonth - 1, lunarDay.coerceIn(1, 28))
                                cal.time
                            }
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
                        
                        // 如果今年的日期已经过了，设置为明年
                        val today = Calendar.getInstance()
                        if (eventCal.before(today) || isSameDay(eventCal, today)) {
                            eventCal.add(Calendar.YEAR, 1)
                        }
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
                    
                    // 如果今年的日期已经过了，设置为明年
                    val today = Calendar.getInstance()
                    if (eventCal.before(today) || eventCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
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
     * 检查是否有权限设置提醒
     */
    fun canScheduleAlarms(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Android 12以下版本默认有权限
        }
    }
    
    /**
     * 检查今天是否已经为某个事件的特定提醒类型发送过通知
     */
    private fun hasReminderSentToday(eventId: String, daysRemaining: Int): Boolean {
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
    private fun cleanupOldReminderHistory() {
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
     * 测试通知功能 - 立即发送一个测试通知
     */
    fun sendTestNotification() {
        try {
            // 确保通知渠道存在
            createNotificationChannel()
            
            val testIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.xiaomaotai.REMINDER"
                putExtra("event_id", "test_notification")
                putExtra("event_name", "测试通知")
                putExtra("event_date", "test")
                putExtra("days_remaining", 0)
                putExtra("reminder_label", "测试")
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "test_notification".hashCode(),
                testIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            // 设置5秒后触发测试通知
            val testTime = System.currentTimeMillis() + 5000
            
            if (canScheduleAlarms()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        testTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        testTime,
                        pendingIntent
                    )
                }
                Log.d("ReminderManager", "测试通知已设置，5秒后触发")
            } else {
                // 对于没有精确闹钟权限的情况，直接发送通知
                Log.w("ReminderManager", "没有精确闹钟权限，直接发送测试通知")
                sendDirectTestNotification()
            }
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "发送测试通知失败: ${e.message}")
        }
    }
    
    /**
     * 直接发送测试通知 - 用于没有精确闹钟权限的情况
     */
    private fun sendDirectTestNotification() {
        try {
            // 确保通知渠道存在
            createNotificationChannel()
            
            // 直接通过ReminderReceiver发送通知
            val receiver = ReminderReceiver()
            val testIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.xiaomaotai.REMINDER"
                putExtra("event_id", "test_notification_direct")
                putExtra("event_name", "测试通知")
                putExtra("event_date", "test")
                putExtra("days_remaining", 0)
                putExtra("reminder_label", "测试")
            }
            
            // 直接调用onReceive方法
            receiver.onReceive(context, testIntent)
            Log.d("ReminderManager", "直接发送测试通知成功")
            
        } catch (e: Exception) {
            Log.e("ReminderManager", "直接发送测试通知失败: ${e.message}")
        }
    }

}
