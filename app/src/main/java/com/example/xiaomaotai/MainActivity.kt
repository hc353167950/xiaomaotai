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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.xiaomaotai.ui.theme.Space
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Sizes
import com.example.xiaomaotai.ui.components.AboutScreen
import com.example.xiaomaotai.ui.components.EventItem
import com.example.xiaomaotai.ui.components.EventDialog
import com.example.xiaomaotai.ui.components.CardStyleManager
import com.example.xiaomaotai.ui.components.GlobalLoadingDialog
import com.example.xiaomaotai.ui.components.KeepAliveScreen
import com.example.xiaomaotai.ui.components.NotificationScreen
import com.example.xiaomaotai.ui.components.SettingsHomeScreen
import com.example.xiaomaotai.ui.components.SettingsRoute
import com.example.xiaomaotai.ui.components.SortingScreen
import com.example.xiaomaotai.ui.components.UiStyleScreen
import com.example.xiaomaotai.ui.components.WebDavScreen
import com.example.xiaomaotai.ui.components.calculateDaysAfter
import com.example.xiaomaotai.ui.components.SortScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
            val context = LocalContext.current
            val app = context.applicationContext as XiaoMaoTaiApp
            var uiStyleId by remember {
                mutableStateOf(
                    app.settings.getUiStyleId().ifBlank { "soft_diary" }
                )
            }
            val uiStyle = com.example.xiaomaotai.ui.theme.UiStyle.fromId(uiStyleId)

            XiaoMaoTaiTheme(uiStyle = uiStyle) {
                LaunchedEffect(Unit) {
                    app.events.initialize()

                    // 检查并应用隐藏最近任务设置
                    handleHideRecentTask(app.settings)

                    // 检查并启动常驻通知服务
                    handlePersistentNotification(app.settings)
                }

                MainApp(
                    events = app.events,
                    webDavSync = app.webDavSync,
                    webDavCredentials = app.webDavCredentials,
                    settings = app.settings,
                    uiStyleId = uiStyleId,
                    onUiStyleChange = { id ->
                        app.settings.setUiStyleId(id)
                        uiStyleId = id
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 如果开启了隐藏最近任务，在APP进入后台时从最近任务列表中移除（不杀掉APP）
        // 但如果正在跳转到系统设置页，则跳过此操作，避免APP被回收
        if (XiaoMaoTaiApp.settings(this).isHideRecentTaskEnabled() && !isNavigatingToSettings) {
            moveTaskToBack(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回，重置标记
        isNavigatingToSettings = false

        // 每次恢复时检查设置
        val settings = XiaoMaoTaiApp.settings(this)
        handleHideRecentTask(settings)
        handlePersistentNotification(settings)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不再在APP退出时停止常驻通知服务
        // 常驻通知的目的就是APP退出后仍然显示
        // 只有用户主动关闭开关时才停止服务（在SettingsScreen中处理）
    }

    /**
     * 处理隐藏最近任务设置
     * 动态设置Activity的excludeFromRecents属性
     */
    private fun handleHideRecentTask(settings: AppSettings) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (settings.isHideRecentTaskEnabled()) {
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
    private fun handlePersistentNotification(settings: AppSettings) {
        if (settings.isPersistentNotificationEnabled()) {
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
fun MainApp(
    events: EventStore,
    webDavSync: WebDavSync,
    webDavCredentials: WebDavCredentials,
    settings: AppSettings,
    uiStyleId: String = "soft_diary",
    onUiStyleChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showNotificationPrompt by remember { mutableStateOf(false) }
    var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") }
    // 设置二级页当前路由；null 表示停留在设置根页
    var settingsRoute by remember { mutableStateOf<SettingsRoute?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var sortScreenEvents by remember { mutableStateOf<List<Event>>(emptyList()) } // 保存传递给排序页的事件列表

    // 双击返回退出相关状态
    var backPressedTime by remember { mutableStateOf(0L) }
    var showExitToast by remember { mutableStateOf(false) }

    val permissionManager = remember { PermissionManager(context) }

    // 监听生命周期，从后台恢复时刷新首页数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从后台恢复时，通过递增refreshKey触发HomeScreen刷新
                android.util.Log.d("MainApp", "APP从后台恢复，触发refreshKey递增")
                refreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
        // 检查是否需要显示通知权限提示
        if (!hasNotificationPermission() && settings.shouldShowNotificationPrompt()) {
            showNotificationPrompt = true
        }
    }
    
    // 设置二级页的返回键由各页内部 BackHandler 处理；这里只处理两个 Tab 根页
    if (currentScreen == "home" || (currentScreen == "settings" && settingsRoute == null)) {
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 排序页与设置二级页都全屏显示，隐藏底部导航
            if (currentScreen != "sort" && settingsRoute == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Sizes.bottomBar)
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomNavigationItem(
                                icon = Icons.Default.Home,
                                label = "首页",
                                selected = currentScreen == "home",
                                onClick = { currentScreen = "home" }
                            )
                            CustomNavigationItem(
                                icon = Icons.Default.Settings,
                                label = "设置",
                                selected = currentScreen == "settings",
                                onClick = {
                                    currentScreen = "settings"
                                    settingsRoute = null
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // 导航逻辑
        when (currentScreen) {
            "home" -> HomeScreen(
                eventStore = events,
                settings = settings,
                modifier = Modifier.padding(paddingValues),
                onNavigateToSort = { events ->
                    sortScreenEvents = events // 保存事件列表
                    currentScreen = "sort"
                },
                refreshKey = refreshKey
            )
            "settings" -> {
                val route = settingsRoute
                if (route == null) {
                    // 设置根页：分类入口列表
                    SettingsHomeScreen(
                        uiStyleId = uiStyleId,
                        syncConfigured = webDavCredentials.isConfigured(),
                        onNavigate = { settingsRoute = it }
                    )
                } else {
                    // 二级页：全屏，自带返回箭头
                    val back = { settingsRoute = null }
                    when (route) {
                        SettingsRoute.UiStyle -> UiStyleScreen(
                            uiStyleId = uiStyleId,
                            onUiStyleChange = onUiStyleChange,
                            onBack = back
                        )
                        SettingsRoute.Notification -> NotificationScreen(onBack = back)
                        SettingsRoute.KeepAlive -> KeepAliveScreen(onBack = back)
                        SettingsRoute.WebDav -> WebDavScreen(
                            eventStore = events,
                            credentials = webDavCredentials,
                            sync = webDavSync,
                            onBack = back
                        )
                        SettingsRoute.Sorting -> SortingScreen(onBack = back)
                        SettingsRoute.About -> AboutScreen(onBack = back)
                    }
                }
            }
            "sort" -> {
                SortScreen(
                    eventStore = events,
                    settings = settings,
                    initialEvents = sortScreenEvents, // 传递首页的事件列表
                    onDone = {
                        refreshKey += 1
                        currentScreen = "home"
                    }
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
                    .padding(bottom = 96.dp)
                    .wrapContentSize(),
                shape = Shape.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "再按一次返回键退出",
                    modifier = Modifier.padding(
                        horizontal = Space.xl,
                        vertical = Space.md
                    ),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp
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
        shape = Shape.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 图标徽标：让权限请求不那么生硬
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(Space.sm)
                            .size(Space.iconMd)
                    )
                }
                Spacer(modifier = Modifier.width(Space.md))
                Text(
                    text = "开启精确提醒",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "为了让纪念日提醒准时送达（即使 App 已退出），需要开启以下权限：",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Space.md))
                PermissionHint("精确闹钟", "确保提醒准时触发")
                Spacer(modifier = Modifier.height(Space.sm))
                PermissionHint("电池优化白名单", "防止系统清理后台提醒")
                Spacer(modifier = Modifier.height(Space.md))
                Text(
                    text = "点击「去设置」将跳转到系统页面授权。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "去设置",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "稍后",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        }
    )
}

/** 权限说明单行：左侧圆点 + 标题 + 灰色说明 */
@Composable
private fun PermissionHint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(Space.sm))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeScreen(
    eventStore: EventStore,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onNavigateToSort: (List<Event>) -> Unit, // 修改为传递事件列表
    refreshKey: Int
) {
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") }
    var internalRefreshKey by remember { mutableStateOf(0) }
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
    // 注意：纪念日都是循环的，cachedDays永远>=0，表示距离下一次的天数
    fun applySortLogic(eventsList: List<Event>): List<Event> {
        return if (settings.isAutoSortExpiredEventsEnabled()) {
            // 开启了自动排序：按距离下次纪念日的天数升序排序（天数越少越靠前）
            // 天数相同时按sortOrder降序（保持用户手动排序）
            val sorted = eventsList.sortedWith(
                compareBy<Event> { it.cachedDays ?: 0L }
                    .thenByDescending { it.sortOrder }
            )

            Log.d("HomeScreen", "Applied auto-sort by days: ${eventsList.size} events")
            sorted
        } else {
            // 关闭了自动排序：完全按照 sortOrder 降序排列
            val sorted = eventsList.sortedByDescending { it.sortOrder }
            Log.d("HomeScreen", "Applied normal sort: ${eventsList.size} events")
            sorted
        }
    }

    // 注意：生命周期监听已移到MainApp层级，通过refreshKey传递触发刷新
    // HomeScreen内部的internalRefreshKey用于广播触发的刷新

    // 监听系统时间变化广播（包括系统广播和自定义广播）
    DisposableEffect(Unit) {
        val intentFilter = android.content.IntentFilter().apply {
            // 自定义刷新广播
            addAction("com.example.xiaomaotai.ACTION_REFRESH_EVENTS")
            // 系统时间变化广播（直接在Activity中监听，确保能收到）
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                android.util.Log.d("HomeScreen", "收到广播: ${intent?.action}，触发数据重新加载")
                // 通过递增internalRefreshKey触发LaunchedEffect重新加载数据
                internalRefreshKey += 1
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "注销广播接收器失败", e)
            }
        }
    }

    /**
     * 判断事件今年的日期是否已过（仅用于显示排序，不修改数据库）
     */
    fun isEventPassedThisYear(eventDate: String, today: java.time.LocalDate): Boolean {
        // 委托给 Countdown 深模块，不再在这里手写日期解析分支。
        // 旧实现自己 split 字符串，闰月/非法日期会与 Countdown 的判断不一致。
        val countdown = Countdown.of(eventDate, today) ?: return false
        // 距离下一次超过 200 天，说明本年的纪念日刚过去不久
        return countdown.days > 200
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
    // 监听 internalRefreshKey 确保从后台恢复时也能刷新数据
    LaunchedEffect(eventStore, refreshKey, internalRefreshKey) {
        scope.launch {
            isLoading = true
            try {
                val newEvents = eventStore.currentEvents()
                // 先计算并缓存每个事件的天数 - 使用copy避免apply的引用问题
                val eventsWithCachedDays = newEvents.map { event ->
                    val days = com.example.xiaomaotai.ui.components.calculateDaysAfter(event.eventDate).second
                    android.util.Log.d("HomeScreen", "缓存天数: ${event.eventName} (${event.eventDate}) = $days 天")
                    event.copy(cachedDays = days)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部固定区：标题 + 操作 + 搜索
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = Space.screenH)
                    .padding(bottom = Space.xs)
            ) {
                HomeListHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    eventsNotEmpty = events.isNotEmpty(),
                    onSortClick = {
                        val eventsWithDays = events.map { event ->
                            val days = com.example.xiaomaotai.ui.components
                                .calculateDaysAfter(event.eventDate).second
                            event.copy(cachedDays = days)
                        }
                        onNavigateToSort(eventsWithDays)
                    },
                    onDeleteAllClick = { showDeleteAllConfirm = true },
                    onAddClick = { showAddDialog = true }
                )
            }

            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenH)
                    .padding(top = Space.sm, bottom = Space.sm)
            ) {
            // 根据模式显示不同界面
            if (isDragSortMode) {
                // 自定义排序界面
                Column(modifier = Modifier.weight(1f)) {
                    // 标题和说明
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
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

                                            // 调用 EventStore 的批量更新方法
                                            eventStore.updateEventOrder(updatedEvents)

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

                    Spacer(modifier = Modifier.height(Space.lg))

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
                // 主界面内容区域
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredEvents.isEmpty() && !isLoading) {
                        EmptyState(
                            isSearch = searchQuery.isNotEmpty(),
                            onAddClick = { showAddDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(filteredEvents, key = { it.id }) { event ->
                                EventItem(
                                    event = event,
                                    isDragMode = false,
                                    onEdit = { selectedEvent = event },
                                    onDelete = {
                                        scope.launch {
                                            isLoading = true
                                            try {
                                                eventStore.deleteEvent(event.id)
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

                    if (isLoading) {
                        GlobalLoadingDialog()
                    }
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
                                    backgroundId = CardStyleManager.getSmartRandomStyleId(events.map { it.backgroundId }) // 智能分配不重复的样式
                                )

                                if (currentEvent != null) {
                                    eventStore.updateEvent(eventToSave)
                                } else {
                                    eventStore.addEvent(eventToSave)
                                }

                                // 获取最新数据，重新计算天数，并应用排序逻辑
                                val newEvents = eventStore.currentEvents()
                                val eventsWithCachedDays = newEvents.map { event ->
                                    val days = com.example.xiaomaotai.ui.components.calculateDaysAfter(event.eventDate).second
                                    android.util.Log.d("HomeScreen", "保存后重新计算天数: ${event.eventName} (${event.eventDate}) = $days 天")
                                    event.copy(cachedDays = days)
                                }
                                events = applySortLogic(eventsWithCachedDays)
                            } catch (e: Exception) {
                                Log.e("HomeScreen", "Failed to save event", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    events = eventStore
                )
            }

            // 一键删除确认对话框
            if (showDeleteAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllConfirm = false },
                    shape = Shape.large,
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(Space.sm)
                                        .size(Space.iconMd)
                                )
                            }
                            Spacer(modifier = Modifier.width(Space.md))
                            Text(
                                text = "删除全部纪念日",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = (-0.3).sp
                            )
                        }
                    },
                    text = {
                        Text(
                            "将删除全部 ${events.size} 个纪念日，此操作无法撤销。",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val eventsToDelete = events.toList()
                                showDeleteAllConfirm = false
                                scope.launch {
                                    isLoading = true
                                    try {
                                        eventsToDelete.forEach { event ->
                                            eventStore.deleteEvent(event.id)
                                        }
                                        events = emptyList()
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Failed to delete all events", e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        ) {
                            Text(
                                "全部删除",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllConfirm = false }) {
                            Text(
                                "取消",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                )
            }
            }
        }
    }
}

/**
 * 首页顶部：标题 + 小按钮 + 搜索（固定在列表上方）
 */
@Composable
private fun HomeListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    eventsNotEmpty: Boolean,
    onSortClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm, bottom = Space.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "纪念日",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.9).sp
                )
                Spacer(modifier = Modifier.height(Space.xs))
                Text(
                    text = "记住每一个重要的日子",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.2.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (eventsNotEmpty) {
                    HeaderIconButton(
                        icon = Icons.Default.SwapVert,
                        contentDescription = "拖拽排序",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onSortClick
                    )
                    HeaderIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "删除所有",
                        container = MaterialTheme.colorScheme.errorContainer,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDeleteAllClick
                    )
                }
                HeaderIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "添加纪念日",
                    container = MaterialTheme.colorScheme.primary,
                    tint = Color.White,
                    onClick = onAddClick
                )
            }
        }

        Spacer(modifier = Modifier.height(Space.md))

        // 搜索框：44dp 高度，内边距与图标比例协调
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.searchHeight),
            shape = Shape.small,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Space.iconSm)
                )
                Spacer(modifier = Modifier.width(Space.sm))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索纪念日",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Space.xs))
                    // 清除按钮保留 32dp 可点区，视觉图标仍小
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(Shape.tiny)
                            .clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶栏图标按钮：视觉 38dp，但外层留足 48dp 触控区。
 */
@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    container: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Space.MinTouchTarget)
            .clip(Shape.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = Shape.tiny,
            color = container,
            shadowElevation = 0.dp
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .padding(Space.sm)
                    .size(Space.iconSm)
            )
        }
    }
}


