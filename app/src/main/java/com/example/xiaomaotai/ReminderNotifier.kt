package com.example.xiaomaotai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 提醒通知的唯一构建/发送出口。
 *
 * 此前 NotificationCompat.Builder 在 ReminderManager、ReminderReceiver、
 * PersistentNotificationService、ReminderForegroundService 里共出现 8 处，
 * 渠道创建逻辑 3 处；样式与行为彼此漂移。现在渠道、点击行为、优先级、
 * 通知 ID 规则全部收拢在这里。
 */
object ReminderNotifier {

    const val CHANNEL_ID = "memory_day_reminders"

    /** 创建提醒通知渠道（幂等，可在任何入口安全调用） */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "纪念日提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "纪念日提醒通知"
            enableVibration(true)
            enableLights(true)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setBypassDnd(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(true)
            }
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 提醒通知的稳定 ID：同事件同纪念日同档位同时刻覆盖，不同时刻互不覆盖。
     *
     * 必须包含 [anniversary]：闰月年的「六月十六」有两个纪念日，
     * 若不带日期，第二次提醒会把第一次的通知覆盖掉。
     */
    fun notificationId(
        eventId: String,
        daysBefore: Int,
        hour: Int,
        anniversary: java.time.LocalDate? = null
    ): Int = if (anniversary == null) {
        "${eventId}_${daysBefore}_${hour}".hashCode()
    } else {
        "${eventId}_${anniversary}_${daysBefore}_${hour}".hashCode()
    }

    /**
     * 发送纪念日提醒通知。
     * 标题/正文来自 [ReminderSchedule.notificationContent]，点击打开 App。
     *
     * @param anniversary 这次提醒对应的纪念日日期。若该日期落在农历闰月那天，
     *   正文会额外标注「闰月」，让用户知道这是一年中的第二次。
     */
    fun sendReminder(
        context: Context,
        event: Event,
        daysBefore: Int,
        hour: Int,
        anniversary: java.time.LocalDate? = null
    ) {
        ensureChannel(context)
        val (title, baseContent) = ReminderSchedule.notificationContent(
            event.eventName, daysBefore, event.reminderTone
        )

        // 闰月那天加标注，区分「六月十六」与「闰六月十六」
        val lunarLabel = leapLabelFor(event.eventDate, anniversary)
        val content = if (lunarLabel != null) {
            "$baseContent（$lunarLabel）"
        } else {
            baseContent
        }

        // 有备注时把备注也带进 BigText，用户不用点开就知道是什么事
        val expanded = if (event.note.isNotBlank()) {
            "$content\n\n${event.note}"
        } else {
            content
        }

        val notification = baseBuilder(context, event.id, event.eventName)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .addAction(
                0,
                "稍后提醒",
                actionIntent(
                    context,
                    ReminderActionReceiver.ACTION_SNOOZE,
                    event, daysBefore, hour, anniversary
                )
            )
            .addAction(
                0,
                "今年不再提醒",
                actionIntent(
                    context,
                    ReminderActionReceiver.ACTION_MUTE_YEAR,
                    event, daysBefore, hour, anniversary
                )
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId(event.id, daysBefore, hour, anniversary), notification)
    }

    /** 构造通知按钮的 PendingIntent，携带后续动作所需的全部上下文 */
    private fun actionIntent(
        context: Context,
        action: String,
        event: Event,
        daysBefore: Int,
        hour: Int,
        anniversary: java.time.LocalDate?
    ): PendingIntent {
        // 通知 ID 必须一并带过去：接收端要靠它精确撤掉这条通知。
        val nid = notificationId(event.id, daysBefore, hour, anniversary)
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            // 用 data 做唯一性区分：PendingIntent 的相等判定只看
            // (requestCode, action, data, type, class, categories)，**忽略 extras**。
            // 两个按钮若仅靠 action 区分，一旦 requestCode 撞车就会互相覆盖，
            // 表现就是「点了没反应」或「点 A 触发了 B」。加 data 后彻底隔离。
            data = android.net.Uri.parse(
                "xiaomaotai://reminder/${action.substringAfterLast('.')}/$nid"
            )
            putExtra("event_id", event.id)
            putExtra("event_name", event.eventName)
            putExtra("event_date", event.eventDate)
            putExtra("days_before", daysBefore)
            putExtra("hour", hour)
            putExtra("anniversary", anniversary?.toString() ?: "")
            putExtra("notification_id", nid)
        }
        return PendingIntent.getBroadcast(
            context,
            "${action}_${event.id}_${anniversary}_${daysBefore}_${hour}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 若该纪念日日期落在闰月那天，返回如「闰六月十六」的标签；否则返回 null。
     */
    private fun leapLabelFor(eventDate: String, anniversary: java.time.LocalDate?): String? {
        if (anniversary == null) return null
        val parsed = DateParser.parse(eventDate) ?: return null
        if (parsed.type != DateParser.DateType.LUNAR &&
            parsed.type != DateParser.DateType.LUNAR_MONTH_DAY
        ) {
            return null
        }
        val lunarYear = LunarCalendarHelper.currentLunarYear(anniversary)
        val isLeapHit = LunarCalendarHelper.isLeapCandidate(
            lunarYear, parsed.month, parsed.day, anniversary
        )
        if (!isLeapHit) return null
        return "闰${LunarCalendarHelper.getLunarMonthName(parsed.month)}" +
            LunarCalendarHelper.getLunarDayName(parsed.day)
    }

    /** 发送测试通知（设置页"测试通知"按钮） */
    fun sendTest(context: Context) {
        ensureChannel(context)
        val notification = baseBuilder(context, "test_notification", "测试通知")
            .setContentTitle("测试通知")
            .setContentText("如果你看到这条通知，说明通知系统工作正常。点击可打开APP")
            .setSubText(TimeFormatUtils.getCurrentRelativeTimeText())
            .setShowWhen(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify("immediate_test".hashCode(), notification)
    }

    /**
     * 撤掉某个事件相关的提醒通知。
     *
     * 这里是「点了没反应」的真正原因所在，需要注意两点：
     *
     * 1. **必须带上 anniversary**。发通知时用的是
     *    `notificationId(eventId, daysBefore, hour, anniversary)`，
     *    早期这里漏了最后一个参数，算出的 ID 与真正通知的 ID 不同，
     *    `cancel()` 自然取消不掉，通知会一直躺在通知栏里。
     *
     * 2. **穷举不了就交给 [cancelByEventPrefix] 兜底**：
     *    anniversary 无法枚举（闰月年有多个日期），
     *    调用点若有确切 ID 应优先用 [cancelById]。
     */
    fun cancelAll(
        context: Context,
        eventId: String,
        anniversary: java.time.LocalDate? = null
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderSchedule.OFFSETS.forEach { daysBefore ->
            ReminderSchedule.hoursFor(daysBefore).forEach { hour ->
                manager.cancel(notificationId(eventId, daysBefore, hour, anniversary))
                // 兼容旧版本发出的「不带 anniversary」的 ID
                manager.cancel(notificationId(eventId, daysBefore, hour, null))
            }
            // 更早版本不带 hour 的格式
            manager.cancel("${eventId}_$daysBefore".hashCode())
        }
    }

    /** 精确撤销一条通知：只有拿到真实 ID 时才能可靠工作 */
    fun cancelById(context: Context, notificationId: Int) {
        if (notificationId == 0) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    /** 统一的通知外观与点击行为 */
    private fun baseBuilder(context: Context, eventId: String, eventName: String): NotificationCompat.Builder {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("event_id", eventId)
            putExtra("event_name", eventName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
    }
}
