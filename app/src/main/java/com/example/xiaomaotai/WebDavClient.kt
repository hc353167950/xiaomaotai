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

    /**
     * 规范化 base URL：去掉尾部斜杠，并对路径部分逐段做 URL 编码。
     *
     * 主机与端口不编码（`https://host:8443` 里的冒号必须保留），
     * 只编码 `/` 之后的路径段——服务器地址可能是 `.../dav/我的同步/` 这种中文目录。
     */
    private fun base(): String {
        val raw = serverUrl.trim().trimEnd('/')
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd < 0) return raw

        val prefix = raw.substring(0, schemeEnd + 3)
        val rest = raw.substring(schemeEnd + 3)
        val parts = rest.split('/')

        val encoded = parts.mapIndexed { index, segment ->
            if (index == 0 || segment.isEmpty()) segment
            else java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }.joinToString("/")

        return prefix + encoded
    }

    /**
     * 拼接完整 URL。
     *
     * 两个必须小心的点：
     *
     * 1. **尾斜杠必须原样保留**。`dir/` 和 `dir` 在 WebDAV 里是两个不同的资源：
     *    前者表示目录，后者表示无扩展名的文件。早先用 split→join 展开路径，
     *    末尾空段被 join 丢弃，导致 MKCOL 请求丢了尾斜杠，服务端一律回 409
     *    「父目录不存在」——这正是这条路走不通的真正原因。
     *
     * 2. **逐段编码但保留分隔符**。服务器地址里可能有空格或中文
     *    （如坚果云的 `/dav/我的同步/`），不编码会请求失败。
     */
    private fun url(path: String): String {
        val trimmed = path.trimStart('/')
        val endsWithSlash = trimmed.endsWith("/")
        val encoded = trimmed
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        val suffix = if (endsWithSlash && encoded.isNotEmpty()) "/" else ""
        return "${base()}/$encoded$suffix"
    }

    /**
     * 逐级创建目录（MKCOL），失败不阻断——真正的成败由随后的 PUT 判定。
     *
     * 踩过的三个坑（都会表现为「父目录不存在 / 创建目录失败」）：
     *
     * 1. **MKCOL 必须显式带 `Content-Length: 0`**。
     *    OkHttp 对无 body 的自定义方法不保证发送该头，而 nginx-dav 类后端
     *    （infini-cloud 就在用）缺少它时直接回 409，看起来像是「父目录不存在」。
     *
     * 2. **尾斜杠两种形式都要试**。有的服务端只认 `dir/`，有的只认 `dir`。
     *
     * 3. **对已存在目录，状态码五花八门**：405 / 301 / 200 / 204 / 409 都见过，
     *    所以一律不当错误，只记日志。
     *
     * 只有 401 是真正的致命错误（凭据不对），其余交给 PUT 决定。
     */
    suspend fun ensureDirectory(path: String): Result<Unit> = runCatching {
        var current = ""
        for (segment in path.trim('/').split('/')) {
            if (segment.isBlank()) continue
            current = if (current.isEmpty()) segment else "$current/$segment"

            if (directoryExists("$current/")) continue

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

    /**
     * 发一次 MKCOL，返回状态码。
     *
     * 关键点：显式声明空 body（Content-Length: 0）。
     * WebDAV 规范要求 MKCOL 请求体为空，很多服务端靠这个头判断请求合法。
     */
    private suspend fun mkcol(path: String): Int {
        val response = client.request(url(path)) {
            method = HttpMethod("MKCOL")
            header(HttpHeaders.Authorization, authHeader())
            header(HttpHeaders.ContentLength, "0")
            setBody(ByteArray(0))
        }
        return response.status.value
    }

    /**
     * 用 PROPFIND（Depth: 0）探测目录是否已存在。
     *
     * 比「先 MKCOL 再看状态码」可靠得多：已存在返回 207，不存在返回 404。
     * 探测失败（服务端不支持 PROPFIND）时按「不存在」处理，不影响后续流程。
     */
    private suspend fun directoryExists(path: String): Boolean {
        return try {
            val response = client.request(url(path)) {
                method = HttpMethod("PROPFIND")
                header(HttpHeaders.Authorization, authHeader())
                header("Depth", "0")
                header(HttpHeaders.ContentLength, "0")
                setBody(ByteArray(0))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.d(TAG, "PROPFIND 探测失败，按不存在处理: ${e.message}")
            false
        }
    }

    /**
     * 覆盖写入文本文件（一次 PUT = 一个完整快照）。
     *
     * 如果 PUT 报 409（父目录不存在），说明前面的 MKCOL 没真正建成目录——
     * 这时补建一次再重试，避免把「目录没建好」暴露成「备份失败」。
     */
    suspend fun putText(path: String, content: String): Result<Unit> = runCatching {
        var response = putOnce(path, content)

        // 409 = 父目录不存在。补建目录后重试一次，仍失败才向用户报错。
        if (response.status.value == 409) {
            val dir = path.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) {
                Log.d(TAG, "PUT 409，补建目录 $dir 后重试")
                ensureDirectory(dir)
                response = putOnce(path, content)
            }
        }

        if (response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.OK
        ) {
            // 带上真实 URL 与状态码，便于定位是「地址填错」还是「服务端拒绝」
            throw WebDavException(
                "上传失败 ${response.status.value}（${url(path)}）"
            )
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
