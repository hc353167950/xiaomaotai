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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
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
                // 今天已发过的档位不再重复设置（按「纪念日日期 + 档位 + 时刻」区分，
                // 闰月年同一档位有两个纪念日，必须分别记录，否则第二套会被误判为已发）
                if (hasReminderSentToday(event.id, occ.daysBefore, occ.hour, occ.anniversary)) {
                    Log.d(
                        TAG,
                        "${occ.label}提醒(${occ.hour}:00)今天已发送，跳过: ${event.eventName} @ ${occ.anniversary}"
                    )
                    return@forEach
                }

                val triggerAt = occ.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setAlarm(triggerAt, primaryPendingIntent(event, occ), exact)
                Log.d(
                    TAG,
                    "已设置${occ.label}提醒(${occ.hour}:00): ${event.eventName} " +
                        "纪念日=${occ.anniversary} at ${occ.triggerAt}"
                )

                // 精确模式下追加 1-3 分钟后的备份闹钟，防厂商 ROM 吞掉首发
                if (exact) setupBackupAlarms(event, occ, triggerAt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置提醒失败: ${e.message}")
        }
    }

    /**
     * 取消事件的全部提醒，包括备份闹钟与升级前旧格式的 PendingIntent。
     *
     * 由于闹钟 key 现在包含纪念日日期（闰月年有两个），这里无法枚举出全部 key，
     * 因此同时取消「旧格式」与「新格式」两类：
     * - 旧格式：eventId_档位_时刻（及更早的 eventId_档位）
     * - 新格式：通过 [CancelAllReminders] 广播让 AlarmManager 按事件前缀清理不可行，
     *   所以改为在设置新提醒前由 [safeScheduleReminder] 逐个覆盖，
     *   并在删除事件时调用 [cancelAllForEvent]。
     */
    fun cancelReminder(eventId: String) {
        try {
            ReminderSchedule.OFFSETS.forEach { daysBefore ->
                listOf(0, 8, 12).forEach { hour ->
                    // 旧格式（无纪念日日期）
                    cancelAlarm("${eventId}_${daysBefore}_${hour}")
                    listOf(1, 2, 3).forEach { delay ->
                        cancelAlarm("${eventId}_${daysBefore}_${hour}_backup_${delay}")
                    }
                }
                // 更早版本不带 hour 的格式
                cancelAlarm("${eventId}_${daysBefore}")
            }
            // 带纪念日日期的「新格式」键无法在这里枚举，由 cancelAllForEvent 清理；
            // 设置新提醒时同 key 会被 FLAG_UPDATE_CURRENT 覆盖，不会残留旧闹钟。
            Log.d(TAG, "已取消事件旧格式提醒: $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "取消提醒失败: $eventId, ${e.message}")
        }
    }

    /**
     * 彻底取消某事件的所有闹钟（含带纪念日日期的所有变体）。
     *
     * 用于删除事件：此时需要按「事件 + 日期」组合穷举清理。
     * 只清理未来 3 年内的组合，避免循环次数过大。
     */
    fun cancelAllForEvent(event: Event) {
        try {
            val today = java.time.LocalDate.now()
            val countdown = Countdown.of(event.eventDate, today)
            val anniversaries = countdown?.occurrences?.toMutableSet() ?: mutableSetOf()
            // 加上未来 3 年的候选，确保历史残留也能清掉
            for (offset in 0..3) {
                val year = LunarCalendarHelper.currentLunarYear(today) + offset
                val parsed = DateParser.parse(event.eventDate)
                if (parsed != null &&
                    (parsed.type == DateParser.DateType.LUNAR ||
                        parsed.type == DateParser.DateType.LUNAR_MONTH_DAY)
                ) {
                    anniversaries.addAll(
                        LunarCalendarHelper.candidatesForYear(
                            year, parsed.month, parsed.day, parsed.isLeapMonth
                        )
                    )
                } else {
                    anniversaries.add(today.plusYears(offset.toLong()))
                }
            }

            anniversaries.forEach { anniversary ->
                ReminderSchedule.OFFSETS.forEach { daysBefore ->
                    listOf(0, 8, 12).forEach { hour ->
                        cancelAlarm("${event.id}_${anniversary}_${daysBefore}_${hour}")
                        listOf(1, 2, 3).forEach { delay ->
                            cancelAlarm("${event.id}_${anniversary}_${daysBefore}_${hour}_backup_${delay}")
                        }
                    }
                }
            }
            cancelReminder(event.id)
            Log.d(TAG, "已彻底取消事件全部闹钟: ${event.eventName}")
        } catch (e: Exception) {
            Log.e(TAG, "彻底取消闹钟失败: ${event.eventName}, ${e.message}")
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

    /**
     * 今天是否已为该事件、该纪念日、该档位发送过提醒。
     *
     * [anniversary] 区分闰月年的两个纪念日（普通六月十六 / 闰六月十六）：
     * 若不区分，第二套提醒会被第一套的台账误判为「今天已发」而永不触发。
     */
    fun hasReminderSentToday(
        eventId: String,
        daysRemaining: Int,
        hour: Int,
        anniversary: java.time.LocalDate
    ): Boolean {
        return sentLedger.getBoolean(ledgerKey(eventId, daysRemaining, hour, anniversary), false)
    }

    /** 记录今天已为该事件、该纪念日、该档位发送过提醒 */
    fun markReminderSentToday(
        eventId: String,
        daysRemaining: Int,
        hour: Int,
        anniversary: java.time.LocalDate
    ) {
        sentLedger.edit()
            .putBoolean(ledgerKey(eventId, daysRemaining, hour, anniversary), true)
            .apply()
    }

    /**
     * 台账键：eventId_档位_时刻_纪念日日期_发送日期。
     * 日期用 ISO 格式（含 `-`），便于 [cleanupOldLedger] 按最后一段识别并清理。
     */
    private fun ledgerKey(
        eventId: String,
        daysRemaining: Int,
        hour: Int,
        anniversary: java.time.LocalDate
    ): String {
        val today = java.time.LocalDate.now().toString()
        return "${eventId}_${daysRemaining}_${hour}_$anniversary" + "_$today"
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
            val cutoffDate = java.time.LocalDate.now().minusDays(7).toString()

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
                editor.apply()
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
            // 纪念日本身：闰月年有两个不同日期，接收端据此区分与去重
            putExtra("anniversary", occ.anniversary.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            // requestCode 必须包含纪念日日期，否则闰月年的第二套闹钟会覆盖第一套
            "${event.id}_${occ.anniversary}_${occ.daysBefore}_${occ.hour}".hashCode(),
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
                putExtra("anniversary", occ.anniversary.toString())
                putExtra("backup_delay", delayMinutes)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "${event.id}_${occ.anniversary}_${occ.daysBefore}_${occ.hour}_backup_${delayMinutes}".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(primaryAtMillis + delayMinutes * 60_000L, pendingIntent, exact = true)
        }
    }
}
