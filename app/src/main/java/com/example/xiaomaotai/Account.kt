package com.example.xiaomaotai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson

/**
 * 账号模块：登录态、注册、验证码、密码重置的唯一出口。
 * 从 DataManager 拆出——账号生命周期与事件数据是两个独立关注点。
 *
 * 登录/登出对事件数据的联动（拉取云端、同步离线、清缓存）由 [EventStore]
 * 通过 [onLogin]/[onLogout] 回调衔接，本模块不持有事件数据。
 */
class Account(
    context: Context,
    private val network: NetworkDataManager = NetworkDataManager()
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** 登录成功后的联动（EventStore 注入：拉云端数据+同步离线事件） */
    var onLogin: (suspend (User) -> Unit)? = null

    /** 登出后的联动（EventStore 注入：清空事件缓存） */
    var onLogout: (() -> Unit)? = null

    fun getCurrentUser(): User? {
        val userJson = prefs.getString("current_user", null) ?: return null
        return try {
            gson.fromJson(userJson, User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "解析用户数据失败: ${e.message}")
            null
        }
    }

    fun isLoggedIn(): Boolean = getCurrentUser() != null

    fun getCurrentUserId(): String? = getCurrentUser()?.id

    suspend fun login(username: String, password: String): Result<User> {
        val result = network.loginUser(username, password)
        if (result.isSuccess) {
            val user = result.getOrNull()!!
            saveUserLocally(user)
            onLogin?.invoke(user)
        }
        return result
    }

    fun logout() {
        prefs.edit().remove("current_user").commit()
        onLogout?.invoke()
    }

    suspend fun register(username: String, nickname: String, email: String, password: String): Result<User> {
        return network.registerUser(username, nickname, email, password)
    }

    // ---------- 验证码 / 密码重置 ----------

    suspend fun sendVerificationCode(username: String, email: String): Result<String> =
        network.sendVerificationCode(username, email)

    suspend fun resetPasswordWithCode(username: String, email: String, code: String, newPassword: String): Result<String> =
        network.resetPasswordWithCode(username, email, code, newPassword)

    suspend fun verifyCode(username: String, email: String, code: String): Result<String> =
        network.verifyCode(username, email, code)

    // ---------- 上次登录账号记忆 ----------

    fun getLastUsername(): String = prefs.getString("last_login_username", "") ?: ""

    private fun saveUserLocally(user: User) {
        prefs.edit()
            .putString("current_user", gson.toJson(user))
            .putString("last_login_username", user.username)
            .commit()
    }

    companion object {
        private const val TAG = "Account"
    }
}
