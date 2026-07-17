package com.example.xiaomaotai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 提醒调度深模块：对外只有 schedule / cancel / update 三个动词。
 *
 * 内部职责：
 * - 触发时刻来自 [ReminderSchedule]（时刻表唯一来源）；
 * - 已发送去重台账（SharedPreferences "reminder_history"）；
 * - AlarmManager 精确/非精确闹钟的适配（权限缺失时自动降级，调用方无感知）；
 * - WorkManager 周期补漏任务的注册（[bootstrapPeriodicWork]，App 启动时调用一次）。
 *
 * 构造函数无副作用：渠道创建、台账清理、WorkManager 注册全部收敛到
 * [bootstrapPeriodicWork]，由 Application.onCreate 统一触发。
 */
class ReminderManager(private val context: Context) {

    companion object {
        /** 渠道已迁移至 [ReminderNotifier]，别名保留给既有调用方 */
        const val CHANNEL_ID = ReminderNotifier.CHANNEL_ID
        private const val TAG = "ReminderManager"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val sentLedger = context.getSharedPreferences("reminder_history", Context.MODE_PRIVATE)

    // ---------- 对外接口 ----------

    /**
     * 设置事件的全部提醒（7天前/1天前/当天，时刻见 [ReminderSchedule]）。
     * 无精确闹钟权限时自动降级为非精确闹钟，APP 被杀死后仍能触发。
     */
    fun scheduleReminder(event: Event) {
        try {
            val exact = canScheduleAlarms()
            if (!exact) {
                Log.w(TAG, "没有精确闹钟权限，降级为非精确闹钟: ${event.eventName} (${Build.MANUFACTURER})")
            }

            val occurrences = ReminderSchedule.occurrencesFor(event.eventDate, LocalDateTime.now())
            if (occurrences.isEmpty()) {
                Log.w(TAG, "无法解析事件日期或无待触发提醒: ${event.eventDate}")
                return
            }

            occurrences.forEach { occ ->
                // 今天已发过的档位不再重复设置
                if (hasReminderSentToday(event.id, occ.daysBefore, occ.hour)) {
                    Log.d(TAG, "${occ.label}提醒(${occ.hour}:00)今天已发送，跳过: ${event.eventName}")
                    return@forEach
                }

                val triggerAt = occ.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setAlarm(triggerAt, primaryPendingIntent(event, occ), exact)
                Log.d(TAG, "已设置${occ.label}提醒(${occ.hour}:00): ${event.eventName} at ${occ.triggerAt}")

                // 精确模式下追加 1-3 分钟后的备份闹钟，防厂商 ROM 吞掉首发
                if (exact) setupBackupAlarms(event, occ, triggerAt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置提醒失败: ${e.message}")
        }
    }

    /**
     * 取消事件的全部提醒，包括备份闹钟与升级前旧格式的 PendingIntent。
     */
    fun cancelReminder(eventId: String) {
        try {
            ReminderSchedule.OFFSETS.forEach { daysBefore ->
                // 当前格式（含所有历史上可能用过的时刻组合）与备份闹钟
                listOf(0, 8, 12).forEach { hour ->
                    cancelAlarm("${eventId}_${daysBefore}_${hour}")
                    listOf(1, 2, 3).forEach { delay ->
                        cancelAlarm("${eventId}_${daysBefore}_${hour}_backup_${delay}")
                    }
                }
                // 旧版本不带 hour 的格式
                cancelAlarm("${eventId}_${daysBefore}")
            }
            Log.d(TAG, "已取消事件全部提醒: $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "取消提醒失败: $eventId, ${e.message}")
        }
    }

    /** 先取消再设置，避免重复（数据加载/开机恢复用） */
    fun safeScheduleReminder(event: Event) {
        try {
            cancelReminder(event.id)
            scheduleReminder(event)
        } catch (e: Exception) {
            Log.e(TAG, "安全设置提醒失败: ${event.eventName}, ${e.message}")
        }
    }

    /** 更新事件提醒 */
    fun updateReminder(event: Event) {
        cancelReminder(event.id)
        scheduleReminder(event)
    }

    /** 下一次纪念日对应的公历日期（委托 [Countdown]），解析失败返回 null */
    fun getNextReminderDate(eventDate: String): Date? =
        Countdown.of(eventDate)?.nextDateAsDate()

    // ---------- 权限探测 ----------

    /** 是否允许精确闹钟（Android 12+ 需要用户授权，vivo/iQOO 需在系统设置手动开启） */
    fun canScheduleAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** 是否已加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 请求加入电池优化白名单的系统设置 Intent */
    fun getBatteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    // ---------- 已发送台账 ----------

    /** 今天是否已为该事件的该档位发送过提醒 */
    fun hasReminderSentToday(eventId: String, daysRemaining: Int, hour: Int): Boolean {
        return sentLedger.getBoolean(ledgerKey(eventId, daysRemaining, hour), false)
    }

    /** 记录今天已为该事件的该档位发送过提醒 */
    fun markReminderSentToday(eventId: String, daysRemaining: Int, hour: Int) {
        sentLedger.edit().putBoolean(ledgerKey(eventId, daysRemaining, hour), true).commit()
    }

    private fun ledgerKey(eventId: String, daysRemaining: Int, hour: Int): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "${eventId}_${daysRemaining}_${hour}_$today"
    }

    // ---------- 启动引导（Application.onCreate 调用一次） ----------

    /**
     * 注册 WorkManager 周期补漏任务并清理过期台账。
     * 此前藏在构造函数里，每次 new 都会执行；现在显式化为启动步骤。
     */
    fun bootstrapPeriodicWork() {
        ReminderNotifier.ensureChannel(context)
        cleanupOldLedger()
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag("reminder_check")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "WorkManager周期补漏任务已注册（每30分钟）")
        } catch (e: Exception) {
            Log.e(TAG, "注册WorkManager任务失败: ${e.message}")
        }
    }

    /** 清理7天前的台账记录，防止 SharedPreferences 无限膨胀 */
    private fun cleanupOldLedger() {
        try {
            val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
            val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(sevenDaysAgo.time)

            val editor = sentLedger.edit()
            var removed = 0
            sentLedger.all.keys.forEach { key ->
                val datePart = key.substringAfterLast('_')
                if (datePart.length == 10 && datePart < cutoffDate) {
                    editor.remove(key)
                    removed++
                }
            }
            if (removed > 0) {
                editor.commit()
                Log.d(TAG, "清理了${removed}条过期提醒台账")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理提醒台账失败: ${e.message}")
        }
    }

    /** 发送立即测试通知（设置页调试入口） */
    fun sendImmediateTestNotification() {
        ReminderNotifier.sendTest(context)
    }

    // ---------- AlarmManager 适配 ----------

    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent, exact: Boolean) {
        try {
            if (exact) {
                // Doze 模式下也要触发
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // 降级：非精确但 Doze 下仍工作
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "设置闹钟权限被拒绝: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "设置闹钟失败: ${e.message}", e)
        }
    }

    private fun cancelAlarm(requestKey: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.xiaomaotai.REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun primaryPendingIntent(event: Event, occ: ReminderSchedule.Occurrence): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.xiaomaotai.REMINDER"
            putExtra("event_id", event.id)
            putExtra("event_name", event.eventName)
            putExtra("event_date", event.eventDate)
            putExtra("days_remaining", occ.daysBefore)
            putExtra("reminder_hour", occ.hour)
            putExtra("reminder_label", occ.label)
        }
        return PendingIntent.getBroadcast(
            context,
            "${event.id}_${occ.daysBefore}_${occ.hour}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 备份闹钟：原时刻后 1/2/3 分钟各一发，接收端凭台账去重 */
    private fun setupBackupAlarms(event: Event, occ: ReminderSchedule.Occurrence, primaryAtMillis: Long) {
        listOf(1, 2, 3).forEach { delayMinutes ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.xiaomaotai.BACKUP_REMINDER"
                putExtra("event_id", event.id)
                putExtra("event_name", event.eventName)
                putExtra("event_date", event.eventDate)
                putExtra("days_remaining", occ.daysBefore)
                putExtra("reminder_hour", occ.hour)
                putExtra("reminder_label", occ.label)
                putExtra("backup_delay", delayMinutes)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "${event.id}_${occ.daysBefore}_${occ.hour}_backup_${delayMinutes}".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(primaryAtMillis + delayMinutes * 60_000L, pendingIntent, exact = true)
        }
    }
}
