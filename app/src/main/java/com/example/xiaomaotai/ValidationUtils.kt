package com.example.xiaomaotai

object ValidationUtils {

    // 校验账号（6-12位英文数字组合）
    fun validateAccount(account: String?): ValidationResult {
        if (account.isNullOrEmpty()) {
            return ValidationResult(false, "账号不能为空")
        }
        if (account.length < 6 || account.length > 12) {
            return ValidationResult(false, "账号长度必须在6-12位之间")
        }
        if (!account.matches(Regex("^[a-zA-Z0-9]+$"))) {
            return ValidationResult(false, "账号只能包含英文和数字")
        }
        return ValidationResult(true, "")
    }

    // 校验密码（6-12位英文数字，不能与账号相同）
    fun validatePassword(password: String?, account: String = ""): ValidationResult {
        if (password.isNullOrEmpty()) {
            return ValidationResult(false, "密码不能为空")
        }
        if (password.length < 6 || password.length > 12) {
            return ValidationResult(false, "密码长度必须在6-12位之间")
        }
        if (!password.matches(Regex("^[a-zA-Z0-9]+$"))) {
            return ValidationResult(false, "密码只能包含英文和数字")
        }
        if (password == account) {
            return ValidationResult(false, "密码不能与账号相同")
        }
        return ValidationResult(true, "")
    }

    // 校验昵称（至少2位，支持英文数字中文）
    fun validateUsername(username: String?): ValidationResult {
        if (username.isNullOrEmpty()) {
            return ValidationResult(false, "昵称不能为空")
        }
        if (username.length < 2) {
            return ValidationResult(false, "昵称至少需要2位字符")
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