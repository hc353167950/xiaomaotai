package com.example.xiaomaotai.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.NotificationAccessibilityService
import com.example.xiaomaotai.PersistentNotificationService
import com.example.xiaomaotai.XiaoMaoTaiApp
import com.example.xiaomaotai.ui.theme.BrandMint
import com.example.xiaomaotai.ui.theme.BrandViolet
import com.example.xiaomaotai.ui.theme.Space

/**
 * 后台保活二级页。
 *
 * 三项都是「让 App 在后台活得更久」的手段，语义同族，所以放一起：
 * - 常驻通知：前台服务，保活强度最高
 * - 无障碍保活：借系统级服务刷新天数
 * - 隐藏最近任务：降低被用户误杀的概率
 */
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { XiaoMaoTaiApp.settings(context) }

    var persistentNotification by remember { mutableStateOf(settings.isPersistentNotificationEnabled()) }
    var hideRecentTask by remember { mutableStateOf(settings.isHideRecentTaskEnabled()) }
    var accessibilityEnabled by remember {
        mutableStateOf(NotificationAccessibilityService.isServiceEnabled(context))
    }

    // 从系统无障碍页返回时刷新状态
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = NotificationAccessibilityService.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubPage(title = "后台保活", onBack = onBack) {
        SettingsGroupLabel("保活手段")

        SettingsCard {
            SettingsSwitchRow(
                icon = Icons.Default.NotificationsActive,
                title = "常驻通知",
                subtitle = if (persistentNotification)
                    "通知栏常驻，保活强度最高" else "已关闭",
                checked = persistentNotification,
                iconTint = BrandMint,
                onCheckedChange = { enabled ->
                    persistentNotification = enabled
                    settings.setPersistentNotification(enabled)
                    if (enabled) PersistentNotificationService.startService(context)
                    else PersistentNotificationService.stopService(context)
                }
            )

            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.VisibilityOff,
                title = "隐藏最近任务",
                subtitle = if (hideRecentTask)
                    "不出现在最近任务列表" else "已关闭",
                checked = hideRecentTask,
                iconTint = BrandViolet,
                onCheckedChange = { enabled ->
                    hideRecentTask = enabled
                    settings.setHideRecentTask(enabled)
                    applyHideRecentTaskSetting(context, enabled)
                }
            )

            SettingsDivider()

            SettingsRow(
                icon = Icons.Default.Build,
                title = "无障碍保活",
                subtitle = if (accessibilityEnabled)
                    "已启用，通知栏天数实时刷新" else "未启用，天数可能不实时更新",
                iconTint = if (accessibilityEnabled) BrandMint
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { openAccessibilitySettings(context) },
                trailing = {
                    Switch(
                        checked = accessibilityEnabled,
                        onCheckedChange = { openAccessibilitySettings(context) }
                    )
                }
            )
        }

        SettingsGroupLabel("说明")
        SettingsCard {
            Column(modifier = Modifier.padding(Space.lg)) {
                Text(
                    text = "常驻通知使用前台服务，最稳；隐藏最近任务只能降低被误杀的概率；" +
                        "无障碍服务用于让通知栏天数实时刷新，可选。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartSettings(context, intent)
    } catch (e: Exception) {
        // 少数设备没有独立的无障碍页，退到应用详情
        try {
            safeStartSettings(
                context,
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            )
        } catch (e2: Exception) {
            android.util.Log.e("KeepAliveScreen", "无法打开无障碍设置: ${e2.message}")
        }
    }
}

/**
 * 立即应用隐藏最近任务设置。
 *
 * setExcludeFromRecents 只影响「当前任务栈」，
 * 所以必须在开关切换的瞬间调用一次，等待下次进后台才生效就太晚了。
 */
private fun applyHideRecentTaskSetting(context: Context, enable: Boolean) {
    try {
        context as? android.app.Activity ?: return
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                tasks[0].setExcludeFromRecents(enable)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("KeepAliveScreen", "应用隐藏最近任务设置失败: ${e.message}")
    }
}
