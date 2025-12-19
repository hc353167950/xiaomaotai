package com.example.xiaomaotai

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.xiaomaotai.ui.theme.XiaoMaoTaiTheme
import com.example.xiaomaotai.ui.components.ForgotPasswordScreen
import com.example.xiaomaotai.ui.components.ProfileScreen
import com.example.xiaomaotai.ui.components.EventItem
import com.example.xiaomaotai.ui.components.EventDialog
import com.example.xiaomaotai.ui.components.CardStyleManager
import com.example.xiaomaotai.ui.components.GlobalLoadingDialog
import com.example.xiaomaotai.ui.components.calculateDaysAfter
import com.example.xiaomaotai.ui.components.SortScreen
import com.example.xiaomaotai.ui.components.MahjongScoreScreen
import com.example.xiaomaotai.ui.components.MahjongHistoryScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner



class MainActivity : ComponentActivity() {
    
    companion object {
        // 全局会话级别的提示关闭状态 - 一旦关闭在应用重启前不再显示
        private var isLoginTipDismissedForSession = false
        
        fun dismissLoginTip() {
            isLoginTipDismissedForSession = true
        }
        
        fun isLoginTipDismissed(): Boolean {
            return isLoginTipDismissedForSession
        }
        
        fun resetLoginTipOnLogin() {
            isLoginTipDismissedForSession = true
        }

        // 标记：是否正在跳转到系统设置页（临时禁用隐藏最近任务）
        var isNavigatingToSettings = false
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Permission", "Notification permission granted.")
        } else {
            Log.d("Permission", "Notification permission denied.")
        }
    }
    
    // 精确闹钟权限请求结果处理
    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val permissionManager = PermissionManager(this)
        if (permissionManager.hasExactAlarmPermission()) {
            Log.d("Permission", "Exact alarm permission granted after request.")
        } else {
            Log.d("Permission", "Exact alarm permission still not granted.")
            // 如果标准方式失败，尝试vivo专用方式
            if (permissionManager.isVivoDevice()) {
                showVivoAlarmPermissionDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            XiaoMaoTaiTheme {
                val context = LocalContext.current
                val dataManager = remember { DataManager(context) }

                LaunchedEffect(Unit) {
                    dataManager.initializeLocalData()

                    // 检查并应用隐藏最近任务设置
                    handleHideRecentTask(dataManager)

                    // 检查并启动常驻通知服务
                    handlePersistentNotification(dataManager)
                }

                MainApp(dataManager = dataManager)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val dataManager = DataManager(this)

        // 如果开启了隐藏最近任务，在APP进入后台时从最近任务列表中移除（不杀掉APP）
        // 但如果正在跳转到系统设置页，则跳过此操作，避免APP被回收
        if (dataManager.isHideRecentTaskEnabled() && !isNavigatingToSettings) {
            moveTaskToBack(true)
        }
    }

    override fun onResume() {
        super.onResume()
        val dataManager = DataManager(this)

        // 从设置页返回，重置标记
        isNavigatingToSettings = false

        // 每次恢复时检查设置
        handleHideRecentTask(dataManager)
        handlePersistentNotification(dataManager)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 只在Activity真正退出时停止常驻通知服务
        // isFinishing为true表示Activity正在结束（用户退出），为false表示配置更改（如旋转屏幕）
        if (isFinishing) {
            val dataManager = DataManager(this)
            // 如果开启了常驻通知，在APP退出时停止服务
            if (dataManager.isPersistentNotificationEnabled()) {
                PersistentNotificationService.stopService(this)
                Log.d("MainActivity", "APP退出，停止常驻通知服务")
            }
        }
    }

    /**
     * 处理隐藏最近任务设置
     * 动态设置Activity的excludeFromRecents属性
     */
    private fun handleHideRecentTask(dataManager: DataManager) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (dataManager.isHideRecentTaskEnabled()) {
                // 开启隐藏：设置excludeFromRecents标志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 通过反射设置excludeFromRecents
                    val tasks = activityManager.appTasks
                    if (tasks.isNotEmpty()) {
                        tasks[0].setExcludeFromRecents(true)
                    }
                }
            } else {
                // 关闭隐藏：取消excludeFromRecents标志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val tasks = activityManager.appTasks
                    if (tasks.isNotEmpty()) {
                        tasks[0].setExcludeFromRecents(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "设置隐藏最近任务失败: ${e.message}")
        }
    }

    /**
     * 处理常驻通知设置
     */
    private fun handlePersistentNotification(dataManager: DataManager) {
        if (dataManager.isPersistentNotificationEnabled()) {
            // 启动常驻通知服务
            PersistentNotificationService.startService(this)
        } else {
            // 停止常驻通知服务
            PersistentNotificationService.stopService(this)
        }
    }

    private fun checkAndRequestPermissions() {
        // 只检查通知权限，不自动请求精准闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permission", "Notification permission already granted.")
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    private fun checkExactAlarmPermission(permissionManager: PermissionManager) {
        val hasPermission = permissionManager.hasExactAlarmPermission()
        val androidVersion = Build.VERSION.SDK_INT
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
        
        Log.d("Permission", "=== 精确闹钟权限检查 ===")
        Log.d("Permission", "Android版本: $androidVersion")
        Log.d("Permission", "设备信息: $deviceInfo")
        Log.d("Permission", "当前权限状态: $hasPermission")
        
        if (androidVersion >= Build.VERSION_CODES.S) {
            if (!hasPermission) {
                Log.d("Permission", "精确闹钟权限未授权，开始请求权限")
                requestExactAlarmPermission(permissionManager)
            } else {
                Log.d("Permission", "精确闹钟权限已授权，但为了确保在系统列表中显示，仍然发起一次请求")
                // 即使有权限，也发起一次请求确保在系统权限列表中显示
                requestExactAlarmPermission(permissionManager)
            }
        } else {
            Log.d("Permission", "Android 12以下，无需精确闹钟权限")
        }
    }
    
    private fun requestExactAlarmPermission(permissionManager: PermissionManager) {
        try {
            val intent = permissionManager.getExactAlarmSettingsIntent()
            exactAlarmPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("Permission", "无法打开精确闹钟设置页面: ${e.message}")
            // 如果是vivo设备，尝试专用方式
            if (permissionManager.isVivoDevice()) {
                showVivoAlarmPermissionDialog()
            }
        }
    }
    
    private fun showVivoAlarmPermissionDialog() {
        // vivo设备专用的权限请求方式
        val permissionManager = PermissionManager(this)
        try {
            val intent = permissionManager.getVivoAlarmSettingsIntent()
            exactAlarmPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("Permission", "无法打开vivo权限设置页面: ${e.message}")
        }
    }
}

@Composable
fun MainApp(dataManager: DataManager) {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf<User?>(null) }
    var showOfflineMessage by remember { mutableStateOf(true) }
    var showNotificationPrompt by remember { mutableStateOf(false) }
    var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") }
    var forgotPasswordSource by remember { mutableStateOf("login") } // 跟踪忘记密码页面的来源
    var refreshKey by remember { mutableStateOf(0) }
    var sortScreenEvents by remember { mutableStateOf<List<Event>>(emptyList()) } // 保存传递给排序页的事件列表
    
    // 双击返回退出相关状态
    var backPressedTime by remember { mutableStateOf(0L) }
    var showExitToast by remember { mutableStateOf(false) }
    
    val permissionManager = remember { PermissionManager(context) }

    // 检查通知权限
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    LaunchedEffect(Unit) {
        dataManager.initializeLocalData()
        currentUser = dataManager.getCurrentUser()
        
        // 检查是否需要显示通知权限提示
        if (!hasNotificationPermission() && dataManager.shouldShowNotificationPrompt()) {
            showNotificationPrompt = true
        }
    }
    
    // 处理首页、晃晃、我的页面的返回按键
    if (currentScreen in listOf("home", "mahjong", "profile")) {
        BackHandler {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                // 2秒内第二次按返回键，退出应用
                (context as? Activity)?.finish()
            } else {
                // 第一次按返回键，显示提示
                backPressedTime = currentTime
                showExitToast = true
            }
        }
    }
    
    Scaffold(
        bottomBar = {
            if (currentScreen != "sort" && currentScreen != "mahjong_history") {
                NavigationBar(
                    modifier = Modifier.height(64.dp) // 缩小底部导航栏高度
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                        label = { Text("首页") },
                        selected = currentScreen == "home",
                        onClick = { currentScreen = "home" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "晃晃") },
                        label = { Text("晃晃") },
                        selected = currentScreen == "mahjong",
                        onClick = { currentScreen = "mahjong" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                        label = { Text("我的") },
                        selected = currentScreen == "profile",
                        onClick = { currentScreen = "profile" }
                    )
                }
            }
        }
    ) { paddingValues ->
        // 导航逻辑
        when (currentScreen) {
            "home" -> HomeScreen(
                dataManager = dataManager,
                loginState = dataManager.isLoggedIn(),
                modifier = Modifier.padding(paddingValues),
                onNavigateToSort = { events ->
                    sortScreenEvents = events // 保存事件列表
                    currentScreen = "sort"
                },
                refreshKey = refreshKey
            )
            "profile" -> ProfileScreen(
                dataManager = dataManager,
                currentUser = currentUser,
                onLogin = { user ->
                    currentUser = user
                },
                onLogout = {
                    dataManager.logout()
                    currentUser = null
                },
                onNavigateToForgotPassword = {
                    forgotPasswordSource = "profile"
                    currentScreen = "forgot_password"
                },
                onNavigateToSettings = {
                    currentScreen = "settings"
                },
                onNavigateToLogin = {
                    currentScreen = "login"
                },
                onNavigateToRegister = {
                    currentScreen = "register"
                },
                onBack = {
                    // 我的页面返回手势应该退出到桌面，不处理返回事件让系统处理
                }
            )
            "settings" -> SettingsScreen(
                onNavigateBack = {
                    currentScreen = "profile"
                }
            )
            "forgot_password" -> ForgotPasswordScreen(
                dataManager = dataManager,
                onBack = {
                    currentScreen = forgotPasswordSource
                },
                onResetSuccess = {
                    currentScreen = forgotPasswordSource
                }
            )
            "login" -> {
                // 独立的登录页面路由 - 使用ProfileScreen内部的LoginScreen
                ProfileScreen(
                    dataManager = dataManager,
                    currentUser = null,
                    onLogin = { user ->
                        currentUser = user
                        currentScreen = "profile"
                    },
                    onLogout = { },
                    onNavigateToForgotPassword = {
                        forgotPasswordSource = "login"
                        currentScreen = "forgot_password"
                    },
                    onNavigateToSettings = {
                        currentScreen = "settings"
                    },
                    onNavigateToLogin = { },
                    onNavigateToRegister = {
                        currentScreen = "register"
                    },
                    onBack = {
                        currentScreen = "profile"
                    }
                )
            }
            "register" -> {
                // 独立的注册页面路由 - 使用ProfileScreen内部的RegisterScreen
                ProfileScreen(
                    dataManager = dataManager,
                    currentUser = null,
                    onLogin = { user ->
                        currentUser = user
                        currentScreen = "profile"
                    },
                    onLogout = { },
                    onNavigateToForgotPassword = { },
                    onNavigateToSettings = {
                        currentScreen = "settings"
                    },
                    onNavigateToLogin = {
                        currentScreen = "login"
                    },
                    onNavigateToRegister = { },
                    onBack = {
                        currentScreen = "profile"
                    }
                )
            }
            "sort" -> {
                SortScreen(
                    dataManager = dataManager,
                    initialEvents = sortScreenEvents, // 传递首页的事件列表
                    onDone = {
                        refreshKey += 1
                        currentScreen = "home"
                    }
                )
            }
            "mahjong" -> {
                MahjongScoreScreen(
                    dataManager = dataManager,
                    onNavigateToHistory = {
                        currentScreen = "mahjong_history"
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            "mahjong_history" -> {
                MahjongHistoryScreen(
                    dataManager = dataManager,
                    onNavigateBack = {
                        currentScreen = "mahjong"
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    // 退出提示Toast（覆盖层，需在MainApp作用域内）
    if (showExitToast) {
        LaunchedEffect(showExitToast) {
            kotlinx.coroutines.delay(2000)
            showExitToast = false
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = "再次返回退出到桌面",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 14.sp
                )
            }
        }
    }

    // 首次启动时检查精确闹钟权限
    LaunchedEffect(Unit) {
        if (!permissionManager.hasExactAlarmPermission()) {
            showExactAlarmPermissionDialog = true
        }
    }
    
    // 精确闹钟权限请求对话框
    if (showExactAlarmPermissionDialog) {
        ExactAlarmPermissionDialog(
            onConfirm = {
                showExactAlarmPermissionDialog = false
                val intent = permissionManager.getExactAlarmSettingsIntent()
                context.startActivity(intent)
            },
            onDismiss = {
                showExactAlarmPermissionDialog = false
            }
        )
    }
}

@Composable
fun ExactAlarmPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "开启精确提醒权限",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "为了确保纪念日提醒能够准时送达，即使在APP退出后也能正常工作，需要开启以下权限：",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "• 精确闹钟权限 - 确保提醒准时触发",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• 电池优化白名单 - 防止系统杀死提醒功能",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击「去设置」将跳转到系统设置页面进行授权。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "去设置",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "稍后设置",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    )
}

@Composable
fun HomeScreen(
    dataManager: DataManager,
    loginState: Boolean,
    modifier: Modifier = Modifier,
    onNavigateToSort: (List<Event>) -> Unit, // 修改为传递事件列表
    refreshKey: Int
) {
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var internalRefreshKey by remember { mutableStateOf(0) }
    var forgotPasswordSource by remember { mutableStateOf("profile") }
    var showNotificationPrompt by remember { mutableStateOf(false) }
    var sortScreenEvents by remember { mutableStateOf<List<Event>>(emptyList()) } // 保存传递给排序页的事件列表
    var dragSortEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var sortOrder by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // 添加缺失的状态变量
    var isDragSortMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 提取排序逻辑为独立函数，确保在所有地方都应用相同的排序规则
    fun applySortLogic(eventsList: List<Event>): List<Event> {
        return if (dataManager.isAutoSortExpiredEventsEnabled()) {
            // 开启了自动排序：未过期事件在前，已过期事件在后（仅在内存中，不改数据库）
            val (expiredEvents, upcomingEvents) = eventsList.partition { event ->
                // 使用缓存的天数判断是否过期（负数表示已过期）
                val days = event.cachedDays ?: 0L
                days < 0
            }

            // 未过期事件：按剩余天数升序排序（天数越少越靠前），天数相同时按sortOrder降序
            val sortedUpcoming = upcomingEvents.sortedWith(
                compareBy<Event> { it.cachedDays ?: 0L }  // 使用缓存的天数，不重新计算！
                    .thenByDescending { it.sortOrder }
            )

            // 过期事件：按sortOrder降序排序，保持用户手动排序
            val sortedExpired = expiredEvents.sortedByDescending { it.sortOrder }

            val sorted = sortedUpcoming + sortedExpired

            Log.d("HomeScreen", "Applied auto-sort: ${eventsList.size} total (upcoming: ${upcomingEvents.size}, expired: ${expiredEvents.size})")
            sorted
        } else {
            // 关闭了自动排序：完全按照 sortOrder 降序排列，不区分是否过期
            val sorted = eventsList.sortedByDescending { it.sortOrder }
            Log.d("HomeScreen", "Applied normal sort: ${eventsList.size} events")
            sorted
        }
    }

    // 监听生命周期，当APP从后台恢复时刷新数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从后台恢复时，重新计算天数
                android.util.Log.d("HomeScreen", "APP从后台恢复，刷新天数数据")
                scope.launch {
                    try {
                        // 重新计算每个事件的缓存天数
                        val updatedEvents = events.map { ev ->
                            ev.apply {
                                cachedDays = com.example.xiaomaotai.ui.components.calculateDaysAfter(eventDate).second
                                android.util.Log.d("HomeScreen", "刷新天数: $eventName ($eventDate) = $cachedDays 天")
                            }
                        }
                        // 重新应用排序逻辑
                        events = applySortLogic(updatedEvents)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "刷新天数失败", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /**
     * 判断事件今年的日期是否已过（仅用于显示排序，不修改数据库）
     */
    fun isEventPassedThisYear(eventDate: String, today: java.time.LocalDate): Boolean {
        return try {
            when {
                // 农历事件：计算距离下次的天数
                eventDate.startsWith("lunar:") -> {
                    val lunarDatePart = eventDate.removePrefix("lunar:")
                    val daysBetween = when {
                        lunarDatePart.contains("-L") -> {
                            val parts = lunarDatePart.split("-")
                            if (parts.size >= 3) {
                                val year = parts[0].toInt()
                                val monthPart = parts[1]
                                val lunarDay = parts[2].toInt()
                                val actualMonth = monthPart.substring(1).toInt()
                                LunarCalendarHelper.calculateLunarCountdown(year, actualMonth, lunarDay, true, today)
                            } else 1L
                        }
                        lunarDatePart.contains("--") -> {
                            val corrected = lunarDatePart.replace("--", "-")
                            val parts = corrected.split("-")
                            if (parts.size >= 3) {
                                val year = parts[0].toInt()
                                val lunarMonth = parts[1].toInt()
                                val lunarDay = parts[2].toInt()
                                LunarCalendarHelper.calculateLunarCountdown(year, lunarMonth, lunarDay, true, today)
                            } else 1L
                        }
                        else -> {
                            val parts = lunarDatePart.split("-")
                            if (parts.size >= 3) {
                                val year = parts[0].toInt()
                                val lunarMonth = parts[1].toInt()
                                val lunarDay = parts[2].toInt()
                                LunarCalendarHelper.calculateLunarCountdown(year, lunarMonth, lunarDay, false, today)
                            } else 1L
                        }
                    }
                    // 距离下次超过200天，说明刚过去不久
                    daysBetween > 200
                }
                // 月-日格式：检查今年的这个日期是否已过
                eventDate.matches(Regex("\\d{2}-\\d{2}")) -> {
                    val parts = eventDate.split("-")
                    if (parts.size >= 2) {
                        val month = parts[0].toInt()
                        val day = parts[1].toInt()
                        val thisYearDate = java.time.LocalDate.of(today.year, month, day)
                        // 今年的日期已过（包括昨天和今天之前）
                        thisYearDate.isBefore(today)
                    } else false
                }
                // 标准格式 yyyy-MM-dd：检查今年的这个日期是否已过
                eventDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                    val parts = eventDate.split("-")
                    if (parts.size >= 3) {
                        val month = parts[1].toInt()
                        val day = parts[2].toInt()
                        val thisYearDate = java.time.LocalDate.of(today.year, month, day)
                        // 今年的日期已过（包括昨天和今天之前）
                        thisYearDate.isBefore(today)
                    } else false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Error checking if event passed: $eventDate", e)
            false
        }
    }

    // 手势返回处理：仅在需要拦截时启用，其他情况交给上层(MainApp)的双击退出逻辑
    BackHandler(enabled = isDragSortMode || showAddDialog || selectedEvent != null) {
        when {
            isDragSortMode -> {
                isDragSortMode = false
            }

            showAddDialog || selectedEvent != null -> {
                showAddDialog = false
                selectedEvent = null
            }
        }
    }

    // 过滤搜索结果
    val filteredEvents = remember(events, searchQuery) {
        if (searchQuery.isBlank()) {
            events
        } else {
            events.filter { event ->
                event.eventName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 数据加载 - 智能排序：根据设置决定是否将过期事件移到末尾（仅在内存中排序，不修改数据库）
    LaunchedEffect(loginState, dataManager, refreshKey) {
        scope.launch {
            isLoading = true
            try {
                val newEvents = dataManager.getEvents()
                // 先计算并缓存每个事件的天数
                val eventsWithCachedDays = newEvents.map { event ->
                    event.apply {
                        cachedDays = com.example.xiaomaotai.ui.components.calculateDaysAfter(eventDate).second
                        android.util.Log.d("HomeScreen", "缓存天数: $eventName ($eventDate) = $cachedDays 天")
                    }
                }
                // 使用统一的排序逻辑
                events = applySortLogic(eventsWithCachedDays)
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to load events", e)
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "我的纪念日",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "记录生活中的重要时刻",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 右侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 拖拽排序按钮
                    if (events.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                // 在传递前，先计算并缓存每个事件的天数
                                val eventsWithDays = events.map { event ->
                                    event.apply {
                                        cachedDays = com.example.xiaomaotai.ui.components.calculateDaysAfter(eventDate).second
                                        android.util.Log.d("MainActivity", "计算天数: $eventName = $cachedDays 天")
                                    }
                                }
                                onNavigateToSort(eventsWithDays) // 传递带天数的事件列表
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "拖拽排序",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 一键删除按钮
                    if (events.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllConfirm = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除所有",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                // 添加按钮
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加纪念日",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 搜索栏 - 现代化小巧设计
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索纪念日",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.6f
                                        ),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 根据模式显示不同界面
            if (isDragSortMode) {
                // 自定义排序界面
                Column(modifier = Modifier.weight(1f)) {
                    // 标题和说明
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "自定义排序",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "拖动",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(horizontal = 2.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "调整顺序，正序/倒序按天数排序",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // 保存按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 正序按钮
                            Button(
                                onClick = {
                                    val sortedEvents = dragSortEvents.sortedWith { event1, event2 ->
                                        val days1 = calculateDaysAfter(event1.eventDate).second
                                        val days2 = calculateDaysAfter(event2.eventDate).second
                                        when {
                                            days1 >= 0 && days2 >= 0 -> days1.compareTo(days2)
                                            days1 >= 0 && days2 < 0 -> -1
                                            days1 < 0 && days2 >= 0 -> 1
                                            else -> days2.compareTo(days1)
                                        }
                                    }
                                    dragSortEvents = sortedEvents
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("正序", fontSize = 12.sp)
                            }

                            // 倒序按钮
                            Button(
                                onClick = {
                                    val sortedEvents = dragSortEvents.sortedWith { event1, event2 ->
                                        val days1 = calculateDaysAfter(event1.eventDate).second
                                        val days2 = calculateDaysAfter(event2.eventDate).second
                                        when {
                                            days1 >= 0 && days2 >= 0 -> days2.compareTo(days1)
                                            days1 >= 0 && days2 < 0 -> -1
                                            days1 < 0 && days2 >= 0 -> 1
                                            else -> days1.compareTo(days2)
                                        }
                                    }
                                    dragSortEvents = sortedEvents
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("倒序", fontSize = 12.sp)
                            }

                            // 默认按钮
                            Button(
                                onClick = {
                                    dragSortEvents = events.sortedBy { it.sortOrder }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text("默认", fontSize = 12.sp)
                            }
                        }

                        // 保存按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { isDragSortMode = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    // 保存排序到数据库
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            // 更新每个事件的 sortOrder
                                            val updatedEvents =
                                                dragSortEvents.mapIndexed { index, event ->
                                                    event.copy(sortOrder = index)
                                                }

                                            // 调用 DataManager 的批量更新方法
                                            dataManager.updateEventOrder(updatedEvents)

                                            // 保存本地排序状态，并应用排序逻辑
                                            sortOrder = updatedEvents.map { it.id }
                                            events = applySortLogic(updatedEvents)
                                            isDragSortMode = false

                                            Log.d("HomeScreen", "排序保存成功")
                                        } catch (e: Exception) {
                                            Log.e("HomeScreen", "保存排序失败: ${e.message}")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存排序")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 拖拽卡片列表
                    val dragListState = rememberLazyListState()
                    LazyColumn(
                        state = dragListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(dragSortEvents.size) { index ->
                            val event = dragSortEvents[index]
                            EventItem(
                                event = event,
                                isDragMode = true,
                                onEdit = { },
                                onDelete = { },
                                onDragMove = { fromIndex, toIndex ->
                                    if (fromIndex != toIndex &&
                                        fromIndex in 0 until dragSortEvents.size &&
                                        toIndex in 0 until dragSortEvents.size
                                    ) {
                                        val newList = dragSortEvents.toMutableList()
                                        val item = newList.removeAt(fromIndex)
                                        newList.add(toIndex, item)
                                        dragSortEvents = newList
                                    }
                                },
                                dragIndex = index
                            )
                        }
                    }
                }
            } else {
                // 主界面内容区域 - 加载时显示指示器但保持现有数据
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredEvents.isEmpty() && !isLoading) {
                        // 空状态
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "未找到相关纪念日" else "暂无纪念日",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "尝试使用其他关键词搜索" else "点击右上角按钮添加您的第一个纪念日",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // 事件列表
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredEvents) { event ->
                                EventItem(
                                    event = event,
                                    isDragMode = false,
                                    onEdit = { selectedEvent = event },
                                    onDelete = {
                                        scope.launch {
                                            isLoading = true
                                            try {
                                                dataManager.deleteEvent(event.id)

                                                // 从当前列表中移除删除的卡片，并重新应用排序逻辑
                                                val updatedEvents = events.filter { it.id != event.id }
                                                events = applySortLogic(updatedEvents)
                                            } catch (e: Exception) {
                                                Log.e("HomeScreen", "Failed to delete event", e)
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Loading指示器 - 使用GlobalLoadingDialog样式
                if (isLoading) {
                    GlobalLoadingDialog()
                }
            }



            if (showAddDialog || selectedEvent != null) {
                EventDialog(
                    event = selectedEvent,
                    onDismiss = {
                        showAddDialog = false
                        selectedEvent = null
                    },
                    onSave = { eventName: String, eventDate: String ->
                        // 保存当前编辑的事件引用
                        val currentEvent = selectedEvent

                        // 先关闭弹窗
                        showAddDialog = false
                        selectedEvent = null

                        // 然后开始loading和数据操作
                        scope.launch {
                            isLoading = true
                            try {
                                val eventToSave = currentEvent?.copy(
                                    eventName = eventName,
                                    eventDate = eventDate
                                    // 编辑时不改变backgroundId，保持原有样式
                                ) ?: Event(
                                    eventName = eventName,
                                    eventDate = eventDate,
                                    sortOrder = (events.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                                    backgroundId = CardStyleManager.getRandomStyleId() // 新建事件时分配随机样式
                                )

                                if (currentEvent != null) {
                                    dataManager.updateEvent(eventToSave)
                                } else {
                                    dataManager.addEvent(eventToSave)
                                }

                                // 获取最新数据并应用排序逻辑
                                val newEvents = dataManager.getEvents()
                                events = applySortLogic(newEvents)
                            } catch (e: Exception) {
                                Log.e("HomeScreen", "Failed to save event", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    dataManager = dataManager
                )
            }

            // 一键删除确认对话框
            if (showDeleteAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllConfirm = false },
                    title = { Text("确认删除") },
                    text = {
                        Text("确定要删除所有纪念日吗？此操作不可撤销，将删除 ${events.size} 个纪念日。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // 保存要删除的事件列表
                                val eventsToDelete = events.toList()

                                // 先关闭弹窗
                                showDeleteAllConfirm = false

                                // 然后开始loading和删除操作
                                scope.launch {
                                    isLoading = true
                                    try {
                                        // 删除所有事件
                                        eventsToDelete.forEach { event ->
                                            dataManager.deleteEvent(event.id)
                                        }
                                        // 直接清空列表，不重新排序
                                        events = emptyList()
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Failed to delete all events", e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllConfirm = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

