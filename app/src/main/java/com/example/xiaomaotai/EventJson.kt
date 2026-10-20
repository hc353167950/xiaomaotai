package com.example.xiaomaotai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * 事件列表的 JSON 编解码。
 *
 * 为什么需要这一层，而不是直接用 `Gson()`：
 *
 * Gson 反序列化 Kotlin data class 时会用 UnsafeAllocator **绕过构造器**，
 * 因此 Kotlin 里写的参数默认值（`note: String = ""`、`mode = COUNTDOWN` 等）
 * **完全不会生效**。旧版本存下的 JSON 没有这些新字段，读出来就是：
 *
 * - `note`          → null（声明为非空 String，一访问就 NPE）
 * - `reminderEnabled` → false（静默关掉用户所有提醒）
 * - `reminderTone`  → null（枚举，NPE）
 * - `mode`          → null（枚举，NPE）
 *
 * 表现就是「升级后卡片全部消失」——其实数据还在，是渲染时崩了。
 *
 * 这里用显式反序列化器逐字段兜底：字段缺失或类型不符时一律回落到默认值，
 * 并且把旧的枚举名字面量（如 `"COUNT_UP"`）和整数形式（如 `4`）都认下来，
 * 保证任何历史版本写出的数据都能读。
 */
object EventJson {

    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Event::class.java, EventDeserializer())
        .create()

    private val listType = object : TypeToken<List<Event>>() {}.type

    fun encode(events: List<Event>): String = gson.toJson(events, listType)

    /** 解析事件数组；整体损坏时返回空列表，不抛异常 */
    fun decodeList(json: String?): List<Event> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<Event>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("EventJson", "解析事件列表失败: ${e.message}")
            emptyList()
        }
    }

    /** 只解析出单个对象（云端包装格式用） */
    fun decodeObject(element: JsonElement?): Event? {
        if (element == null || element.isJsonNull || !element.isJsonObject) return null
        return try {
            gson.fromJson<Event>(element, Event::class.java)
        } catch (e: Exception) {
            android.util.Log.e("EventJson", "解析单个事件失败: ${e.message}")
            null
        }
    }

    // ---------- 逐字段兜底的反序列化器 ----------

    private class EventDeserializer : JsonDeserializer<Event> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Event {
            val obj: JsonObject = if (json != null && json.isJsonObject) {
                json.asJsonObject
            } else {
                JsonObject()
            }

            return Event(
                id = obj.stringOr("id", ""),
                eventName = obj.stringOr("eventName", ""),
                eventDate = obj.stringOr("eventDate", ""),
                sortOrder = obj.intOr("sortOrder", 0),
                status = enumOr(obj, "status", EventStatus.NORMAL) { EventStatus.fromInt(it) },
                eventType = enumOr(obj, "eventType", EventType.SOLAR) { EventType.fromInt(it) },
                backgroundId = obj.intOr("backgroundId", 0),
                // ---- 以下为 3.2.0 新增字段，旧数据缺省 ----
                note = obj.stringOr("note", ""),
                // 旧数据没有这个字段，默认必须为 true，否则会静默关掉全部提醒
                reminderEnabled = obj.booleanOr("reminderEnabled", true),
                reminderDaysBefore = obj.offsetsOrNull("reminderDaysBefore"),
                reminderTone = enumOr(obj, "reminderTone", ReminderTone.NEUTRAL) {
                    ReminderTone.fromInt(it)
                },
                mode = enumOr(obj, "mode", EventMode.COUNTDOWN) { EventMode.fromInt(it) }
            )
        }

        private fun JsonObject.stringOr(name: String, fallback: String): String {
            val e = get(name) ?: return fallback
            if (e.isJsonNull) return fallback
            return try {
                e.asString.ifEmpty { fallback }
            } catch (ex: Exception) {
                fallback
            }
        }

        private fun JsonObject.intOr(name: String, fallback: Int): Int {
            val e = get(name) ?: return fallback
            if (e.isJsonNull) return fallback
            return try {
                e.asInt
            } catch (ex: Exception) {
                fallback
            }
        }

        private fun JsonObject.booleanOr(name: String, fallback: Boolean): Boolean {
            val e = get(name) ?: return fallback
            if (e.isJsonNull) return fallback
            return try {
                e.asBoolean
            } catch (ex: Exception) {
                fallback
            }
        }

        /**
         * 枚举兜底：同时接受三种历史写法
         * - 名字面量 `"COUNT_UP"`（Gson 默认写法）
         * - 整数 `1`（kotlinx.serialization 写法，旧 WebDAV 数据可能是这种）
         * - 缺失或无法识别 → fallback
         */
        private fun <T> enumOr(
            obj: JsonObject,
            name: String,
            fallback: T,
            fromInt: (Int) -> T
        ): T {
            val e = obj.get(name) ?: return fallback
            if (e.isJsonNull) return fallback
            return try {
                if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) {
                    fromInt(e.asInt)
                } else {
                    val raw = e.asString
                    @Suppress("UNCHECKED_CAST")
                    val match = enumValuesByName(fallback, raw)
                    match ?: fallback
                }
            } catch (ex: Exception) {
                fallback
            }
        }

        /** 按名字匹配枚举，找不到返回 null */
        private fun <T> enumValuesByName(fallback: T, name: String): T? {
            @Suppress("UNCHECKED_CAST")
            val constants = (fallback as? Enum<*>)?.declaringJavaClass?.enumConstants
                ?: return null
            return constants.firstOrNull { (it as Enum<*>).name == name } as? T
        }

        /** 提前档位：缺失 → null（跟随全局默认）；空数组 → 空列表（不提醒） */
        private fun JsonObject.offsetsOrNull(name: String): List<Int>? {
            val e = get(name) ?: return null
            if (e.isJsonNull || !e.isJsonArray) return null
            return try {
                e.asJsonArray.mapNotNull { item ->
                    if (item.isJsonNull) null else item.asInt
                }
            } catch (ex: Exception) {
                null
            }
        }
    }
}
