package com.example.xiaomaotai

object ValidationUtils {

    // 校验账号（英文、数字，6-20位）
    fun validateUsername(username: String): ValidationResult {
        if (username.isEmpty()) {
            return ValidationResult(false, "用户名不能为空")
        }
        if (username.length < 6 || username.length > 20) {
            return ValidationResult(false, "用户名长度必须在6-20位之间")
        }
        if (!username.matches(Regex("^[a-zA-Z0-9]+$"))) {
            return ValidationResult(false, "用户名只能包含英文和数字")
        }
        return ValidationResult(true, "")
    }

    // 校验密码（6-20位英文数字，不能与账号相同）
    fun validatePassword(password: String, account: String = ""): ValidationResult {
        if (password.isEmpty()) {
            return ValidationResult(false, "密码不能为空")
        }
        if (password.length < 6 || password.length > 20) {
            return ValidationResult(false, "密码长度必须在6-20位之间")
        }
        if (!password.matches(Regex("^[a-zA-Z0-9]+$"))) {
            return ValidationResult(false, "密码只能包含英文和数字")
        }
        if (password == account) {
            return ValidationResult(false, "密码不能与账号相同")
        }
        return ValidationResult(true, "")
    }

    // 校验昵称（不能为空，最多10个字符）
    fun validateNickname(nickname: String): ValidationResult {
        if (nickname.isEmpty()) {
            return ValidationResult(false, "昵称不能为空")
        }
        if (nickname.length > 10) {
            return ValidationResult(false, "昵称不能超过10个字符")
        }
        return ValidationResult(true, "")
    }

    // 校验邮箱
    fun validateEmail(email: String): ValidationResult {
        if (email.isEmpty()) {
            return ValidationResult(false, "邮箱不能为空")
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return ValidationResult(false, "请输入正确的邮箱格式")
        }
        return ValidationResult(true, "")
    }

    // 校验纪念日名称
    fun validateEventName(eventName: String?): ValidationResult {
        if (eventName.isNullOrEmpty()) {
            return ValidationResult(false, "事件名称不能为空")
        }
        return ValidationResult(true, "")
    }
}

data class ValidationResult(val isValid: Boolean, val message: String)
