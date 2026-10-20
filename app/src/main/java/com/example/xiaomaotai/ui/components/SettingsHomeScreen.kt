package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.AppInfo
import com.example.xiaomaotai.PermissionManager
import com.example.xiaomaotai.ReminderManager
import com.example.xiaomaotai.ui.theme.BrandBlue
import com.example.xiaomaotai.ui.theme.BrandMint
import com.example.xiaomaotai.ui.theme.BrandViolet
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Space
import com.example.xiaomaotai.ui.theme.UiStyle

/**
 * 设置根页：只做「分类入口」，不直接放开关。
 *
 * 设计取舍：设置项从 10 个散卡改成 5 个分类入口后，
 * 用户在根页一屏就能扫完所有类别，具体开关进二级页再调。
 * 每个入口都带一句状态摘要，不进去也知道当前是什么状态。
 */
@Composable
fun SettingsHomeScreen(
    uiStyleId: String,
    syncConfigured: Boolean,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsRoute) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    var summary by remember { mutableStateOf(permissionManager.getPermissionSummary()) }
    val settings = remember { com.example.xiaomaotai.XiaoMaoTaiApp.settings(context) }

    // 页面回到前台时刷新权限状态（用户可能刚从系统设置页返回）
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

    // 后台保活类开关的开启数量
    var keepAliveCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val list = listOf(
            permissionManager.isIgnoringBatteryOptimizations(),
            settings.isPersistentNotificationEnabled(),
            settings.isHideRecentTaskEnabled()
        )
        keepAliveCount = list.count { it }
    }

    val version = remember { AppInfo.version(context) }
    val styleTitle = UiStyle.fromId(uiStyleId).title

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部大标题
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.screenH)
                .padding(top = Space.lg, bottom = Space.md)
        ) {
            Text(
                text = "设置",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.6).sp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenH)
                .padding(top = Space.xxs, bottom = Space.lg)
        ) {
            // ---------- 界面风格 ----------
            SettingsGroupLabel("界面风格")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "界面风格",
                    subtitle = styleTitle,
                    iconTint = BrandViolet,
                    onClick = { onNavigate(SettingsRoute.UiStyle) }
                )
            }

            // ---------- 通知与权限 ----------
            SettingsGroupLabel("通知与权限")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "通知与权限",
                    subtitle = buildString {
                        append(if (summary.hasNotification) "通知已开启" else "通知未开启")
                        append(" · ")
                        append(if (summary.hasExactAlarm) "闹钟正常" else "闹钟未授权")
                    },
                    iconTint = BrandBlue,
                    onClick = { onNavigate(SettingsRoute.Notification) }
                )
            }

            // ---------- 后台保活 ----------
            SettingsGroupLabel("后台保活")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Shield,
                    title = "后台保活",
                    subtitle = if (keepAliveCount == 0) "尚未开启任何保活项"
                    else "已开启 $keepAliveCount / 3 项",
                    iconTint = BrandMint,
                    onClick = { onNavigate(SettingsRoute.KeepAlive) }
                )
            }

            // ---------- 备份与同步 ----------
            SettingsGroupLabel("备份与同步")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Cloud,
                    title = "备份与同步",
                    subtitle = if (syncConfigured) "WebDAV 已配置" else "尚未配置 WebDAV",
                    iconTint = BrandBlue,
                    onClick = { onNavigate(SettingsRoute.WebDav) }
                )
            }

            // ---------- 排序与列表 ----------
            SettingsGroupLabel("排序与列表")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.SwapVert,
                    title = "排序与列表",
                    subtitle = if (settings.isAutoSortExpiredEventsEnabled())
                        "自动排序已开启" else "完全按手动排序",
                    iconTint = BrandViolet,
                    onClick = { onNavigate(SettingsRoute.Sorting) }
                )
            }

            // ---------- 关于 ----------
            SettingsGroupLabel("关于")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "v${version.name}",
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onNavigate(SettingsRoute.About) }
                )
            }

            Spacer(modifier = Modifier.height(Space.lg))

            // ---------- 提醒时间：一行紧凑说明，不占高度 ----------
            ReminderScheduleHint()

            Spacer(modifier = Modifier.height(Space.md))

            // ---------- 通知测试按钮 ----------
            TestNotificationButton()
        }
    }
}

/**
 * 提醒时间说明：压成两行小字。
 *
 * 原来用「• 标题 + 子行」三组，占近 140dp；这里改成半透明底的一行摘要，
 * 具体时刻用分隔符连起来，只保留必要的刻度信息。
 */
@Composable
private fun ReminderScheduleHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = Shape.small
            )
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(Space.sm))
        Column {
            Text(
                text = "提前 7 天 / 1 天 08:00 · 当天 00:00 / 08:00 / 12:00",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.xxs))
            Text(
                text = "当天新增的纪念日会在 30 秒后立即提醒",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 发送测试通知：根页底部的主行动 */
@Composable
private fun TestNotificationButton() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showToast by remember { mutableStateOf(false) }

    if (showToast) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1800)
            showToast = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                ReminderManager(context).sendImmediateTestNotification()
                showToast = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.MinTouchTarget),
            shape = Shape.small,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = "发送测试通知",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showToast) {
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = "测试通知已发送，请查看通知栏",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
