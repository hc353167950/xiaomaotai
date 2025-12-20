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
import com.example.xiaomaotai.ValidationUtils
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

    // 手势返回处理
    BackHandler(enabled = showLoginScreen || showRegisterScreen) {
        if (showLoginScreen || showRegisterScreen) {
            showLoginScreen = false
            showRegisterScreen = false
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
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { showRegisterScreen = false },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
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
                        modifier = Modifier.align(Alignment.Center)
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
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { showLoginScreen = false },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
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
                        modifier = Modifier.align(Alignment.Center)
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
    // 读取上次登录的用户名
    var username by remember { mutableStateOf(dataManager.getLastUsername()) }
    var password by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var serverErrorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 用户名输入框
        StyledInputField(
            label = "用户名",
            value = username,
            onValueChange = {
                username = it.filter { c -> c.isLetterOrDigit() }.take(20)
                usernameError = null
                serverErrorMessage = ""
            },
            placeholder = "请输入用户名",
            error = usernameError
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 密码输入框
        StyledInputField(
            label = "密码",
            value = password,
            onValueChange = {
                password = it.take(20)
                passwordError = null
                serverErrorMessage = ""
            },
            placeholder = "请输入密码",
            error = passwordError,
            isPassword = true,
            keyboardType = KeyboardType.Password
        )

        // 忘记密码链接
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "忘记密码？",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNavigateToForgotPassword() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        // 服务器错误信息
        if (serverErrorMessage.isNotEmpty()) {
            Text(
                text = serverErrorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 登录按钮
        Button(
            onClick = {
                val usernameValidation = ValidationUtils.validateUsername(username)
                val passwordValidation = ValidationUtils.validatePassword(password)

                usernameError = if (!usernameValidation.isValid) usernameValidation.message else null
                passwordError = if (!passwordValidation.isValid) passwordValidation.message else null

                if (usernameValidation.isValid && passwordValidation.isValid) {
                    scope.launch {
                        isLoading = true
                        serverErrorMessage = ""
                        try {
                            val result = dataManager.loginUser(username, password)
                            if (result.isSuccess) {
                                val user = result.getOrNull()!!
                                onLoginSuccess(user)
                                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                            } else {
                                serverErrorMessage = result.exceptionOrNull()?.message ?: "登录失败"
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 切换到注册
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "还没有账号？",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "立即注册",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSwitchToRegister() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        // 底部间距，确保键盘弹出时按钮也能滚动到可见位置
        Spacer(modifier = Modifier.height(200.dp))

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

    var usernameError by remember { mutableStateOf<String?>(null) }
    var nicknameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var serverErrorMessage by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun validateAllFields(): Boolean {
        val usernameValidation = ValidationUtils.validateUsername(username)
        val nicknameValidation = ValidationUtils.validateNickname(nickname)
        val emailValidation = ValidationUtils.validateEmail(email)
        val passwordValidation = ValidationUtils.validatePassword(password, username)
        val confirmPasswordValidation = when {
            confirmPassword.isEmpty() -> "请再次输入密码"
            password != confirmPassword -> "两次输入的密码不一致"
            else -> null
        }

        usernameError = if (!usernameValidation.isValid) usernameValidation.message else null
        nicknameError = if (!nicknameValidation.isValid) nicknameValidation.message else null
        emailError = if (!emailValidation.isValid) emailValidation.message else null
        passwordError = if (!passwordValidation.isValid) passwordValidation.message else null
        confirmPasswordError = confirmPasswordValidation

        return usernameError == null && nicknameError == null && emailError == null && passwordError == null && confirmPasswordError == null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 用户名输入框
        StyledInputField(
            label = "用户名",
            value = username,
            onValueChange = {
                username = it.filter { char -> char.isLetterOrDigit() }.take(20)
                usernameError = null
                serverErrorMessage = ""
            },
            placeholder = "仅支持英文和数字",
            error = usernameError
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 昵称输入框
        StyledInputField(
            label = "昵称",
            value = nickname,
            onValueChange = {
                nickname = it.take(10)
                nicknameError = null
            },
            placeholder = "请输入昵称",
            error = nicknameError
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 邮箱输入框
        StyledInputField(
            label = "邮箱",
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            placeholder = "用于找回密码",
            error = emailError,
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 密码输入框
        StyledInputField(
            label = "密码",
            value = password,
            onValueChange = {
                password = it.take(20)
                passwordError = null
            },
            placeholder = "6-12位英文或数字",
            error = passwordError,
            isPassword = true,
            keyboardType = KeyboardType.Password
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 确认密码输入框
        StyledInputField(
            label = "确认密码",
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it.take(20)
                confirmPasswordError = null
            },
            placeholder = "再输入一遍密码",
            error = confirmPasswordError,
            isPassword = true,
            keyboardType = KeyboardType.Password
        )

        // 服务器错误信息
        if (serverErrorMessage.isNotEmpty()) {
            Text(
                text = serverErrorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 注册按钮
        Button(
            onClick = {
                val isValid = validateAllFields()
                if (!isValid) {
                    // 验证失败时滚动到底部，确保错误提示可见
                    scope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    serverErrorMessage = ""
                    try {
                        val result = dataManager.registerUser(username, nickname, email, password)
                        if (result.isSuccess) {
                            val user = result.getOrNull()!!
                            onRegisterSuccess(user)
                            Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                        } else {
                            serverErrorMessage = result.exceptionOrNull()?.message ?: "注册失败"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "注册",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 切换到登录
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已有账号？",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "立即登录",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSwitchToLogin() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        // 底部间距，确保键盘弹出时按钮也能滚动到可见位置
        Spacer(modifier = Modifier.height(200.dp))

        if (isLoading) {
            GlobalLoadingDialog()
        }
    }
}

/**
 * 统一风格的输入框组件
 */
@Composable
fun StyledInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            isError = error != null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorBorderColor = MaterialTheme.colorScheme.error
            )
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
