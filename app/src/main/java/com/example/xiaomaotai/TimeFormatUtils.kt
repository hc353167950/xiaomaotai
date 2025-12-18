package com.example.xiaomaotai

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

/**
 * 时间格式化工具类
 * 用于格式化相对时间显示和日期处理
 */
object TimeFormatUtils {

    // 常用日期格式 - 线程安全的懒加载
    private val dateFormatYMD: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val dateFormatMD: SimpleDateFormat
        get() = SimpleDateFormat("MM-dd", Locale.getDefault())

    private val dateFormatFull: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 将时间戳格式化为相对时间文本
     * 规则：
     * - 1小时内：显示"X分钟之前"
     * - 1小时-1天：显示"X小时之前"
     * - 1天-1年：显示日期（MM-dd）
     * - 超过1年：显示完整日期时间（yyyy-MM-dd HH:mm）
     *
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间文本
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp

        // 如果是未来时间，显示完整时间
        if (diffMillis < 0) {
            return dateFormatFull.format(Date(timestamp))
        }

        val diffMinutes = diffMillis / (60 * 1000)
        val diffHours = diffMillis / (60 * 60 * 1000)
        val diffDays = diffMillis / (24 * 60 * 60 * 1000)

        return when {
            // 1小时内：显示分钟
            diffMinutes < 60 -> {
                if (diffMinutes <= 1) "刚刚" else "${diffMinutes}分钟之前"
            }

            // 1小时到1天：显示小时
            diffHours < 24 -> {
                "${diffHours}小时之前"
            }

            // 1天到1年：显示日期
            diffDays < 365 -> {
                dateFormatMD.format(Date(timestamp))
            }

            // 超过1年：显示完整日期时间
            else -> {
                dateFormatFull.format(Date(timestamp))
            }
        }
    }

    /**
     * 获取当前时间的相对时间文本（用于新发送的通知）
     */
    fun getCurrentRelativeTimeText(): String {
        return "刚刚"
    }

    /**
     * 格式化日期为 yyyy-MM-dd 格式
     */
    fun formatDateYMD(date: Date): String {
        return dateFormatYMD.format(date)
    }

    /**
     * 格式化日期为 MM-dd 格式
     */
    fun formatDateMD(date: Date): String {
        return dateFormatMD.format(date)
    }

    /**
     * 格式化日期为完整格式 yyyy-MM-dd HH:mm
     */
    fun formatDateFull(date: Date): String {
        return dateFormatFull.format(date)
    }

    /**
     * 解析 yyyy-MM-dd 格式的日期字符串
     * @return 解析成功返回 Date，失败返回 null
     */
    fun parseDateYMD(dateString: String): Date? {
        return try {
            dateFormatYMD.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 MM-dd 格式的日期字符串
     * @return 解析成功返回 Date，失败返回 null
     */
    fun parseDateMD(dateString: String): Date? {
        return try {
            dateFormatMD.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取今天的日期字符串 (yyyy-MM-dd)
     */
    fun getTodayString(): String {
        return dateFormatYMD.format(Date())
    }

    /**
     * 判断日期字符串是否为 yyyy-MM-dd 格式
     */
    fun isYMDFormat(dateString: String): Boolean {
        return dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    }

    /**
     * 判断日期字符串是否为 MM-dd 格式
     */
    fun isMDFormat(dateString: String): Boolean {
        return dateString.matches(Regex("\\d{2}-\\d{2}"))
    }

    /**
     * 判断日期字符串是否为农历格式 (lunar:开头)
     */
    fun isLunarFormat(dateString: String): Boolean {
        return dateString.startsWith("lunar:")
    }
}
