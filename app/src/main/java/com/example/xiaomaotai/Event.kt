package com.example.xiaomaotai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = EventStatusSerializer::class)
enum class EventStatus(val value: Int) {
    NORMAL(0),
    DELETED(1),
    UPDATED(2);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

@Serializable(with = EventTypeSerializer::class)
enum class EventType(val value: Int) {
    SOLAR(0),           // 公历事件 (yyyy-MM-dd)
    LUNAR(1),           // 农历事件 (lunar:yyyy-MM-dd)
    MONTH_DAY(2),       // 忽略年份事件-公历 (MM-dd)
    LUNAR_MONTH_DAY(3); // 忽略年份事件-农历 (lunar:MM-dd 或 lunar:LMM-dd)

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: SOLAR
        
        fun fromDateString(dateString: String): EventType {
            return when {
                dateString.startsWith("lunar:") -> {
                    val lunarPart = dateString.removePrefix("lunar:")
                    // 检查是否为忽略年份的农历格式
                    if (lunarPart.matches(Regex("L?\\d{2}-\\d{2}"))) {
                        LUNAR_MONTH_DAY
                    } else {
                        LUNAR
                    }
                }
                dateString.matches(Regex("\\d{2}-\\d{2}")) -> MONTH_DAY
                else -> SOLAR
            }
        }
    }
}

class EventTypeSerializer : KSerializer<EventType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventType", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: EventType) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): EventType {
        return EventType.fromInt(decoder.decodeInt())
    }
}

class EventStatusSerializer : KSerializer<EventStatus> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventStatus", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: EventStatus) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): EventStatus {
        return EventStatus.fromInt(decoder.decodeInt())
    }
}

/**
 * 提醒语气：决定通知文案的情感色彩。
 *
 * 之所以要区分，是因为「生日」适合祝福、「忌日」绝不能推「祝你开心」。
 * 默认 [NEUTRAL] 只陈述事实，最安全。
 */
@Serializable(with = ReminderToneSerializer::class)
enum class ReminderTone(val value: Int, val label: String) {
    NEUTRAL(0, "中性"),
    BLESSING(1, "祝福"),
    MEMORIAL(2, "纪念"),
    REMIND(3, "提醒");

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NEUTRAL
    }
}

class ReminderToneSerializer : KSerializer<ReminderTone> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReminderTone", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: ReminderTone) = encoder.encodeInt(value.value)
    override fun deserialize(decoder: Decoder): ReminderTone = ReminderTone.fromInt(decoder.decodeInt())
}

/**
 * 计数方向。
 *
 * - [COUNTDOWN]：距离下一次还有 N 天（默认）
 * - [COUNT_UP]：从起始日至今已经 N 天（「在一起 1825 天」「戒烟第 90 天」）
 */
@Serializable(with = EventModeSerializer::class)
enum class EventMode(val value: Int) {
    COUNTDOWN(0),
    COUNT_UP(1);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: COUNTDOWN
    }
}

class EventModeSerializer : KSerializer<EventMode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventMode", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: EventMode) = encoder.encodeInt(value.value)
    override fun deserialize(decoder: Decoder): EventMode = EventMode.fromInt(decoder.decodeInt())
}

@Serializable
data class Event(
    val id: String = "",
    val eventName: String,
    val eventDate: String, // 日期格式：yyyy-MM-dd, lunar:yyyy-MM-dd, 或 MM-dd
    val sortOrder: Int = 0,
    val status: EventStatus = EventStatus.NORMAL,
    val eventType: EventType = EventType.SOLAR, // 事件类型
    val backgroundId: Int = 0, // 背景样式 ID

    /** 备注：记录这个日子背后的故事。默认空，卡片上折叠显示 */
    val note: String = "",

    /** 是否为该事件启用提醒。关闭后不排任何闹钟 */
    val reminderEnabled: Boolean = true,

    /**
     * 自定义提前天数。null 表示沿用全局默认 [ReminderSchedule.OFFSETS]。
     * 空列表表示「仅当天提醒」。
     */
    val reminderDaysBefore: List<Int>? = null,

    /** 提醒语气，决定通知文案的情感色彩 */
    val reminderTone: ReminderTone = ReminderTone.NEUTRAL,

    /** 计数方向：倒数 / 正数 */
    val mode: EventMode = EventMode.COUNTDOWN,

    @kotlinx.serialization.Transient var cachedDays: Long? = null // 缓存的天数（不持久化）
) {
    /** 便捷方法：根据 eventDate 自动推断 eventType */
    fun withAutoDetectedType(): Event {
        return this.copy(eventType = EventType.fromDateString(eventDate))
    }

    /** 该事件实际生效的提醒档位 */
    fun effectiveOffsets(): List<Int> = reminderDaysBefore ?: ReminderSchedule.OFFSETS
}