/**
 * 自定义底部导航项组件
 * 功能描述: 限制点击区域只在图标和文字附近，点击空白区域不触发切换
 * Args:
 *     icon (ImageVector): 图标
 *     label (String): 标签文字
 *     selected (Boolean): 是否选中
 *     onClick (() -> Unit): 点击回调
 */
@Composable
fun CustomNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            // 48dp 最小触控区，视觉上仍是紧凑的图标+文字
            .heightIn(min = Space.MinTouchTarget)
            .clip(Shape.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.xxl, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(Space.iconLg)
        )
        Spacer(modifier = Modifier.height(Space.xxs))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            letterSpacing = 0.1.sp
        )
    }
}

/**
 * 空状态。
 *
 * 视觉：柔光圆形图标底 + 主标题 + 副标题 + 主行动按钮。
 * 有搜索词时不显示按钮（避免与"清空搜索"混淆）。
 */
@Composable
private fun EmptyState(
    isSearch: Boolean,
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.huge)
                .padding(bottom = Space.huge)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        imageVector = if (isSearch) Icons.Default.SearchOff else Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(Space.xl)
                            .size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Space.xxl))

            Text(
                text = if (isSearch) "没有找到" else "开始记录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(Space.sm))

            Text(
                text = if (isSearch)
                    "换个关键词，或检查是否有错别字"
                else
                    "生日、纪念日、约定……\n把值得记住的日子都放在这里",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!isSearch) {
                Spacer(modifier = Modifier.height(Space.xxl))
                Button(
                    onClick = onAddClick,
                    shape = Shape.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    contentPadding = PaddingValues(
                        horizontal = Space.xl,
                        vertical = Space.md
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(Space.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Space.sm))
                    Text("添加第一个纪念日", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
