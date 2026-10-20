package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.XiaoMaoTaiApp
import com.example.xiaomaotai.ui.theme.BrandViolet
import com.example.xiaomaotai.ui.theme.Space

/**
 * 排序与列表二级页。
 *
 * 目前只有一个开关（自动排序过期事件）。刻意保持独立页面，
 * 而不塞进界面风格——两者语义无关，混在一起会让人找不到。
 */
@Composable
fun SortingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { XiaoMaoTaiApp.settings(context) }
    var autoSort by remember { mutableStateOf(settings.isAutoSortExpiredEventsEnabled()) }

    SettingsSubPage(title = "排序与列表", onBack = onBack) {
        SettingsGroupLabel("排序规则")

        SettingsCard {
            SettingsSwitchRow(
                icon = Icons.Default.SwapVert,
                title = "自动排序过期事件",
                subtitle = if (autoSort) "按距离下次纪念日的天数升序，过期项排到末尾"
                           else "完全按你手动拖拽的顺序",
                checked = autoSort,
                iconTint = BrandViolet,
                onCheckedChange = {
                    autoSort = it
                    settings.setAutoSortExpiredEvents(it)
                }
            )
        }

        SettingsGroupLabel("怎么排序？")
        SettingsCard {
            Column(modifier = Modifier.padding(Space.lg)) {
                Text(
                    text = "开启后",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Space.xxs))
                Text(
                    text = "列表按「还要等多少天」自动从近到远排，天数相同的保持你手动排的顺序。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Space.md))
                Text(
                    text = "关闭后",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Space.xxs))
                Text(
                    text = "首页列表完全按「排序」页里拖拽的结果显示，不会自动变动。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
