package com.example.xiaomaotai

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.ContentType
import java.util.Base64

/**
 * WebDAV 客户端：只做协议层的事——目录创建、整文件读写。
 *
 * 为什么必须用 OkHttp 引擎：`ktor-client-android` 底层是 HttpURLConnection，
 * 它只允许 GET/POST/HEAD/OPTIONS/PUT/DELETE/TRACE，WebDAV 的 PROPFIND/MKCOL
 * 会被直接拒绝。OkHttp 允许任意 HTTP 方法。
 *
 * 接口刻意收窄为三个动词：[ensureDirectory]、[putText]、[getText]。
 * 同步策略（覆盖/合并）不属于本模块，见 [WebDavSync]。
 */
class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        expectSuccess = false
    }

    private fun authHeader(): String {
        val raw = "$username:$password"
        return "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    /** 规范化 base URL：去掉尾部斜杠 */
    private fun base(): String = serverUrl.trimEnd('/')

    /** 拼接完整 URL，并做最小化的路径转义（保留已编码部分） */
    private fun url(path: String): String = "${base()}/${path.trimStart('/')}"

    /**
     * 逐级创建目录（MKCOL）。
     * 已存在（405 Method Not Allowed）视为成功，这是 WebDAV 的通行语义。
     */
    suspend fun ensureDirectory(path: String): Result<Unit> = runCatching {
        var current = ""
        for (segment in path.trim('/').split('/')) {
            if (segment.isBlank()) continue
            current = if (current.isEmpty()) segment else "$current/$segment"
            val response = client.request(url(current)) {
                method = HttpMethod("MKCOL")
                header(HttpHeaders.Authorization, authHeader())
            }
            // 201 创建成功 / 405 已存在 / 409 父目录缺失（理论上不会到这里）
            if (response.status != HttpStatusCode.Created &&
                response.status != HttpStatusCode.MethodNotAllowed &&
                response.status != HttpStatusCode.OK
            ) {
                throw WebDavException("创建目录失败 ${response.status.value}: $current")
            }
        }
    }

    /** 覆盖写入文本文件（一次 PUT = 一个完整快照） */
    suspend fun putText(path: String, content: String): Result<Unit> = runCatching {
        val response = client.request(url(path)) {
            method = HttpMethod.Put
            header(HttpHeaders.Authorization, authHeader())
            contentType(ContentType.Application.Json)
            setBody(content.toByteArray(Charsets.UTF_8))
        }
        if (response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.OK
        ) {
            throw WebDavException("上传失败 ${response.status.value}")
        }
    }

    /**
     * 读取文本文件。文件不存在返回 null（区别于网络错误）。
     */
    suspend fun getText(path: String): Result<String?> = runCatching {
        val response: HttpResponse = client.get(url(path)) {
            header(HttpHeaders.Authorization, authHeader())
        }
        when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> response.bodyAsText()
            response.status == HttpStatusCode.Unauthorized ->
                throw WebDavException("认证失败，请检查用户名与密码")
            else -> throw WebDavException("读取失败 ${response.status.value}")
        }
    }

    /** 探测连接与认证是否可用（不关心文件是否存在） */
    suspend fun testConnection(): Result<Unit> = runCatching {
        val response = client.get(url("")) {
            header(HttpHeaders.Authorization, authHeader())
        }
        when {
            response.status == HttpStatusCode.Unauthorized ->
                throw WebDavException("认证失败，请检查用户名与密码")
            response.status.value in 200..499 -> Unit
            else -> throw WebDavException("连接失败 ${response.status.value}")
        }
    }

    fun close() {
        try {
            client.close()
        } catch (e: Exception) {
            Log.w(TAG, "关闭客户端失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WebDavClient"
    }
}

class WebDavException(message: String) : Exception(message)
