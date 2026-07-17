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

    /** 提醒通知的稳定 ID：同事件同档位同时刻覆盖，不同时刻互不覆盖 */
    fun notificationId(eventId: String, daysBefore: Int, hour: Int): Int =
        "${eventId}_${daysBefore}_${hour}".hashCode()

    /**
     * 发送纪念日提醒通知。
     * 标题/正文来自 [ReminderSchedule.notificationContent]，点击打开 App。
     */
    fun sendReminder(context: Context, event: Event, daysBefore: Int, hour: Int) {
        ensureChannel(context)
        val (title, content) = ReminderSchedule.notificationContent(event.eventName, daysBefore)

        val notification = baseBuilder(context, event.id, event.eventName)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId(event.id, daysBefore, hour), notification)
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

    /** 取消事件在所有档位×时刻的通知（事件被删除时清理残留） */
    fun cancelAll(context: Context, eventId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderSchedule.OFFSETS.forEach { daysBefore ->
            ReminderSchedule.hoursFor(daysBefore).forEach { hour ->
                manager.cancel(notificationId(eventId, daysBefore, hour))
            }
            // 兼容旧格式 ID（升级前设置的通知）
            manager.cancel("${eventId}_$daysBefore".hashCode())
        }
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
