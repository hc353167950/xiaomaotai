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
    SOLAR(0),      // 公历事件 (yyyy-MM-dd)
    LUNAR(1),      // 农历事件 (lunar:yyyy-MM-dd)
    MONTH_DAY(2);  // 忽略年份事件 (MM-dd)

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: SOLAR
        
        fun fromDateString(dateString: String): EventType {
            return when {
                dateString.startsWith("lunar:") -> LUNAR
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

@Serializable
data class Event(
    val id: String = "",
    val eventName: String,
    val eventDate: String, // 日期格式：yyyy-MM-dd, lunar:yyyy-MM-dd, 或 MM-dd
    val sortOrder: Int = 0,
    val status: EventStatus = EventStatus.NORMAL,
    val eventType: EventType = EventType.SOLAR, // 新增：事件类型字段
    val backgroundId: Int = 0 // 新增：背景ID字段，用于存储背景样式
) {
    // 便捷方法：根据eventDate自动推断eventType
    fun withAutoDetectedType(): Event {
        return this.copy(eventType = EventType.fromDateString(eventDate))
    }
}
