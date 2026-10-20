package com.example.xiaomaotai.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.PermissionManager
import com.example.xiaomaotai.XiaoMaoTaiApp
import com.example.xiaomaotai.ui.theme.BrandBlue
import com.example.xiaomaotai.ui.theme.BrandMint
import com.example.xiaomaotai.ui.theme.Space

/**
 * 通知与权限二级页。
 *
 * 只放「系统级授权」：通知、精确闹钟、电池优化。
 * 应用内开关（常驻通知/隐藏任务/无障碍）都在「后台保活」页，
 * 因为它们的语义是「保活手段」而不是「权限」。
 */
@Composable
fun NotificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    var summary by remember { mutableStateOf(permissionManager.getPermissionSummary()) }
    var showAlarmGuide by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                summary = permissionManager.getPermissionSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubPage(title = "通知与权限", onBack = onBack) {
        SettingsGroupLabel("系统权限")

        SettingsCard {
            // 通知权限
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "允许通知",
                subtitle = if (summary.hasNotification) "已启用" else "未启用，提醒无法送达",
                iconTint = if (summary.hasNotification) BrandBlue
                           else MaterialTheme.colorScheme.error,
                onClick = {
                    safeStartSettings(
                        context,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                },
                trailing = {
                    Switch(
                        checked = summary.hasNotification,
                        onCheckedChange = {
                            safeStartSettings(
                                context,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    )
                }
            )

            SettingsDivider()

            // 精确闹钟
            SettingsRow(
                icon = Icons.Default.Alarm,
                title = "精确闹钟权限",
                subtitle = if (summary.hasExactAlarm) "已开启" else "未开启，提醒可能延迟",
                iconTint = if (summary.hasExactAlarm) BrandBlue
                           else MaterialTheme.colorScheme.error,
                onClick = {
                    if (summary.hasExactAlarm) {
                        showAlarmGuide = !showAlarmGuide
                    } else {
                        openAlarmSettings(context, permissionManager)
                    }
                },
                trailing = {
                    Switch(
                        checked = summary.hasExactAlarm,
                        onCheckedChange = { openAlarmSettings(context, permissionManager) }
                    )
                }
            )

            // 未开启时才展示厂商引导
            AnimatedVisibility(visible = !summary.hasExactAlarm || showAlarmGuide) {
                Column(modifier = Modifier.padding(horizontal = Space.lg)) {
                    VendorAlarmGuide(permissionManager)
                }
            }

            SettingsDivider()

            // 电池优化
            SettingsRow(
                icon = Icons.Default.PhoneAndroid,
                title = "允许后台运行",
                subtitle = if (summary.isIgnoringBatteryOptimization)
                    "电池优化已关闭" else "未允许，系统可能清理后台",
                iconTint = if (summary.isIgnoringBatteryOptimization) BrandMint
                           else MaterialTheme.colorScheme.error,
                onClick = { openBatterySettings(context, permissionManager) },
                trailing = {
                    Switch(
                        checked = summary.isIgnoringBatteryOptimization,
                        onCheckedChange = { openBatterySettings(context, permissionManager) }
                    )
                }
            )
        }

        SettingsGroupLabel("说明")
        SettingsCard {
            Column(modifier = Modifier.padding(Space.lg)) {
                Text(
                    text = "这三项都开启后，纪念日提醒才能准时送达，即使 App 已退出。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 厂商引导：只在需要时展开，避免平时占屏 */
@Composable
private fun VendorAlarmGuide(permissionManager: PermissionManager) {
    val context = LocalContext.current
    val isChinese = permissionManager.isChineseDevice()
    val vendor = permissionManager.getDeviceVendorName()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.md)
    ) {
        Text(
            text = if (isChinese) "检测到 $vendor 设备，可尝试以下入口：" else "可尝试以下入口：",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.sm)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            AssistChip(
                onClick = { openAlarmSettings(context, permissionManager) },
                label = { Text("标准权限设置", fontSize = 12.sp) }
            )
            if (isChinese) {
                AssistChip(
                    onClick = {
                        try {
                            safeStartSettings(
                                context,
                                permissionManager.getVendorSpecificAlarmSettingsIntent()
                            )
                        } catch (e: Exception) {
                            fallbackToAppDetail(context)
                        }
                    },
                    label = { Text("$vendor 权限管理", fontSize = 12.sp) }
                )
            }
        }

        if (isChinese) {
            Spacer(modifier = Modifier.height(Space.sm))
            val guide = when {
                permissionManager.isVivoDevice() -> "设置 → 应用与权限 → 权限管理 → 闹钟"
                permissionManager.isXiaomiDevice() -> "设置 → 应用设置 → 应用权限 → 闹钟"
                permissionManager.isOppoDevice() -> "设置 → 应用管理 → 应用权限 → 闹钟"
                permissionManager.isHuaweiDevice() -> "设置 → 应用和服务 → 权限管理 → 闹钟"
                else -> "在系统设置中搜索「闹钟权限」"
            }
            Text(
                text = guide,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

// ---------- 跳转辅助 ----------

private fun openAlarmSettings(context: Context, pm: PermissionManager) {
    try {
        val intent = if (pm.isChineseDevice()) pm.getVendorSpecificAlarmSettingsIntent()
                     else pm.getExactAlarmSettingsIntent()
        safeStartSettings(context, intent)
    } catch (e: Exception) {
        try {
            safeStartSettings(context, pm.getExactAlarmSettingsIntent())
        } catch (e2: Exception) {
            fallbackToAppDetail(context)
        }
    }
}

private fun openBatterySettings(context: Context, pm: PermissionManager) {
    try {
        safeStartSettings(context, pm.getBatteryOptimizationSettingsIntent())
    } catch (e: Exception) {
        fallbackToAppDetail(context)
    }
}

private fun fallbackToAppDetail(context: Context) {
    try {
        safeStartSettings(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    } catch (e: Exception) {
        safeStartSettings(context, Intent(Settings.ACTION_SETTINGS))
    }
}

/**
 * 统一的安全跳转：跳转前打标记，避免「隐藏最近任务」开启时被 onStop 回收。
 */
internal fun safeStartSettings(context: Context, intent: Intent) {
    try {
        if (XiaoMaoTaiApp.settings(context).isHideRecentTaskEnabled()) {
            com.example.xiaomaotai.MainActivity.isNavigatingToSettings = true
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        com.example.xiaomaotai.MainActivity.isNavigatingToSettings = false
        throw e
    }
}
