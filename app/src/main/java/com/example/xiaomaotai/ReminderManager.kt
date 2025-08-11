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

    init {
        createNotificationChannel()
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
            // 精确闹钟权限默认开启，直接设置提醒
            
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
                
                // 特殊处理当天事件：如果是当天且时间已过，立即触发通知
                if (daysBefore == 0 && isSameDay(reminderCalendar, now) && reminderCalendar.timeInMillis <= now.timeInMillis) {
                    // 设置为1分钟后提醒（立即提醒）
                    reminderCalendar.timeInMillis = now.timeInMillis + 1 * 60 * 1000
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
            val reminderDays = listOf(7, 1, 0) // 7天前、1天前、当天
            
            reminderDays.forEach { daysBefore ->
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = "com.example.xiaomaotai.REMINDER"
                }
                
                // 使用与设置提醒时相同的ID生成方式
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${eventId}_$daysBefore".hashCode(),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d("ReminderManager", "已取消${daysBefore}天前提醒: $eventId")
                }
            }
            
            Log.d("ReminderManager", "已取消事件所有提醒: $eventId")
        } catch (e: Exception) {
            Log.e("ReminderManager", "取消提醒失败: ${e.message}")
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

}
