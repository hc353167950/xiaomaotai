package com.example.xiaomaotai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * WebDAV 同步深模块：三个动词对应三个功能。
 *
 * - [backupToCloud]   本地全量 → 覆盖云端
 * - [restoreFromCloud]云端全量 → 覆盖本地（覆盖前自动本地快照）
 * - [mergeWithCloud]  云端 ∪ 本地，按「名称 + 日期」去重，两边都保留
 *
 * 设计取舍（单设备场景）：
 * 事件没有跨设备的稳定标识需求，因此合并以 `eventName + eventDate` 作为
 * 同一事件的判定键。这意味着「把某条事件改了日期」会被视为两条不同事件。
 * 在单设备手动同步的使用方式下，这个边界是可接受的。
 */
class WebDavSync(
    private val context: Context,
    private val eventStore: EventStore,
    private val credentials: WebDavCredentials
) {

    private val gson = Gson()

    // ---------- 结果类型 ----------

    /** 合并预演结果：让用户在写入前知道会发生什么 */
    data class MergePlan(
        val localCount: Int,
        val remoteCount: Int,
        val addedFromRemote: Int,
        val keptLocalOnly: Int,
        val unchanged: Int
    ) {
        val mergedCount: Int get() = unchanged + keptLocalOnly + addedFromRemote
    }

    // ---------- 三个功能 ----------

    /**
     * 备份到云端：本地全量覆盖云端。
     *
     * **空数据保护**：本地 0 条时拒绝执行。
     *
     * 这是必须的守卫。典型丢数据场景：用户换手机 → 新装 App → 本地空 →
     * 手滑点「备份」→ 云端原有备份被空列表覆盖，且不可逆。
     * 覆盖式备份永远不该允许「用空覆盖有」。
     *
     * 另外在覆盖前把云端旧内容快照到 [SNAPSHOT_FILE]，留一条退路。
     */
    suspend fun backupToCloud(): Result<String> = withClient { client ->
        client.ensureDirectory(DIR).getOrThrow()

        val events = eventStore.persistedEvents()
        if (events.isEmpty()) {
            throw WebDavException(
                "本地没有纪念日，已阻止备份。\n" +
                    "覆盖式备份会用空数据清掉云端原有备份，因此本地为空时不允许执行。"
            )
        }

        // 覆盖前先给云端旧内容留一份快照（不存在或已损坏都无妨）
        snapshotCloud(client)

        val payload = SyncPayload(
            version = PAYLOAD_VERSION,
            savedAt = nowIso(),
            events = events
        )
        client.putText(FILE, gson.toJson(payload)).getOrThrow()
        Result.success("已备份 ${events.size} 条纪念日到云端")
    }

    /** 把云端现有 events.json 另存一份，最多保留最近一次（避免无限增长） */
    private suspend fun snapshotCloud(client: WebDavClient) {
        try {
            val current = client.getText(FILE).getOrNull() ?: return
            if (current.isBlank()) return
            client.putText(SNAPSHOT_FILE, current)
            Log.d(TAG, "已保存云端快照")
        } catch (e: Exception) {
            Log.w(TAG, "云端快照失败（不影响备份）: ${e.message}")
        }
    }

    /**
     * 从云端恢复：云端全量覆盖本地。
     * 覆盖前先把当前本地数据写一份快照，防止误操作导致数据全丢。
     */
    suspend fun restoreFromCloud(): Result<String> = withClient { client ->
        val raw = client.getText(FILE).getOrThrow()
            ?: return@withClient Result.failure(WebDavException("云端还没有备份文件"))

        val remote = decodeEvents(raw).getOrThrow()
        val localCount = eventStore.persistedEvents().size
        snapshotLocal(localCount)

        eventStore.replaceAll(remote)
        Result.success("已从云端恢复 ${remote.size} 条纪念日（覆盖前已保存本地快照）")
    }

    /**
     * 预演合并：只读，不写入任何数据。供 UI 弹确认框用。
     */
    suspend fun previewMerge(): Result<MergePlan> = withClientGeneric { client ->
        val raw = client.getText(FILE).getOrThrow()
        val local = eventStore.persistedEvents()
        val remote = if (raw == null) emptyList() else decodeEvents(raw).getOrThrow()

        val localKeys = local.map { mergeKey(it) }.toSet()
        val remoteKeys = remote.map { mergeKey(it) }.toSet()

        val addedFromRemote = remote.count { mergeKey(it) !in localKeys }
        val keptLocalOnly = local.count { mergeKey(it) !in remoteKeys }
        val unchanged = local.count { mergeKey(it) in remoteKeys }

        Result.success(
            MergePlan(
                localCount = local.size,
                remoteCount = remote.size,
                addedFromRemote = addedFromRemote,
                keptLocalOnly = keptLocalOnly,
                unchanged = unchanged
            )
        )
    }

    /**
     * 智能合并：云端 ∪ 本地，按「名称 + 日期」去重。
     *
     * 规则：
     * - 只在云端有 → 拉回本地
     * - 只在本地有 → 上传云端
     * - 两边都有 → 保留本地版本（背景色/排序是用户最近调过的）
     *
     * 合并结果同时写回本地与云端，保证两边一致。sortOrder 重新编号避免冲突。
     */
    suspend fun mergeWithCloud(): Result<String> = withClient { client ->
        client.ensureDirectory(DIR).getOrThrow()

        val raw = client.getText(FILE).getOrThrow()
        val local = eventStore.persistedEvents()
        val remote = if (raw == null) emptyList() else decodeEvents(raw).getOrThrow()

        val localKeys = local.map { mergeKey(it) }.toSet()
        val remoteKeys = remote.map { mergeKey(it) }.toSet()

        // 本地优先：先放本地全部，再追加云端独有的
        val merged = mutableListOf<Event>()
        merged.addAll(local)
        remote.forEach { r ->
            if (mergeKey(r) !in localKeys) merged.add(r)
        }

        // sortOrder 重新编号：按现有降序顺序映射为连续序号，避免两边序号打架
        val normalized = merged
            .sortedByDescending { it.sortOrder }
            .mapIndexed { index, e -> e.copy(sortOrder = merged.size - 1 - index) }

        val addedFromRemote = remote.count { mergeKey(it) !in localKeys }
        val keptLocalOnly = local.count { mergeKey(it) !in remoteKeys }

        eventStore.replaceAll(normalized)

        val payload = SyncPayload(
            version = PAYLOAD_VERSION,
            savedAt = nowIso(),
            events = normalized
        )
        client.putText(FILE, gson.toJson(payload)).getOrThrow()

        Result.success(
            "合并完成：本地 ${local.size} 条 + 云端新增 $addedFromRemote 条" +
                " = ${normalized.size} 条（本地独有 $keptLocalOnly 条已上传）"
        )
    }

    /** 测试连接 */
    suspend fun testConnection(): Result<String> = withClient { client ->
        client.testConnection().getOrThrow()
        Result.success("连接成功")
    }

    // ---------- 实现细节 ----------

    /** 合并判定键：名称 + 日期。同名同日期视为同一条纪念日 */
    private fun mergeKey(event: Event): String =
        "${event.eventName.trim()}\u0000${event.eventDate.trim()}"

    /** 解析云端文件，兼容「裸数组」与「带外层包装」两种格式 */
    private fun decodeEvents(raw: String): Result<List<Event>> = runCatching {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val type = object : com.google.gson.reflect.TypeToken<List<Event>>() {}.type
            gson.fromJson<List<Event>>(trimmed, type) ?: emptyList()
        } else {
            val payload = gson.fromJson(trimmed, SyncPayload::class.java)
            payload?.events ?: emptyList()
        }
    }

    /** 覆盖前保存本地快照，只保留最近 [MAX_SNAPSHOTS] 份 */
    private fun snapshotLocal(count: Int) {
        if (count == 0) return
        try {
            val prefs = context.getSharedPreferences("MemoryDayApp", Context.MODE_PRIVATE)
            val json = prefs.getString(EventStore.KEY_EVENTS, null) ?: return
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

            val existing = prefs.getString(KEY_SNAPSHOT_INDEX, null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList() ?: mutableListOf()

            prefs.edit().putString("events_snapshot_$stamp", json).apply()
            existing.add(stamp)

            // 超出上限时删掉最旧的
            while (existing.size > MAX_SNAPSHOTS) {
                val oldest = existing.removeAt(0)
                prefs.edit().remove("events_snapshot_$oldest").apply()
            }
            prefs.edit().putString(KEY_SNAPSHOT_INDEX, existing.joinToString(",")).apply()
            Log.d(TAG, "已保存本地快照 events_snapshot_$stamp（$count 条）")
        } catch (e: Exception) {
            Log.e(TAG, "保存本地快照失败: ${e.message}")
        }
    }

    /** 统一创建/关闭客户端，保证资源释放 */
    private suspend fun withClient(
        block: suspend (WebDavClient) -> Result<String>
    ): Result<String> = withClientGeneric { client -> block(client) }

    private suspend fun <T> withClientGeneric(
        block: suspend (WebDavClient) -> Result<T>
    ): Result<T> {
        if (!credentials.isConfigured()) {
            return Result.failure(WebDavException("请先填写 WebDAV 服务器、用户名和密码"))
        }
        val client = WebDavClient(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password
        )
        return try {
            block(client)
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV 操作失败: ${e.message}")
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    private fun nowIso(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    companion object {
        private const val TAG = "WebDavSync"

        /** 云端目录与文件（单文件快照，一次 PUT 即完整写入） */
        private const val DIR = "xiaomaotai"
        private const val FILE = "$DIR/events.json"
        /** 覆盖前的云端快照，用户误操作时唯一的退路 */
        private const val SNAPSHOT_FILE = "$DIR/events.snapshot.json"

        private const val PAYLOAD_VERSION = 1
        private const val MAX_SNAPSHOTS = 3
        private const val KEY_SNAPSHOT_INDEX = "events_snapshot_index"
    }
}

/** 云端文件的外层包装：带版本与时间，便于将来演进格式 */
data class SyncPayload(
    val version: Int = 1,
    val savedAt: String = "",
    val events: List<Event> = emptyList()
)
