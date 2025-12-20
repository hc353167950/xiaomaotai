package com.example.xiaomaotai.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val scrollState = rememberScrollState()

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
        val confirmPasswordValidation = when {
            confirmPassword.isEmpty() -> "请再次输入密码"
            newPassword != confirmPassword -> "两次输入的密码不一致"
            else -> null
        }
        val codeValidation = if (verificationCode.isEmpty()) "验证码不能为空" else if (verificationCode.length != 6) "验证码必须是6位" else null

        usernameError = if (!usernameValidation.isValid) usernameValidation.message else null
        emailError = if (!emailValidation.isValid) emailValidation.message else null
        newPasswordError = if (!passwordValidation.isValid) passwordValidation.message else null
        confirmPasswordError = confirmPasswordValidation
        verificationCodeError = codeValidation

        return usernameError == null && emailError == null && newPasswordError == null && confirmPasswordError == null && verificationCodeError == null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .imePadding()
        ) {
            // 顶部导航栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "忘记密码",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            ) {
                // 用户名输入框
                StyledInputField(
                    label = "用户名",
                    value = username,
                    onValueChange = {
                        username = it.filter { char -> char.isLetterOrDigit() }.take(20)
                        usernameError = null
                        serverErrorMessage = ""
                    },
                    placeholder = "请输入用户名",
                    error = usernameError
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 邮箱输入框
                StyledInputField(
                    label = "邮箱",
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                        serverErrorMessage = ""
                    },
                    placeholder = "请输入注册时的邮箱",
                    error = emailError,
                    keyboardType = KeyboardType.Email
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 验证码输入框和获取按钮
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "验证码",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = {
                                verificationCode = it.filter { char -> char.isDigit() }.take(6)
                                verificationCodeError = null
                                serverErrorMessage = ""
                            },
                            placeholder = {
                                Text(
                                    "请输入6位验证码",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = verificationCodeError != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                errorBorderColor = MaterialTheme.colorScheme.error
                            )
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
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = if (countdown > 0) "${countdown}s" else "获取验证码",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (verificationCodeError != null) {
                        Text(
                            text = verificationCodeError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 新密码输入框
                StyledInputField(
                    label = "新密码",
                    value = newPassword,
                    onValueChange = {
                        newPassword = it.take(20)
                        newPasswordError = null
                        serverErrorMessage = ""
                    },
                    placeholder = "6-12位英文或数字",
                    error = newPasswordError,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 确认密码输入框
                StyledInputField(
                    label = "确认密码",
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it.take(20)
                        confirmPasswordError = null
                        serverErrorMessage = ""
                    },
                    placeholder = "再输入一遍密码",
                    error = confirmPasswordError,
                    isPassword = true
                )

                // 服务器错误提示
                if (serverErrorMessage.isNotEmpty()) {
                    Text(
                        text = serverErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 确认按钮
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
                            val result = dataManager.resetPasswordWithCode(username, email, verificationCode, newPassword)
                            if (result.isSuccess) {
                                Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                                onResetSuccess()
                            } else {
                                serverErrorMessage = result.exceptionOrNull()?.message ?: "重置失败，请稍后重试"
                            }
                            isLoading = false
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
                        "确认修改",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 底部额外空间，确保错误提示出现时有足够滚动空间
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // 全屏加载指示器
        if (isLoading) {
            GlobalLoadingDialog()
        }
    }
}
