package com.example.xiaomaotai.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DataManager
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    dataManager: DataManager,
    onResetSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 手势返回处理
    BackHandler {
        onBack()
    }
    
    // 倒计时逻辑
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }
    
    // 邮箱格式验证函数
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "忘记密码",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(48.dp)) // 平衡左侧图标
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                // 用户名输入框
                OutlinedTextField(
                    value = username,
                    onValueChange = { newValue ->
                        // 限制用户名：只允许字母、数字、下划线，长度不超过20位
                        if (newValue.length <= 20 && newValue.all { it.isLetterOrDigit() || it == '_' }) {
                            username = newValue
                        }
                    },
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
                
                // 邮箱输入框
                OutlinedTextField(
                    value = email,
                    onValueChange = { newValue ->
                        // 限制邮箱长度不超过50位，去除空格
                        val trimmedValue = newValue.trim()
                        if (trimmedValue.length <= 50) {
                            email = trimmedValue
                        }
                    },
                    label = { 
                        Text(
                            "邮箱",
                            fontSize = 13.sp
                        ) 
                    },
                    placeholder = { 
                        Text(
                            "请输入注册账号时的邮箱",
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
                
                // 验证码输入框和获取按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { newValue ->
                            // 只允许数字，最多6位
                            val filteredValue = newValue.filter { it.isDigit() }.take(6)
                            verificationCode = filteredValue
                        },
                        label = { 
                            Text(
                                "验证码",
                                fontSize = 13.sp
                            ) 
                        },
                        placeholder = { 
                            Text(
                                "请输入验证码",
                                fontSize = 13.sp
                            ) 
                        },
                        modifier = Modifier.weight(1f),
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
                    
                    Button(
                        onClick = {
                            if (username.isNotEmpty() && email.isNotEmpty() && isValidEmail(email)) {
                                scope.launch {
                                    val result = dataManager.sendVerificationCode(username, email)
                                    if (result.isSuccess) {
                                        countdown = 60
                                        Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "发送失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                when {
                                    username.isEmpty() -> Toast.makeText(context, "请输入用户名", Toast.LENGTH_SHORT).show()
                                    email.isEmpty() -> Toast.makeText(context, "请输入邮箱", Toast.LENGTH_SHORT).show()
                                    !isValidEmail(email) -> Toast.makeText(context, "请输入正确的邮箱格式", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = countdown == 0 && username.isNotEmpty() && email.isNotEmpty() && isValidEmail(email),
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 100.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s" else "获取验证码",
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // 新密码输入框
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newValue ->
                        // 只允许英文字母、数字和特定特殊符号，长度不超过30位
                        val filteredValue = newValue.filter { char ->
                            char.isLetter() || char.isDigit() || char in "!@#$%^&*()_+-=[]{}|;:,.<>?"
                        }.take(30)
                        newPassword = filteredValue
                    },
                    label = { 
                        Text(
                            "新密码",
                            fontSize = 13.sp
                        ) 
                    },
                    placeholder = { 
                        Text(
                            "英文字母、数字、特殊符号",
                            fontSize = 13.sp
                        ) 
                    },
                    visualTransformation = PasswordVisualTransformation(),
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
                
                // 确认密码输入框
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { newValue ->
                        // 只允许英文字母、数字和特定特殊符号，长度不超过30位
                        val filteredValue = newValue.filter { char ->
                            char.isLetter() || char.isDigit() || char in "!@#$%^&*()_+-=[]{}|;:,.<>?"
                        }.take(30)
                        confirmPassword = filteredValue
                    },
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
                    visualTransformation = PasswordVisualTransformation(),
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
                
                // 密码匹配校验提示
                if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                    Text(
                        text = "两次密码输入不一致",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                
                // 错误提示
                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 确认按钮
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = ""
                            
                            // 验证输入
                            if (username.isEmpty()) {
                                errorMessage = "请输入用户名"
                                isLoading = false
                                return@launch
                            }
                            
                            if (email.isEmpty()) {
                                errorMessage = "请输入邮箱"
                                isLoading = false
                                return@launch
                            }
                            
                            if (!isValidEmail(email)) {
                                errorMessage = "请输入正确的邮箱格式"
                                isLoading = false
                                return@launch
                            }
                            
                            if (verificationCode.isEmpty()) {
                                errorMessage = "请输入验证码"
                                isLoading = false
                                return@launch
                            }
                            
                            if (verificationCode.length != 6) {
                                errorMessage = "验证码必须是6位数字"
                                isLoading = false
                                return@launch
                            }
                            
                            if (newPassword.isEmpty()) {
                                errorMessage = "请输入新密码"
                                isLoading = false
                                return@launch
                            }
                            
                            if (confirmPassword.isEmpty()) {
                                errorMessage = "请确认新密码"
                                isLoading = false
                                return@launch
                            }
                            
                            if (newPassword != confirmPassword) {
                                errorMessage = "两次输入的密码不一致"
                                isLoading = false
                                return@launch
                            }
                            
                            // 验证验证码并重置密码
                            val result = dataManager.resetPasswordWithCode(username, email, verificationCode, newPassword)
                            if (result.isSuccess) {
                                Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                                onResetSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "重置失败，请稍后重试"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && email.isNotEmpty() && verificationCode.isNotEmpty() &&
                            newPassword.isNotEmpty() && confirmPassword.isNotEmpty() &&
                            isValidEmail(email) && verificationCode.length == 6,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确认", fontSize = 14.sp)
                }
            }
        }
        
        // 全屏加载指示器
        if (isLoading) {
            GlobalLoadingDialog()
        }
    }
}
