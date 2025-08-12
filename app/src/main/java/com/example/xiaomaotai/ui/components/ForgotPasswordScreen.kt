package com.example.xiaomaotai.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.DataManager
import com.example.xiaomaotai.ValidationUtils
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

    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var verificationCodeError by remember { mutableStateOf<String?>(null) }
    var serverErrorMessage by remember { mutableStateOf("") }

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

    fun validateAllFields(): Boolean {
        val usernameValidation = ValidationUtils.validateUsername(username)
        val emailValidation = ValidationUtils.validateEmail(email)
        val passwordValidation = ValidationUtils.validatePassword(newPassword, username)
        val confirmPasswordValidation = if (newPassword != confirmPassword) "两次输入的密码不一致" else null
        val codeValidation = if (verificationCode.isEmpty()) "验证码不能为空" else if (verificationCode.length != 6) "验证码必须是6位" else null

        usernameError = if (!usernameValidation.isValid) usernameValidation.message else null
        emailError = if (!emailValidation.isValid) emailValidation.message else null
        newPasswordError = if (!passwordValidation.isValid) passwordValidation.message else null
        confirmPasswordError = confirmPasswordValidation
        verificationCodeError = codeValidation

        return usernameError == null && emailError == null && newPasswordError == null && confirmPasswordError == null && verificationCodeError == null
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
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text(text = "忘记密码", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(48.dp)) // 平衡左侧图标
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                // 用户名输入框
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.filter { char -> char.isLetterOrDigit() }.take(20)
                        usernameError = null
                        serverErrorMessage = ""
                    },
                    label = { Text("用户名", fontSize = 13.sp) },
                    placeholder = { Text("请输入用户名", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = {
                        if (usernameError != null) {
                            Text(usernameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 邮箱输入框
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                        serverErrorMessage = ""
                    },
                    label = { Text("邮箱", fontSize = 13.sp) },
                    placeholder = { Text("请输入注册账号时的邮箱", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = emailError != null,
                    supportingText = {
                        if (emailError != null) {
                            Text(emailError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 验证码输入框和获取按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = {
                            verificationCode = it.filter { char -> char.isDigit() }.take(6)
                            verificationCodeError = null
                            serverErrorMessage = ""
                        },
                        label = { Text("验证码", fontSize = 13.sp) },
                        placeholder = { Text("请输入6位验证码", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = verificationCodeError != null,
                        supportingText = {
                            verificationCodeError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val usernameValidation = ValidationUtils.validateUsername(username)
                            val emailValidation = ValidationUtils.validateEmail(email)
                            usernameError = if (!usernameValidation.isValid) usernameValidation.message else null
                            emailError = if (!emailValidation.isValid) emailValidation.message else null

                            if (usernameValidation.isValid && emailValidation.isValid) {
                                scope.launch {
                                    isLoading = true
                                    val result = dataManager.sendVerificationCode(username, email)
                                    if (result.isSuccess) {
                                        countdown = 60
                                        Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                                    } else {
                                        serverErrorMessage = result.exceptionOrNull()?.message ?: "发送失败"
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        enabled = countdown == 0 && !isLoading,
                        modifier = Modifier.height(56.dp).widthIn(min = 100.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (countdown > 0) "${countdown}s" else "获取验证码", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 新密码输入框
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it.take(20)
                        newPasswordError = null
                        serverErrorMessage = ""
                    },
                    label = { Text("新密码", fontSize = 13.sp) },
                    placeholder = { Text("6-12位英文或数字", fontSize = 13.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = newPasswordError != null,
                    supportingText = {
                        if (newPasswordError != null) {
                            Text(newPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 确认密码输入框
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it.take(20)
                        confirmPasswordError = null
                        serverErrorMessage = ""
                    },
                    label = { Text("确认密码", fontSize = 13.sp) },
                    placeholder = { Text("再输入一遍密码", fontSize = 13.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPasswordError != null,
                    supportingText = {
                        if (confirmPasswordError != null) {
                            Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                // 服务器错误提示
                if (serverErrorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serverErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 确认按钮
                Button(
                    onClick = {
                        if (validateAllFields()) {
                            scope.launch {
                                isLoading = true
                                serverErrorMessage = ""
                                val result = dataManager.resetPasswordWithCode(username, email, verificationCode, newPassword)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                                    onResetSuccess()
                                } else {
                                    serverErrorMessage = result.exceptionOrNull()?.message ?: "重置失败，请稍后重试"
                                }
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
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
