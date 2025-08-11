package com.example.xiaomaotai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.xiaomaotai.DataManager
import com.example.xiaomaotai.User
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    dataManager: DataManager,
    currentUser: User?,
    onLogin: (User) -> Unit,
    onLogout: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: () -> Unit
) {
    var showLoginScreen by remember { mutableStateOf(false) }
    var showRegisterScreen by remember { mutableStateOf(false) }

    // 检查是否应该直接显示登录或注册页面（通过外部导航）
    // 这个逻辑已经不需要了，直接使用内部状态管理

    // 手势返回处理
    BackHandler {
        if (showLoginScreen || showRegisterScreen) {
            showLoginScreen = false
            showRegisterScreen = false
        } else {
            onBack()
        }
    }

    when {
        showRegisterScreen -> {
            // 注册页面
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showRegisterScreen = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "注册",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                RegisterScreen(
                    dataManager = dataManager,
                    onRegisterSuccess = { user ->
                        showRegisterScreen = false
                        onLogin(user)
                    },
                    onSwitchToLogin = { 
                        showRegisterScreen = false
                        showLoginScreen = true
                    }
                )
            }
        }
        showLoginScreen -> {
            // 登录页面
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showLoginScreen = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "登录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                LoginScreen(
                    dataManager = dataManager,
                    onLoginSuccess = { user ->
                        showLoginScreen = false
                        onLogin(user)
                    },
                    onSwitchToRegister = { 
                        showLoginScreen = false
                        showRegisterScreen = true
                    },
                    onNavigateToForgotPassword = onNavigateToForgotPassword
                )
            }
        }
        currentUser != null -> {
            // 已登录状态 - 显示用户信息和功能菜单
            LoggedInProfileScreen(
                user = currentUser,
                onLogout = onLogout,
                onNavigateToSettings = onNavigateToSettings,
                onBack = onBack
            )
        }
        else -> {
            // 未登录状态 - 显示前置页面
            ProfileMainScreen(
                onNavigateToLogin = { showLoginScreen = true },
                onNavigateToRegister = { showRegisterScreen = true },
                onNavigateToSettings = onNavigateToSettings,
                onBack = onBack
            )
        }
    }
}

@Composable
fun ProfileMainScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏 - 只显示右上角设置按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 右上角设置按钮
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // 内容区域 - 靠中上位置
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 主标题
            Text(
                text = "欢迎使用纪念日APP",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "登录后可以同步数据到云端",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 登录按钮
            Button(
                onClick = onNavigateToLogin,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 注册按钮
            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "注册",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            } // 关闭内层Column
        }
    }
}

@Composable
fun LoggedInProfileScreen(
    user: User,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏 - 只显示右上角设置按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 右上角设置按钮
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // 内容区域 - 靠中上位置
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用户头像 - 稍大一些
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.nickname.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 用户信息区域 - 更饱满的布局
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = user.nickname,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "@${user.username}",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (!user.email.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = user.email,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 退出登录按钮 - 更饱满的设计
                OutlinedButton(
                    onClick = {
                        onLogout()
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "退出登录",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } // 关闭内层Column
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun LoginScreen(
    dataManager: DataManager,
    onLoginSuccess: (User) -> Unit,
    onSwitchToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "登录",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 用户名输入框
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { 
                Text(
                    "用户名",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入用户名",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 密码输入框
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { 
                Text(
                    "密码",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入密码",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        // 忘记密码链接
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "忘记密码？",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    onNavigateToForgotPassword()
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 错误信息
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        // 登录按钮
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = ""
                    try {
                        val result = dataManager.loginUser(username, password)
                        if (result.isSuccess) {
                            val user = result.getOrNull()!!
                            onLoginSuccess(user)
                            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "登录失败"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && username.isNotEmpty() && password.isNotEmpty(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("登录")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 切换到注册
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "还没有账号？",
                fontSize = 13.sp
            )
            Text(
                text = "立即注册",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { onSwitchToRegister() }
            )
        }
        
        // 全屏加载指示器
        if (isLoading) {
            GlobalLoadingDialog()
        }
    }
}

@Composable
fun RegisterScreen(
    dataManager: DataManager,
    onRegisterSuccess: (User) -> Unit,
    onSwitchToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "注册",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 用户名输入框
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { 
                Text(
                    "用户名",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入用户名",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 昵称输入框
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { 
                Text(
                    "昵称",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入昵称",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 邮箱输入框
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { 
                Text(
                    "邮箱",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入邮箱",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        // 邮箱错误提示
        if (email.isNotEmpty() && !isValidEmail(email)) {
            Text(
                text = "请输入正确的邮箱格式",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 密码输入框
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { 
                Text(
                    "密码",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "请输入密码",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 确认密码输入框
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { 
                Text(
                    "确认密码",
                    fontSize = 13.sp
                ) 
            },
            placeholder = { 
                Text(
                    "再输入一遍密码",
                    fontSize = 13.sp
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 错误信息
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        // 注册按钮
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = ""
                    
                    // 输入验证
                    when {
                        username.isEmpty() -> {
                            errorMessage = "请输入用户名"
                            isLoading = false
                            return@launch
                        }
                        nickname.isEmpty() -> {
                            errorMessage = "请输入昵称"
                            isLoading = false
                            return@launch
                        }
                        email.isEmpty() -> {
                            errorMessage = "请输入邮箱"
                            isLoading = false
                            return@launch
                        }
                        !isValidEmail(email) -> {
                            errorMessage = "请输入正确的邮箱格式"
                            isLoading = false
                            return@launch
                        }
                        password.isEmpty() -> {
                            errorMessage = "请输入密码"
                            isLoading = false
                            return@launch
                        }
                        password.length < 6 -> {
                            errorMessage = "密码长度不能少于6位"
                            isLoading = false
                            return@launch
                        }
                        confirmPassword.isEmpty() -> {
                            errorMessage = "请确认密码"
                            isLoading = false
                            return@launch
                        }
                        password != confirmPassword -> {
                            errorMessage = "两次输入的密码不一致"
                            isLoading = false
                            return@launch
                        }
                    }
                    
                    try {
                        val result = dataManager.registerUser(username, nickname, email, password)
                        if (result.isSuccess) {
                            val user = result.getOrNull()!!
                            onRegisterSuccess(user)
                            Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "注册失败"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && username.isNotEmpty() && nickname.isNotEmpty() &&
                    email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() &&
                    isValidEmail(email),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("注册")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 切换到登录
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "已有账号？",
                fontSize = 13.sp
            )
            Text(
                text = "立即登录",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { onSwitchToLogin() }
            )
        }
        
        // 全屏加载指示器
        if (isLoading) {
            GlobalLoadingDialog()
        }
    }
}
