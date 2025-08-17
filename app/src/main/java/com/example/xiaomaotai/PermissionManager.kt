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
     * 增强版本，包含更详细的权限检测
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
     * 检测设备制造商
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    /**
     * 是否为vivo设备
     */
    fun isVivoDevice(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("vivo") || manufacturer.contains("iqoo")
    }
    
    /**
     * 是否为小米设备
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
    }
    
    /**
     * 是否为OPPO设备
     */
    fun isOppoDevice(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("oppo") || manufacturer.contains("oneplus")
    }
    
    /**
     * 是否为华为设备
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
    }
    
    /**
     * 是否为三星设备
     */
    fun isSamsungDevice(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("samsung")
    }
    
    /**
     * 是否为国产手机（可能需要特殊处理）
     */
    fun isChineseDevice(): Boolean {
        return isVivoDevice() || isXiaomiDevice() || isOppoDevice() || isHuaweiDevice()
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
     * 针对vivo等国产手机优化多种跳转方式
     */
    fun getExactAlarmSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 优先尝试标准的精确闹钟设置页面
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
     * 获取vivo专用的闹钟权限设置Intent
     * 当标准方式无效时使用
     */
    fun getVivoAlarmSettingsIntent(): Intent {
        return try {
            // 尝试vivo的闹钟权限管理页面
            Intent().apply {
                setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                putExtra("packageName", context.packageName)
            }
        } catch (e: Exception) {
            try {
                // 备选方案：vivo的应用管理页面
                Intent().apply {
                    setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    putExtra("packagename", context.packageName)
                }
            } catch (e2: Exception) {
                // 最终备选：系统应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }
    
    /**
     * 获取小米专用的闹钟权限设置Intent
     */
    fun getXiaomiAlarmSettingsIntent(): Intent {
        return try {
            // 尝试小米的权限管理页面
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            }
        } catch (e: Exception) {
            try {
                // 备选方案：小米的应用管理
                Intent().apply {
                    setClassName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity")
                    putExtra("package_name", context.packageName)
                }
            } catch (e2: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }
    
    /**
     * 获取OPPO专用的闹钟权限设置Intent
     */
    fun getOppoAlarmSettingsIntent(): Intent {
        return try {
            // 尝试OPPO的权限管理页面
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionManagerActivity")
                putExtra("package_name", context.packageName)
            }
        } catch (e: Exception) {
            try {
                // 备选方案：OPPO的应用管理
                Intent().apply {
                    setClassName("com.oppo.safe", "com.oppo.safe.permission.PermissionAppListActivity")
                }
            } catch (e2: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }
    
    /**
     * 获取华为专用的闹钟权限设置Intent
     */
    fun getHuaweiAlarmSettingsIntent(): Intent {
        return try {
            // 尝试华为的权限管理页面
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity")
                putExtra("package_name", context.packageName)
            }
        } catch (e: Exception) {
            try {
                // 备选方案：华为的应用管理
                Intent().apply {
                    setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                }
            } catch (e2: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }
    
    /**
     * 根据设备制造商获取对应的专用权限设置Intent
     */
    fun getVendorSpecificAlarmSettingsIntent(): Intent {
        return when {
            isVivoDevice() -> getVivoAlarmSettingsIntent()
            isXiaomiDevice() -> getXiaomiAlarmSettingsIntent()
            isOppoDevice() -> getOppoAlarmSettingsIntent()
            isHuaweiDevice() -> getHuaweiAlarmSettingsIntent()
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 获取系统闹钟应用列表Intent
     * 用于引导用户在系统闹钟设置中查找APP
     */
    fun getSystemAlarmSettingsIntent(): Intent {
        return try {
            // 尝试打开系统闹钟设置
            Intent(Settings.ACTION_SETTINGS).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * 获取设备制造商名称（中文）
     */
    fun getDeviceVendorName(): String {
        return when {
            isVivoDevice() -> "vivo/iQOO"
            isXiaomiDevice() -> "小米/红米"
            isOppoDevice() -> "OPPO/一加"
            isHuaweiDevice() -> "华为/荣耀"
            isSamsungDevice() -> "三星"
            else -> Build.MANUFACTURER
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
