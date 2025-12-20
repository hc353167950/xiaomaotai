package com.example.xiaomaotai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * 零点闹钟辅助类
 * 统一管理零点更新闹钟的设置和取消
 */
object MidnightAlarmHelper {

    private const val TAG = "MidnightAlarmHelper"
    private const val MIDNIGHT_ALARM_REQUEST_CODE = 9998

    /**
     * 设置零点精确更新闹钟
     * 使用 BroadcastReceiver 接收闹钟，比直接启动 Service 更可靠
     */
    fun setupMidnightAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, MidnightUpdateReceiver::class.java).apply {
                action = MidnightUpdateReceiver.ACTION_MIDNIGHT_UPDATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // 计算明天凌晨00:00:01的时间
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }

            val triggerTime = calendar.timeInMillis

            // 使用精确闹钟确保零点准时触发
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 需要检查精确闹钟权限
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "已设置零点精确闹钟（Android 12+），将在 ${calendar.time} 触发")
                } else {
                    // 没有精确闹钟权限，使用非精确闹钟
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "无精确闹钟权限，使用非精确闹钟，将在 ${calendar.time} 左右触发")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "已设置零点精确闹钟（Android 6+），将在 ${calendar.time} 触发")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "已设置零点精确闹钟，将在 ${calendar.time} 触发")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置零点闹钟失败: ${e.message}", e)
        }
    }

    /**
     * 取消零点更新闹钟
     */
    fun cancelMidnightAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, MidnightUpdateReceiver::class.java).apply {
                action = MidnightUpdateReceiver.ACTION_MIDNIGHT_UPDATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "已取消零点更新闹钟")
        } catch (e: Exception) {
            Log.e(TAG, "取消零点闹钟失败: ${e.message}", e)
        }
    }
}
