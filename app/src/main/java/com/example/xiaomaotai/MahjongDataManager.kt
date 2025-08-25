package com.example.xiaomaotai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * 麻将计分数据管理器
 */
class MahjongDataManager(private val context: Context) {
    
    private val dbHelper = MahjongDatabaseHelper(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val networkDataManager = NetworkDataManager()
    
    /**
     * 保存麻将计分记录（支持云端同步）
     */
    suspend fun saveMahjongScore(
        winnerPosition: String,
        winnerFan: Double,
        positionData: Map<String, PositionData>,
        calculationDetail: String,
        finalAmounts: FinalAmounts,
        userId: String?
    ): Long {
        val db = dbHelper.writableDatabase
        val currentTime = System.currentTimeMillis()
        
        val values = ContentValues().apply {
            put("winner_position", winnerPosition)
            put("winner_fan", winnerFan)
            put("position_data", Json.encodeToString(positionData))
            put("calculation_detail", calculationDetail)
            put("final_amounts", Json.encodeToString(finalAmounts))
            put("user_id", userId)
            put("record_time", currentTime)
            put("sync_status", 0) // 0=未同步，1=已同步，2=同步失败
        }
        
        val localId = db.insert("mahjong_scores", null, values)
        Log.d("MahjongDataManager", "Saved mahjong score locally with ID: $localId")
        
        // 后台无感知云端同步
        if (userId != null && localId > 0) {
            Log.d("MahjongDataManager", "开始云端同步，用户ID: $userId, 本地记录ID: $localId")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // 先测试云端连接
                    Log.d("MahjongDataManager", "测试云端连接...")
                    val connectionTest = networkDataManager.testConnection()
                    if (connectionTest.isFailure) {
                        Log.e("MahjongDataManager", "云端连接测试失败: ${connectionTest.exceptionOrNull()?.message}")
                        // 更新同步状态为失败
                        val updateValues = ContentValues().apply {
                            put("sync_status", 2)
                        }
                        db.update("mahjong_scores", updateValues, "id = ?", arrayOf(localId.toString()))
                        return@launch
                    }
                    Log.d("MahjongDataManager", "云端连接测试成功")
                    
                    Log.d("MahjongDataManager", "准备调用NetworkDataManager.saveMahjongScore")
                    val result = networkDataManager.saveMahjongScore(
                        userId = userId,
                        recordTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(currentTime)),
                        winnerPosition = winnerPosition,
                        winnerFan = winnerFan,
                        positionData = Json.encodeToString(positionData),
                        calculationDetail = calculationDetail,
                        finalAmounts = Json.encodeToString(finalAmounts)
                    )
                    
                    Log.d("MahjongDataManager", "NetworkDataManager.saveMahjongScore调用完成，结果: ${result.isSuccess}")
                    
                    // 更新本地同步状态
                    val updateValues = ContentValues().apply {
                        put("sync_status", if (result.isSuccess) 1 else 2)
                    }
                    db.update("mahjong_scores", updateValues, "id = ?", arrayOf(localId.toString()))
                    
                    if (result.isSuccess) {
                        Log.d("MahjongDataManager", "Background sync to cloud successful for record $localId")
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                        Log.e("MahjongDataManager", "Background sync to cloud failed for record $localId: $errorMsg")
                        Log.e("MahjongDataManager", "完整错误信息: ${result.exceptionOrNull()}")
                    }
                } catch (e: Exception) {
                    // 更新同步状态为失败
                    val updateValues = ContentValues().apply {
                        put("sync_status", 2)
                    }
                    db.update("mahjong_scores", updateValues, "id = ?", arrayOf(localId.toString()))
                    Log.e("MahjongDataManager", "Background sync error for record $localId: ${e.message}")
                    Log.e("MahjongDataManager", "完整异常堆栈: ", e)
                }
            }
        } else {
            Log.w("MahjongDataManager", "跳过云端同步 - 用户ID: $userId, 本地记录ID: $localId")
        }
        
        return localId
    }
    
    /**
     * 获取麻将计分记录列表
     */
    fun getMahjongScores(userId: String? = null): List<MahjongScoreRecord> {
        val db = dbHelper.readableDatabase
        val scores = mutableListOf<MahjongScoreRecord>()
        
        val query = if (userId != null) {
            "SELECT * FROM mahjong_scores WHERE user_id = ? ORDER BY record_time DESC LIMIT 20"
        } else {
            "SELECT * FROM mahjong_scores ORDER BY record_time DESC LIMIT 20"
        }
        
        val cursor = if (userId != null) {
            db.rawQuery(query, arrayOf(userId))
        } else {
            db.rawQuery(query, null)
        }
        
        cursor.use {
            while (it.moveToNext()) {
                val syncStatus: Int = try {
                    it.getInt(it.getColumnIndexOrThrow("sync_status"))
                } catch (e: Exception) {
                    0 // 默认未同步状态，兼容旧数据
                }
                
                val record = MahjongScoreRecord(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    winnerPosition = it.getString(it.getColumnIndexOrThrow("winner_position")),
                    winnerFan = it.getDouble(it.getColumnIndexOrThrow("winner_fan")),
                    positionData = Json.decodeFromString(it.getString(it.getColumnIndexOrThrow("position_data"))),
                    calculationDetail = it.getString(it.getColumnIndexOrThrow("calculation_detail")),
                    finalAmounts = Json.decodeFromString(it.getString(it.getColumnIndexOrThrow("final_amounts"))),
                    userId = it.getString(it.getColumnIndexOrThrow("user_id")),
                    recordTime = it.getLong(it.getColumnIndexOrThrow("record_time")),
                    syncStatus = syncStatus
                )
                scores.add(record)
            }
        }
        
        // 后台尝试同步未同步的记录
        if (userId != null) {
            retryFailedSyncs(userId)
        }
        
        return scores
    }
    
    /**
     * 删除麻将计分记录
     */
    suspend fun deleteMahjongScore(id: Int): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val deletedRows = db.delete("mahjong_scores", "id = ?", arrayOf(id.toString()))
            Log.d("MahjongDataManager", "Deleted mahjong score with ID: $id")
            deletedRows > 0
        } catch (e: Exception) {
            Log.e("MahjongDataManager", "Error deleting mahjong score", e)
            false
        }
    }
    
    /**
     * 清空所有麻将计分记录
     */
    suspend fun clearAllMahjongScores(userId: String? = null): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val selection = if (userId != null) "user_id = ? OR user_id IS NULL" else "user_id IS NULL"
            val selectionArgs = if (userId != null) arrayOf(userId) else null
            
            val deletedRows = db.delete("mahjong_scores", selection, selectionArgs)
            Log.d("MahjongDataManager", "Cleared $deletedRows mahjong scores")
            true
        } catch (e: Exception) {
            Log.e("MahjongDataManager", "Error clearing mahjong scores", e)
            false
        }
    }
    
    /**
     * 获取统计信息
     */
    suspend fun getMahjongStatistics(userId: String? = null): MahjongStatistics {
        return try {
            val scores = getMahjongScores(userId)
            val totalGames = scores.size
            
            if (totalGames == 0) {
                return MahjongStatistics()
            }
            
            var totalWinAmount = 0.0
            var totalLoseAmount = 0.0
            var winCount = 0
            var loseCount = 0
            
            scores.forEach { score: MahjongScoreRecord ->
                val finalAmounts = score.finalAmounts
                val downAmount = finalAmounts.down // 雀神（自己）的金额
                
                if (downAmount > 0) {
                    totalWinAmount += downAmount
                    winCount++
                } else if (downAmount < 0) {
                    totalLoseAmount += Math.abs(downAmount)
                    loseCount++
                }
            }
            
            MahjongStatistics(
                totalGames = totalGames,
                winCount = winCount,
                loseCount = loseCount,
                totalWinAmount = totalWinAmount,
                totalLoseAmount = totalLoseAmount,
                netAmount = totalWinAmount - totalLoseAmount
            )
        } catch (e: Exception) {
            Log.e("MahjongDataManager", "Error getting statistics", e)
            MahjongStatistics()
        }
    }
    
    private fun retryFailedSyncs(userId: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT * FROM mahjong_scores WHERE user_id = ? AND sync_status = 2 LIMIT 5", arrayOf(userId))
                
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(it.getColumnIndexOrThrow("id"))
                        val recordTime = it.getLong(it.getColumnIndexOrThrow("record_time"))
                        val winnerPosition = it.getString(it.getColumnIndexOrThrow("winner_position"))
                        val winnerFan = it.getDouble(it.getColumnIndexOrThrow("winner_fan"))
                        val positionDataStr = it.getString(it.getColumnIndexOrThrow("position_data"))
                        val calculationDetail = it.getString(it.getColumnIndexOrThrow("calculation_detail"))
                        val finalAmountsStr = it.getString(it.getColumnIndexOrThrow("final_amounts"))
                        
                        try {
                            val result = networkDataManager.saveMahjongScore(
                                userId = userId,
                                recordTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(recordTime)),
                                winnerPosition = winnerPosition,
                                winnerFan = winnerFan,
                                positionData = positionDataStr,
                                calculationDetail = calculationDetail,
                                finalAmounts = finalAmountsStr
                            )
                            
                            // 更新本地同步状态
                            val updateValues = ContentValues().apply {
                                put("sync_status", if (result.isSuccess) 1 else 2)
                            }
                            val writeDb = dbHelper.writableDatabase
                            writeDb.update("mahjong_scores", updateValues, "id = ?", arrayOf(id.toString()))
                            
                            if (result.isSuccess) {
                                Log.d("MahjongDataManager", "Background retry sync to cloud successful for record $id")
                            } else {
                                Log.w("MahjongDataManager", "Background retry sync to cloud failed for record $id: ${result.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            Log.w("MahjongDataManager", "Background retry sync error for record $id: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MahjongDataManager", "Error in retryFailedSyncs: ${e.message}")
            }
        }
    }
    
    /**
     * 自动清理本地数据
     * 规则：超过一个月且数据大于20条时清理，少于20条不清理
     */
    private suspend fun autoCleanupLocalData(userId: String?) {
        try {
            val db = dbHelper.readableDatabase
            val selection = if (userId != null) "user_id = ? OR user_id IS NULL" else "user_id IS NULL"
            val selectionArgs = if (userId != null) arrayOf(userId) else null
            
            // 获取所有记录数量
            val countCursor = db.query(
                "mahjong_scores",
                arrayOf("COUNT(*) as count"),
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            
            val totalCount = if (countCursor.moveToFirst()) {
                countCursor.getInt(0)
            } else {
                0
            }
            countCursor.close()
            
            Log.d("MahjongDataManager", "Total mahjong scores: $totalCount")
            
            // 如果记录少于等于20条，不进行清理
            if (totalCount <= 20) {
                Log.d("MahjongDataManager", "Records <= 20, skipping cleanup")
                return
            }
            
            // 计算一个月前的时间
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)
            val oneMonthAgo = calendar.time
            val oneMonthAgoString = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(oneMonthAgo)
            
            // 查询超过一个月的记录
            val cursor = db.query(
                "mahjong_scores",
                arrayOf("id", "record_time"),
                selection,
                selectionArgs,
                null,
                null,
                "id ASC" // 按ID升序，删除最老的记录
            )
            
            val recordsToDelete = mutableListOf<Int>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getInt(it.getColumnIndexOrThrow("id"))
                    val recordTime = it.getString(it.getColumnIndexOrThrow("record_time"))
                    
                    // 解析记录时间
                    try {
                        val recordDate = SimpleDateFormat("yyyy年MM月dd日HH时mm分", Locale.getDefault()).parse(recordTime)
                        if (recordDate != null && recordDate.before(oneMonthAgo)) {
                            recordsToDelete.add(id)
                        }
                    } catch (e: Exception) {
                        Log.w("MahjongDataManager", "Failed to parse date: $recordTime")
                    }
                }
            }
            
            // 只有在总记录数超过20条时才删除超过一个月的记录
            if (totalCount > 20 && recordsToDelete.isNotEmpty()) {
                val writeDb = dbHelper.writableDatabase
                
                // 获取最新的20条记录ID，这些记录不能被删除
                val keepCursor = db.query(
                    "mahjong_scores",
                    arrayOf("id"),
                    selection,
                    selectionArgs,
                    null,
                    null,
                    "id DESC", // 按ID降序，获取最新的记录
                    "20"
                )
                
                val idsToKeep = mutableSetOf<Int>()
                keepCursor.use {
                    while (it.moveToNext()) {
                        idsToKeep.add(it.getInt(it.getColumnIndexOrThrow("id")))
                    }
                }
                
                // 只删除超过一个月且不在前20条中的记录
                val actualIdsToDelete = recordsToDelete.filter { id -> !idsToKeep.contains(id) }
                
                for (id in actualIdsToDelete) {
                    writeDb.delete("mahjong_scores", "id = ?", arrayOf(id.toString()))
                }
                
                Log.d("MahjongDataManager", "Auto cleanup: deleted ${actualIdsToDelete.size} old records, kept ${idsToKeep.size} latest records")
            } else {
                Log.d("MahjongDataManager", "No cleanup needed: total=$totalCount, oldRecords=${recordsToDelete.size}")
            }
            
        } catch (e: Exception) {
            Log.e("MahjongDataManager", "Error during auto cleanup", e)
        }
    }
    
    /**
     * 获取云端数据并合并到本地（仅登录用户）
     * 暂时禁用，等待云端接口完善
     */
    suspend fun syncFromCloud(userId: String): Boolean {
        return try {
            // TODO: 等待NetworkDataManager添加getMahjongScores方法后再启用云端同步
            Log.d("MahjongDataManager", "Cloud sync temporarily disabled - using local data only")
            true
        } catch (e: Exception) {
            Log.e("MahjongDataManager", "Error syncing from cloud", e)
            false
        }
    }
    
    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日HH时mm分", Locale.getDefault())
        return sdf.format(Date())
    }
}

