package com.example.xiaomaotai

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

    /** 全局默认提醒档位：7天前、1天前、当天。单条事件可覆盖 */
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

    /**
     * 提醒通知的标题与正文（唯一文案来源）。
     *
     * [tone] 决定情感色彩——这是必要的区分：
     * 「生日」推「祝你开心」很自然，「忌日」推同一句就非常冒犯。
     * 默认 [ReminderTone.NEUTRAL] 只陈述事实，不预设场合。
     */
    fun notificationContent(
        eventName: String,
        daysBefore: Int,
        tone: ReminderTone = ReminderTone.NEUTRAL
    ): Pair<String, String> {
        val title = when {
            daysBefore == 0 && tone == ReminderTone.BLESSING -> "今天是个好日子"
            daysBefore == 0 -> "纪念日到了"
            else -> "纪念日提醒"
        }

        val content = when (tone) {
            ReminderTone.BLESSING -> when (daysBefore) {
                7 -> "还有7天就是「$eventName」了，提前准备一下心意吧"
                1 -> "明天就是「$eventName」了，别忘了"
                0 -> "今天是「$eventName」，祝一切顺遂"
                else -> "${labelFor(daysBefore)}：$eventName"
            }
            ReminderTone.MEMORIAL -> when (daysBefore) {
                7 -> "还有7天是「$eventName」"
                1 -> "明天是「$eventName」"
                0 -> "今天是「$eventName」，愿一切安好"
                else -> "${labelFor(daysBefore)}：$eventName"
            }
            ReminderTone.REMIND -> when (daysBefore) {
                7 -> "还有7天：「$eventName」"
                1 -> "明天：「$eventName」"
                0 -> "今天：「$eventName」"
                else -> "${labelFor(daysBefore)}：$eventName"
            }
            ReminderTone.NEUTRAL -> when (daysBefore) {
                7 -> "还有7天就是「$eventName」"
                1 -> "明天就是「$eventName」"
                0 -> "今天是「$eventName」"
                else -> "${labelFor(daysBefore)}：$eventName"
            }
        }

        return title to content
    }

    /**
     * 一次待触发的提醒：事件在 [triggerAt] 时刻按 [daysBefore] 档位提醒。
     *
     * [anniversary] 是该提醒所服务的那个纪念日本身（闰月年会有两个不同的值，
     * 例如普通六月十六与闰六月十六），用于生成互不冲突的闹钟 requestCode。
     */
    data class Occurrence(
        val daysBefore: Int,
        val hour: Int,
        val triggerAt: LocalDateTime,
        val anniversary: LocalDate
    ) {
        val label: String get() = labelFor(daysBefore)
    }

    /**
     * 计算事件全部提醒时刻。
     *
     * 以 [Countdown.occurrences] 里的**每一个**纪念日日期为锚点，展开各档位×各时刻。
     * 农历闰月年的「六月十六」会有两个日期（普通月 + 闰月），因此会生成两整套提醒
     * —— 用户一年过两次这个纪念日，两次都要提醒。
     *
     * 只返回 [now] 之后的时刻（唯一例外：事件距今恰好等于档位天数时，
     * 已过时刻折算为立即提醒——保持"当天中午添加当天事件仍会提醒"的行为）。
     *
     * @return 按时间升序的触发列表；日期无法解析时为空列表
     */
    fun occurrencesFor(
        eventDate: String,
        now: LocalDateTime,
        offsets: List<Int> = OFFSETS
    ): List<Occurrence> {
        if (offsets.isEmpty()) return emptyList()
        val countdown = Countdown.of(eventDate, now.toLocalDate()) ?: return emptyList()
        val anniversaries = countdown.occurrences.ifEmpty { listOf(countdown.nextDate) }

        val result = mutableListOf<Occurrence>()
        anniversaries.forEachIndexed { anniversaryIndex, anniversary ->
            offsets.forEach { daysBefore ->
                hoursFor(daysBefore).forEachIndexed { hourIndex, hour ->
                    val scheduled = anniversary.minusDays(daysBefore.toLong()).atTime(hour, 0)
                    when {
                        scheduled.isAfter(now) ->
                            result.add(Occurrence(daysBefore, hour, scheduled, anniversary))
                        // 该纪念日距今恰好 daysBefore 天且时刻已过：30秒后立即补提醒
                        // 错峰：按纪念日序号与时刻序号错开，避免多个提醒同时弹出
                        ChronoUnit.DAYS.between(now.toLocalDate(), anniversary) == daysBefore.toLong() ->
                            result.add(
                                Occurrence(
                                    daysBefore,
                                    hour,
                                    now.plusSeconds(30L + anniversaryIndex * 20L + hourIndex * 5L),
                                    anniversary
                                )
                            )
                        // 其余已过时刻直接跳过
                    }
                }
            }
        }
        return result.sortedBy { it.triggerAt }
    }

    /**
     * 轮询路径（常驻服务每15分钟检查）用的判定：当前时刻附近是否有档位到期。
     *
     * 同时检查所有候选纪念日，闰月年的第二个日期同样能被识别。
     *
     * @param windowMinutes 允许的时间窗口（分钟），轮询间隔内的提醒不被漏掉
     * @return 到期的 Occurrence 列表，可能为空
     */
    fun dueNow(
        eventDate: String,
        now: LocalDateTime,
        windowMinutes: Long = 15,
        offsets: List<Int> = OFFSETS
    ): List<Occurrence> {
        if (offsets.isEmpty()) return emptyList()
        val countdown = Countdown.of(eventDate, now.toLocalDate()) ?: return emptyList()
        val anniversaries = countdown.occurrences.ifEmpty { listOf(countdown.nextDate) }

        val result = mutableListOf<Occurrence>()
        anniversaries.forEach { anniversary ->
            val daysBefore = ChronoUnit.DAYS.between(now.toLocalDate(), anniversary)
            if (daysBefore !in offsets.map { it.toLong() }) return@forEach

            hoursFor(daysBefore.toInt()).forEach { hour ->
                val scheduled = now.toLocalDate().atTime(hour, 0)
                val diffMinutes = java.time.Duration.between(scheduled, now).abs().toMinutes()
                if (diffMinutes <= windowMinutes) {
                    result.add(Occurrence(daysBefore.toInt(), hour, scheduled, anniversary))
                }
            }
        }
        return result
    }

}
