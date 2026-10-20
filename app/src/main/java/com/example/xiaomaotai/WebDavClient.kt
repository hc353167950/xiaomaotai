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
     *
     * 这里刻意「尽力而为、绝不阻断」：不同 WebDAV 服务端对 MKCOL 的实现差异极大
     * （infini-cloud 对 `dir/` 与 `dir` 返回不同状态码，部分服务端对已存在目录返回 409）。
     * 因此：
     * - 401 才是真正的致命错误（凭据不对）；
     * - 其他任何状态码都只记日志，继续下一步 —— 真正的成败由随后的 PUT 决定。
     *
     * 每个目录会依次尝试「带尾斜杠」和「不带尾斜杠」两种形式，任一成功即可。
     */
    suspend fun ensureDirectory(path: String): Result<Unit> = runCatching {
        var current = ""
        for (segment in path.trim('/').split('/')) {
            if (segment.isBlank()) continue
            current = if (current.isEmpty()) segment else "$current/$segment"

            val withSlash = mkcol("$current/")
            if (withSlash == HttpStatusCode.Unauthorized.value) {
                throw WebDavException("认证失败，请检查用户名与密码")
            }
            if (withSlash in MKCOL_OK) continue

            // 带斜杠不行就再试一次不带斜杠（有些服务端只认其中一种）
            val withoutSlash = mkcol(current)
            if (withoutSlash == HttpStatusCode.Unauthorized.value) {
                throw WebDavException("认证失败，请检查用户名与密码")
            }
            if (withoutSlash !in MKCOL_OK) {
                Log.w(TAG, "MKCOL $current 返回 $withSlash / $withoutSlash，交由 PUT 判定")
            }
        }
    }

    /** 发一次 MKCOL，返回状态码 */
    private suspend fun mkcol(path: String): Int {
        val response = client.request(url(path)) {
            method = HttpMethod("MKCOL")
            header(HttpHeaders.Authorization, authHeader())
        }
        return response.status.value
    }

    /**
     * 覆盖写入文本文件（一次 PUT = 一个完整快照）。
     *
     * 如果 PUT 报 409（父目录不存在），说明前面的 MKCOL 没真正建成目录——
     * 这时补建一次再重试，避免把「目录没建好」暴露成「备份失败」。
     */
    suspend fun putText(path: String, content: String): Result<Unit> = runCatching {
        var response = putOnce(path, content)
        if (response.status.value == 409) {
            val dir = path.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) {
                ensureDirectory(dir)
                response = putOnce(path, content)
            }
        }
        if (response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.OK
        ) {
            throw WebDavException("上传失败 ${response.status.value}")
        }
    }

    private suspend fun putOnce(path: String, content: String): HttpResponse =
        client.request(url(path)) {
            method = HttpMethod.Put
            header(HttpHeaders.Authorization, authHeader())
            contentType(ContentType.Application.Json)
            setBody(content.toByteArray(Charsets.UTF_8))
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
            response.status.value in 200..299 -> response.bodyAsText()
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

        /** MKCOL 视为「目录可用」的状态码（创建成功、已存在、需重定向等） */
        private val MKCOL_OK = setOf(200, 201, 204, 301, 302, 405)
    }
}

class WebDavException(message: String) : Exception(message)
