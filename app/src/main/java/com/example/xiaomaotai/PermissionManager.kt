package com.example.xiaomaotai

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 权限管理器 - 统一管理APP所需的各种权限
 */
class PermissionManager(private val context: Context) {
    
    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    /**
     * 检查精确闹钟权限 (Android 12+)
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Android 12以下默认有权限
        }
    }
    
    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Android 6.0以下不需要
        }
    }
    
    /**
     * 获取通知权限设置Intent
     */
    fun getNotificationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
    
    /**
     * 获取精确闹钟权限设置Intent (Android 12+)
     */
    fun getExactAlarmSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            // Android 12以下跳转到应用详情页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 获取电池优化设置Intent
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 检查所有提醒相关权限是否完备
     */
    fun hasAllReminderPermissions(): Boolean {
        return hasNotificationPermission() && hasExactAlarmPermission()
    }
    
    /**
     * 获取权限状态摘要
     */
    fun getPermissionSummary(): PermissionSummary {
        return PermissionSummary(
            hasNotification = hasNotificationPermission(),
            hasExactAlarm = hasExactAlarmPermission(),
            isIgnoringBatteryOptimization = isIgnoringBatteryOptimizations()
        )
    }
}

/**
 * 权限状态摘要
 */
data class PermissionSummary(
    val hasNotification: Boolean,
    val hasExactAlarm: Boolean,
    val isIgnoringBatteryOptimization: Boolean
) {
    val hasAllRequired: Boolean
        get() = hasNotification && hasExactAlarm
    
    val hasAllOptimal: Boolean
        get() = hasAllRequired && isIgnoringBatteryOptimization
}
