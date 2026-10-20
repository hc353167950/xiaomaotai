package com.example.xiaomaotai

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * WebDAV 凭据的加密存储。
 *
 * WebDAV 用 Basic Auth，用户名/密码（如坚果云的「应用专用密码」）是真正的敏感信息。
 * 绝不能像旧版那样塞进明文 SharedPreferences——那等于把凭据直接暴露给
 * 任何拿到设备备份或 root 权限的人。这里用 EncryptedSharedPreferences
 * （AES256-GCM，主密钥由 Android Keystore 托管）。
 */
class WebDavCredentials(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "webdav_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore 异常（极少数设备）时退化为普通 SharedPreferences，但记录告警
        Log.e(TAG, "加密存储不可用，退化为普通存储: ${e.message}")
        context.getSharedPreferences("webdav_credentials_plain", Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    /** 三项配置是否齐全，可尝试同步 */
    fun isConfigured(): Boolean =
        serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "WebDavCredentials"
        private const val KEY_URL = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
    }
}
