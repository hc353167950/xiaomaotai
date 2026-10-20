package com.example.xiaomaotai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 通知快速操作的接收器。
 *
 * 通知上挂两个 Action：
 * - [ACTION_SNOOZE] 稍后提醒：1 小时后重发同一条提醒
 * - [ACTION_MUTE_YEAR] 今年不再提醒：把该事件的「本年度纪念日」加入静音清单，
 *   此后所有档位都跳过，直到该纪念日日期过去
 *
 * 单独成一个 Receiver 而不是塞进 [ReminderReceiver]：
 * 前者由用户点击触发（交互），后者由闹钟触发（调度），职责不同。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val eventName = intent.getStringExtra(EXTRA_EVENT_NAME) ?: "纪念日"
        val daysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val anniversary = intent.getStringExtra(EXTRA_ANNIVERSARY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(
                context, eventId, eventName, daysBefore, hour, anniversary, intent
            )
            ACTION_MUTE_YEAR -> handleMuteYear(context, eventId, eventName, anniversary)
        }
    }

    /** 稍后提醒：把同一条通知在 1 小时后重新排一次 */
    private fun handleSnooze(
        context: Context,
        eventId: String,
        eventName: String,
        daysBefore: Int,
        hour: Int,
        anniversary: LocalDate?,
        original: Intent
    ) {
        try {
            val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.xiaomaotai.REMINDER"
                putExtra("event_id", eventId)
                putExtra("event_name", eventName)
                putExtra("event_date", original.getStringExtra(EXTRA_EVENT_DATE) ?: "")
                putExtra("days_remaining", daysBefore)
                putExtra("reminder_hour", hour)
                putExtra("anniversary", anniversary?.toString() ?: "")
                // 标记为 snooze，让接收端跳过「已发送」台账的拦截
                putExtra("is_snooze", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "snooze_${eventId}_${triggerAt}".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            }

            // 收起当前通知，避免用户以为没生效
            ReminderNotifier.cancelAll(context, eventId)
            Log.d(TAG, "已安排 ${SNOOZE_MINUTES} 分钟后重提醒: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "稍后提醒设置失败: ${e.message}")
        }
    }

    /**
     * 今年不再提醒：把该事件本年度所有档位加入静音清单。
     *
     * 用「纪念日日期」作键而不是「事件」：闰月年有两个纪念日，
     * 静音第一个不应连带静音第二个——那是两个不同的日子。
     */
    private fun handleMuteYear(
        context: Context,
        eventId: String,
        eventName: String,
        anniversary: LocalDate?
    ) {
        try {
            val prefs = context.getSharedPreferences(PREF_MUTE, Context.MODE_PRIVATE)
            val target = anniversary ?: LocalDate.now()
            prefs.edit().putBoolean(muteKey(eventId, target), true).apply()

            // 取消该纪念日已排的所有闹钟
            ReminderNotifier.cancelAll(context, eventId)
            ReminderManager(context).cancelAllForEvent(
                Event(id = eventId, eventName = eventName, eventDate = "")
            )
            Log.d(TAG, "已静音本年度提醒: $eventName @ $target")
        } catch (e: Exception) {
            Log.e(TAG, "静音设置失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ReminderActionReceiver"

        const val ACTION_SNOOZE = "com.example.xiaomaotai.ACTION_SNOOZE"
        const val ACTION_MUTE_YEAR = "com.example.xiaomaotai.ACTION_MUTE_YEAR"

        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_EVENT_NAME = "event_name"
        private const val EXTRA_EVENT_DATE = "event_date"
        private const val EXTRA_DAYS_BEFORE = "days_before"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_ANNIVERSARY = "anniversary"

        /** 稍后提醒的延后分钟数 */
        const val SNOOZE_MINUTES = 60

        private const val PREF_MUTE = "reminder_mute"

        private fun muteKey(eventId: String, anniversary: LocalDate): String =
            "${eventId}_$anniversary"

        /**
         * 该事件的这个纪念日是否已被「今年不再提醒」静音。
         *
         * 只在纪念日日期尚未到来时生效——过了那一天，静音自动失效，
         * 明年同一个日子仍会正常提醒，符合「今年」的字面语义。
         */
        fun isMutedYear(
            context: Context,
            eventId: String,
            anniversary: LocalDate
        ): Boolean {
            val prefs = context.getSharedPreferences(PREF_MUTE, Context.MODE_PRIVATE)
            val muted = prefs.getBoolean(muteKey(eventId, anniversary), false)
            if (!muted) return false
            // 纪念日已过去 → 清掉标记，为下一年解除静音
            if (anniversary.isBefore(LocalDate.now())) {
                prefs.edit().remove(muteKey(eventId, anniversary)).apply()
                return false
            }
            return true
        }

        /** 构造「稍后提醒」用的 Intent extras 名称，供 Notifier 复用 */
        fun extrasFor(
            eventId: String,
            eventName: String,
            eventDate: String,
            daysBefore: Int,
            hour: Int,
            anniversary: LocalDate?
        ): Map<String, Any?> = mapOf(
            EXTRA_EVENT_ID to eventId,
            EXTRA_EVENT_NAME to eventName,
            EXTRA_EVENT_DATE to eventDate,
            EXTRA_DAYS_BEFORE to daysBefore,
            EXTRA_HOUR to hour,
            EXTRA_ANNIVERSARY to anniversary?.toString()
        )
    }
}