/**
 * 麻将计分数据库助手
 */
class MahjongDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val DATABASE_NAME = "mahjong_scores.db"
        private const val DATABASE_VERSION = 2
        
        private const val CREATE_MAHJONG_SCORES_TABLE = """
            CREATE TABLE mahjong_scores (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                winner_position TEXT NOT NULL,
                winner_fan REAL NOT NULL,
                position_data TEXT NOT NULL,
                calculation_detail TEXT NOT NULL,
                final_amounts TEXT NOT NULL,
                user_id TEXT,
                record_time INTEGER NOT NULL,
                sync_status INTEGER DEFAULT 0
            )
        """
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_MAHJONG_SCORES_TABLE)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                // 从版本1升级到版本2：添加sync_status字段
                try {
                    db.execSQL("ALTER TABLE mahjong_scores ADD COLUMN sync_status INTEGER DEFAULT 0")
                    Log.d("MahjongDatabaseHelper", "Successfully upgraded database from version 1 to 2")
                } catch (e: Exception) {
                    Log.e("MahjongDatabaseHelper", "Error upgrading database: ${e.message}")
                    // 如果升级失败，重建表
                    db.execSQL("DROP TABLE IF EXISTS mahjong_scores")
                    onCreate(db)
                }
            }
            else -> {
                // 其他版本升级：重建表
                db.execSQL("DROP TABLE IF EXISTS mahjong_scores")
                onCreate(db)
            }
        }
    }
}

/**
 * 麻将计分记录数据类
 */
data class MahjongScoreRecord(
    val id: Long,
    val winnerPosition: String,
    val winnerFan: Double,
    val positionData: Map<String, PositionData>,
    val calculationDetail: String,
    val finalAmounts: FinalAmounts,
    val userId: String?,
    val recordTime: Long,
    val syncStatus: Int = 0 // 0=未同步，1=已同步，2=同步失败
)

/**
 * 麻将统计信息
 */
data class MahjongStatistics(
    val totalGames: Int = 0, // 总局数
    val winCount: Int = 0, // 胜局数
    val loseCount: Int = 0, // 败局数
    val totalWinAmount: Double = 0.0, // 总赢金额
    val totalLoseAmount: Double = 0.0, // 总输金额
    val netAmount: Double = 0.0 // 净金额
)
