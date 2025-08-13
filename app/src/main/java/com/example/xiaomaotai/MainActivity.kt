package com.example.xiaomaotai

import android.Manifest
import android.app.Activity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.xiaomaotai.ui.components.GlobalLoadingDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*



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
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Permission", "Notification permission granted.")
            // 通知权限授权后，检查精确闹钟权限
            val permissionManager = PermissionManager(this)
            checkExactAlarmPermission(permissionManager)
        } else {
            Log.d("Permission", "Notification permission denied.")
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
                }

                MainApp(dataManager = dataManager)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionManager = PermissionManager(this)
        
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permission", "Notification permission already granted.")
                    // 检查精确闹钟权限
                    checkExactAlarmPermission(permissionManager)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 13以下直接检查精确闹钟权限
            checkExactAlarmPermission(permissionManager)
        }
    }
    
    private fun checkExactAlarmPermission(permissionManager: PermissionManager) {
        if (!permissionManager.hasExactAlarmPermission()) {
            Log.d("Permission", "Exact alarm permission not granted, showing dialog.")
            // 这里可以显示一个对话框提示用户授权精确闹钟权限
            showExactAlarmPermissionDialog()
        }
    }
    
    private fun showExactAlarmPermissionDialog() {
        // 直接跳转到设置页面请求精确闹钟权限
        val permissionManager = PermissionManager(this)
        try {
            val intent = permissionManager.getExactAlarmSettingsIntent()
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Permission", "无法打开精确闹钟设置页面: ${e.message}")
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
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = currentScreen == "home",
                    onClick = { currentScreen = "home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") },
                    selected = currentScreen == "profile",
                    onClick = { currentScreen = "profile" }
                )
            }
        }
    ) { paddingValues ->
        // 导航逻辑
        when (currentScreen) {
            "home" -> HomeScreen(
                dataManager = dataManager,
                loginState = dataManager.isLoggedIn(),
                modifier = Modifier.padding(paddingValues)
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
fun HomeScreen(dataManager: DataManager, loginState: Boolean, modifier: Modifier = Modifier) {
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 手势返回处理
    BackHandler {
        when {
            showAddDialog || selectedEvent != null -> {
                showAddDialog = false
                selectedEvent = null
            }
            else -> {
                (context as? Activity)?.finish()
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

    // 数据加载 - 优化加载状态，保持现有数据直到新数据加载完成
    LaunchedEffect(loginState, dataManager) {
        scope.launch {
            isLoading = true
            try {
                val newEvents = dataManager.getEvents()
                events = newEvents
                Log.d("HomeScreen", "Events loaded: ${newEvents.size}")
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
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加纪念日",
                        tint = Color.White
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

        // 内容区域 - 加载时显示指示器但保持现有数据
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
                    items(filteredEvents.sortedByDescending { it.sortOrder }) { event ->
                        EventItem(
                            event = event,
                            onEdit = { selectedEvent = event },
                            onDelete = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        dataManager.deleteEvent(event.id)
                                        val newEvents = dataManager.getEvents()
                                        events = newEvents
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
                        ) ?: Event(
                            eventName = eventName,
                            eventDate = eventDate,
                            sortOrder = (events.maxOfOrNull { it.sortOrder } ?: -1) + 1
                        )

                        if (currentEvent != null) {
                            dataManager.updateEvent(eventToSave)
                        } else {
                            dataManager.addEvent(eventToSave)
                        }
                        
                        // 获取最新数据并更新
                        val newEvents = dataManager.getEvents()
                        events = newEvents
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
                                // 获取最新数据并更新
                                val newEvents = dataManager.getEvents()
                                events = newEvents
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
