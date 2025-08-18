package com.example.xiaomaotai

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleOwner

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    var permissionSummary by remember { mutableStateOf(PermissionSummary(false, false, false)) }
    
    // 动态获取版本号
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // 手势返回处理
    BackHandler {
        onNavigateBack()
    }

    // 检查所有权限状态
    LaunchedEffect(Unit) {
        permissionSummary = permissionManager.getPermissionSummary()
    }
    
    // 当页面重新获得焦点时刷新权限状态
    DisposableEffect(Unit) {
        val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionSummary = permissionManager.getPermissionSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 主要内容区域 - 可滚动
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 120.dp), // 为底部版本信息和导航栏预留足够空间
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 简约顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // 通知权限设置项
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "允许通知",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (permissionSummary.hasNotification) "已启用" else "已禁用",
                            fontSize = 13.sp,
                            color = if (permissionSummary.hasNotification) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = permissionSummary.hasNotification,
                        onCheckedChange = { 
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
        
        // 精确闹钟权限设置 - 针对vivo优化
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "精确闹钟权限",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (permissionSummary.hasExactAlarm) "已开启" else "未开启，需要手动设置",
                            fontSize = 13.sp,
                            color = if (permissionSummary.hasExactAlarm) 
                                Color(0xFF4CAF50) 
                            else 
                                Color(0xFFF44336)
                        )
                    }
                    
                    Switch(
                        checked = permissionSummary.hasExactAlarm,
                        onCheckedChange = { },
                        enabled = false
                    )
                }
                
                // 如果权限未开启，显示多个设置选项
                if (!permissionSummary.hasExactAlarm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 检测设备制造商
                    val isChineseDevice = permissionManager.isChineseDevice()
                    val vendorName = permissionManager.getDeviceVendorName()
                    
                    if (isChineseDevice) {
                        Text(
                            text = "检测到${vendorName}设备，请尝试以下方式设置权限：",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "ℹ️ 提示：不同版本路径可能有差异，建议优先尝试搜索功能",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // 标准权限设置按钮
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = permissionManager.getExactAlarmSettingsIntent()
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 如果标准方式失败，尝试应用详情页
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("标准权限设置")
                    }
                    
                    // 国产设备专用设置按钮
                    if (isChineseDevice) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = permissionManager.getVendorSpecificAlarmSettingsIntent()
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // 备选方案
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${vendorName}权限管理")
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = permissionManager.getSystemAlarmSettingsIntent()
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("系统设置")
                        }
                        
                        // 国产设备的详细说明
                        Spacer(modifier = Modifier.height(8.dp))
                        val deviceInstructions = when {
                            permissionManager.isVivoDevice() -> 
                                "vivo/iQOO设备通用方法：\n1. 设置 → 应用与权限 → 权限管理 → 闹钟 → 添加小茅台\n2. 或在设置中直接搜索\"闹钟权限\""
                            permissionManager.isXiaomiDevice() -> 
                                "小米/红米设备通用方法：\n1. 设置 → 应用设置 → 应用权限 → 闹钟 → 添加小茅台\n2. 或在MIUI设置中搜索\"闹钟权限\""
                            permissionManager.isOppoDevice() -> 
                                "OPPO/一加设备通用方法：\n1. 设置 → 应用管理 → 应用权限 → 闹钟 → 添加小茅台\n2. 或在ColorOS设置中搜索\"闹钟\""
                            permissionManager.isHuaweiDevice() -> 
                                "华为/荣耀设备通用方法：\n1. 设置 → 应用和服务 → 权限管理 → 闹钟 → 添加小茅台\n2. 或在HarmonyOS/EMUI设置中搜索\"闹钟权限\""
                            else -> 
                                "国产设备通用方法：\n1. 在系统设置中搜索\"闹钟权限\"或\"应用权限\"\n2. 或在\"手机管家\"、\"安全中心\"中找权限管理"
                        }
                        
                        Text(
                            text = deviceInstructions,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                        
                        // 通用备选方案
                        val isChineseDevice = permissionManager.isVivoDevice() || 
                                            permissionManager.isXiaomiDevice() || 
                                            permissionManager.isOppoDevice() || 
                                            permissionManager.isHuaweiDevice()
                        if (isChineseDevice) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 如果以上路径找不到，请在设置中直接搜索“闹钟”或“权限”关键词",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
        
        // 电池优化白名单设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "电池优化白名单",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (permissionSummary.isIgnoringBatteryOptimization) 
                                "已加入白名单" 
                            else 
                                "未加入，需要手动设置（重要）",
                            fontSize = 13.sp,
                            color = if (permissionSummary.isIgnoringBatteryOptimization) 
                                Color(0xFF4CAF50) 
                            else 
                                Color(0xFFF44336)
                        )
                    }
                    
                    Switch(
                        checked = permissionSummary.isIgnoringBatteryOptimization,
                        onCheckedChange = { 
                            // 点击Switch时跳转到电池优化设置
                            val permissionManager = PermissionManager(context)
                            try {
                                val intent = permissionManager.getBatteryOptimizationSettingsIntent()
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 如果无法打开设置页面，尝试打开通用电池优化设置
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallbackIntent)
                                } catch (e2: Exception) {
                                    // 最后的备用方案：打开应用设置页面
                                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(appSettingsIntent)
                                }
                            }
                        }
                    )
                }
            }
        }


        
        // 提醒时间说明 - 重新设计为提示区域样式
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "提醒时间",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Text(
                    text = "系统会在以下时间自动发送提醒通知",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                // 提醒时间列表 - 统一样式
                val reminderTimes = listOf(
                    "提前7天" to "上午 9:00",
                    "提前1天" to "上午 9:00", 
                    "当天" to "上午 9:00"
                )
                
                reminderTimes.forEach { (time, schedule) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• $time",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = schedule,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // 特殊说明
                Text(
                    text = "当天添加的纪念日会在30秒后立即提醒",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // 通知测试功能
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "通知测试",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Text(
                    text = "点击下方按钮立即发送测试通知",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                Button(
                    onClick = {
                        val reminderManager = ReminderManager(context)
                        reminderManager.sendImmediateTestNotification()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "立即发送",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // 版本信息 - 跟随页面内容底部
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "小茅台 V$versionName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Normal
            )
        }
        } // 结束主要内容区域的Column
    } // 结束Box
}
