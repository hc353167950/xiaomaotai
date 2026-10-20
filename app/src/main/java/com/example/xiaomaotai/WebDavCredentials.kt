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
        set(value) = prefs.edit().putString(KEY_URL, sanitizeUrl(value)).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, sanitizeLine(value)).apply()

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

        /**
         * 清洗服务器地址。
         *
         * 用户从浏览器/网盘帮助页复制地址时，极易带上换行和多余内容，例如：
         * ```
         * https://soya.infini-cloud.net/dav/
         * 353167950
         * ```
         * 这段文本若原样编码进 URL，换行符会变成 %0A，请求路径就成了
         * /dav%0A353167950/ ，服务端一律 404/409 —— 表现为「上传失败」。
         *
         * 处理策略：
         * 1. 去掉 BOM / 零宽字符 / 不换行空格；
         * 2. 按任意空白与换行切开，只保留第一段（真正的 URL）；
         * 3. 丢弃空结果。
         */
        fun sanitizeUrl(input: String): String {
            val cleaned = input
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\u00A0", " ")
                .trim()

            return cleaned
                .split(Regex("[\\s\\r\\n]+"))
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?: ""
        }

        /** 单行文本清洗：去掉所有空白与控制字符（用户名不该含换行） */
        fun sanitizeLine(input: String): String =
            input.replace(Regex("[\\s\\r\\n\\u0000-\\u001F]"), "")
    }
}
