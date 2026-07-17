package com.example.xiaomaotai

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 提醒时刻表：整个应用中"什么时候提醒、提醒什么内容"的唯一来源。
 *
 * 此前时刻表 {7:[8], 1:[8], 0:[0,8,12]} 与通知文案在 ReminderManager、
 * PersistentNotificationService、ReminderReceiver 里各有一份副本，
 * 改一处提醒时间需要同步多处。现在全部调用方从这里取答案。
 *
 * 纯 JVM 逻辑，注入时钟即可单测"该不该发"的全部分支。
 */
object ReminderSchedule {

    /** 提前提醒的天数档位：7天前、1天前、当天 */
    val OFFSETS = listOf(7, 1, 0)

    /** 各档位的提醒时刻（24小时制）：7天前/1天前只在早8点，当天有三个时段 */
    fun hoursFor(daysBefore: Int): List<Int> = when (daysBefore) {
        0 -> listOf(0, 8, 12)
        else -> listOf(8)
    }

    /** 档位的口语化标签，用于日志与通知 */
    fun labelFor(daysBefore: Int): String = when (daysBefore) {
        7 -> "还有7天"
        1 -> "明天就是"
        0 -> "就是今天"
        else -> "还有${daysBefore}天"
    }

    /** 提醒通知的标题与正文（唯一文案来源） */
    fun notificationContent(eventName: String, daysBefore: Int): Pair<String, String> = when (daysBefore) {
        7 -> "纪念日提醒" to "还有7天就是「$eventName」了，记得准备哦！"
        1 -> "纪念日提醒" to "明天就是「$eventName」了，别忘记了！"
        0 -> "纪念日到了！" to "今天是「$eventName」，祝你开心！"
        else -> "纪念日提醒" to "${labelFor(daysBefore)}：$eventName"
    }

    /**
     * 一次待触发的提醒：事件在 [triggerAt] 时刻按 [daysBefore] 档位提醒。
     */
    data class Occurrence(
        val daysBefore: Int,
        val hour: Int,
        val triggerAt: LocalDateTime
    ) {
        val label: String get() = labelFor(daysBefore)
    }

    /**
     * 计算事件全部提醒时刻。
     *
     * 以 [Countdown] 的下一次纪念日为锚点，展开各档位×各时刻；
     * 只返回 [now] 之后的时刻（唯一例外：事件距今恰好等于档位天数时，
     * 已过时刻折算为立即提醒——保持"当天中午添加当天事件仍会提醒"的行为）。
     *
     * @return 按时间升序的触发列表；日期无法解析时为空列表
     */
    fun occurrencesFor(eventDate: String, now: LocalDateTime): List<Occurrence> {
        val countdown = Countdown.of(eventDate, now.toLocalDate()) ?: return emptyList()
        val anniversary: LocalDate = countdown.nextDate

        val result = mutableListOf<Occurrence>()
        OFFSETS.forEach { daysBefore ->
            hoursFor(daysBefore).forEachIndexed { hourIndex, hour ->
                val scheduled = anniversary.minusDays(daysBefore.toLong()).atTime(hour, 0)
                when {
                    scheduled.isAfter(now) -> result.add(Occurrence(daysBefore, hour, scheduled))
                    // 事件距今恰好 daysBefore 天且时刻已过：30秒后立即补提醒（错峰避免同时弹出）
                    countdown.days == daysBefore.toLong() ->
                        result.add(Occurrence(daysBefore, hour, now.plusSeconds(30L + hourIndex * 10)))
                    // 其余已过时刻直接跳过
                }
            }
        }
        return result.sortedBy { it.triggerAt }
    }

    /**
     * 轮询路径（常驻服务每15分钟检查）用的判定：当前时刻附近是否有档位到期。
     *
     * @param windowMinutes 允许的时间窗口（分钟），轮询间隔内的提醒不被漏掉
     * @return 到期的 (daysBefore, hour) 列表，可能为空
     */
    fun dueNow(eventDate: String, now: LocalDateTime, windowMinutes: Long = 15): List<Occurrence> {
        val countdown = Countdown.of(eventDate, now.toLocalDate()) ?: return emptyList()

        val daysBefore = countdown.days
        if (daysBefore !in OFFSETS.map { it.toLong() }) return emptyList()

        return hoursFor(daysBefore.toInt()).mapNotNull { hour ->
            val scheduled = now.toLocalDate().atTime(hour, 0)
            val diffMinutes = java.time.Duration.between(scheduled, now).abs().toMinutes()
            if (diffMinutes <= windowMinutes) Occurrence(daysBefore.toInt(), hour, scheduled) else null
        }
    }
}
