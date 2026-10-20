package com.example.xiaomaotai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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

    /** 用同一套带兜底的 Gson：SyncPayload 里的 events 才能正确解析 */
    private val gson = EventJson.gson

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

        // 空备份保护：云端文件存在但解析出 0 条（损坏 / 被清空）时拒绝执行，
        // 否则「恢复」会变成「清空本地」。
        val remote = decodeEvents(raw).getOrThrow()
        if (remote.isEmpty()) {
            throw WebDavException(
                "云端备份里没有纪念日，已阻止恢复。\n" +
                    "可能是备份文件损坏或为空，请先在云盘确认文件内容。"
            )
        }

        val localCount = eventStore.persistedEvents().size
        snapshotLocal(localCount)

        // 规范化：补 id（缺失/重复都重发新 id）、按日期补 eventType。
        // 云端数据可能来自旧版本或另一台设备，直接写回会让 LazyColumn
        // 因 key 重复而崩溃，或让类型与日期不一致。
        val normalized = normalizeForRestore(remote)

        eventStore.replaceAll(normalized)
        Result.success(
            "已从云端恢复 ${normalized.size} 条纪念日（覆盖前已保存本地快照）"
        )
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

        // 已占用的 id：本地现有 + 本批次已分配。
        // 不加这层去重，云端事件会和本地事件撞 id，UI 的 key = { it.id } 直接崩。
        val usedIds = local.map { it.id }.toMutableSet()

        // 本地优先：先放本地全部，再追加云端独有的（补 id / 去重 / 补 eventType）
        val merged = mutableListOf<Event>()
        merged.addAll(local)
        val incoming = remote.filter { mergeKey(it) !in localKeys }
        merged.addAll(normalizeIncoming(incoming, usedIds))

        // sortOrder 重新编号：**按模式分组各自连续编号**。
        // 两个标签的排序互相独立，若混在一起编号，会打乱其中一组的相对顺序
        // （首页两组各自按 sortOrder 排序，跨组的序号没有可比性）。
        val renumbered = renumberPerMode(merged)

        val addedFromRemote = incoming.size
        val keptLocalOnly = local.count { mergeKey(it) !in remoteKeys }

        eventStore.replaceAll(renumbered)

        val payload = SyncPayload(
            version = PAYLOAD_VERSION,
            savedAt = nowIso(),
            events = renumbered
        )
        client.putText(FILE, gson.toJson(payload)).getOrThrow()

        Result.success(
            "合并完成：本地 ${local.size} 条 + 云端新增 $addedFromRemote 条" +
                " = ${renumbered.size} 条（本地独有 $keptLocalOnly 条已上传）"
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

    /**
     * 规范化一批来自云端的**新增**事件：
     *
     * 1. **id 缺失或与本地/批次内重复 → 重新分配**。
     *    关键：UI 的 LazyColumn 用 `key = { it.id }`，重复 id 会直接抛
     *    「Key was already used」并整页崩溃。云端数据可能来自旧版本（没有 id）
     *    或另一台设备（id 与本地不同源），因此插入前必须去重。
     * 2. **eventType 按日期自动补全**，避免旧数据里 eventType 与 eventDate 不一致。
     * 3. **保留云端事件的 mode / note / reminder 等字段**——它们已经在
     *    [EventJson] 里逐字段兜底过，这里只补 id 与类型。
     *
     * @param incoming 待插入的事件（来自云端）
     * @param usedIds  已占用的 id（本地现有 + 本批次已分配）
     */
    private fun normalizeIncoming(
        incoming: List<Event>,
        usedIds: MutableSet<String>
    ): List<Event> = incoming.map { event ->
        val safeId = event.id.trim().ifBlank { UUID.randomUUID().toString() }
        val finalId = if (safeId in usedIds) {
            // 冲突：再生成一个，直到不重复
            var candidate = UUID.randomUUID().toString()
            while (candidate in usedIds) candidate = UUID.randomUUID().toString()
            candidate
        } else {
            safeId
        }
        usedIds.add(finalId)
        event.copy(id = finalId).withAutoDetectedType()
    }

    /**
     * 整体规范化（恢复用）：保证 id 唯一且非空，并补齐 eventType。
     * 恢复是「覆盖本地」，所以这批事件自己内部去重即可。
     */
    private fun normalizeForRestore(events: List<Event>): List<Event> {
        val used = mutableSetOf<String>()
        return normalizeIncoming(events, used)
    }

    /**
     * 按模式分组重新编号 sortOrder，组内保持原有相对顺序。
     *
     * 为什么必须分组：首页两个标签各自独立排序（将至按天数升序、走过按天数降序，
     * 关闭自动排序时按 sortOrder 降序），跨组的序号没有可比性。
     * 合并时如果把两组混在一起连续编号，会让其中一组的相对顺序被打乱，
     * 表现为「合并后某个标签的卡片顺序变了」。
     *
     * 编号策略：组内从高到低递减（与首页 sortOrder 降序展示一致），
     * 两组各自使用相同的数值区间——因为展示时永远先按模式过滤，不会互相干扰。
     */
    private fun renumberPerMode(events: List<Event>): List<Event> {
        return events
            .groupBy { it.mode }
            .flatMap { (_, group) ->
                group.sortedByDescending { it.sortOrder }
                    .mapIndexed { index, e ->
                        e.copy(sortOrder = group.size - 1 - index)
                    }
            }
    }

    /** 解析云端文件，兼容「裸数组」与「带外层包装」两种格式 */
    private fun decodeEvents(raw: String): Result<List<Event>> = runCatching {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            // 裸数组：交给 EventJson 逐字段兜底
            EventJson.decodeList(trimmed)
        } else {
            // 带外层包装：取出 events 数组再走同一套兜底逻辑
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
