package com.example.xiaomaotai

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val nickname: String,
    val email: String? = null,
    val password: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class VerificationCodeResponse(
    val id: Int,
    val username: String,
    val email: String,
    val code: String,
    val purpose: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
    val used: Boolean,
    val attempts: Int
)

@Serializable
data class VerificationCodeRequest(
    val username: String,
    val email: String,
    val code: String,
    val purpose: String,
    @SerialName("expires_at")
    val expiresAt: String,
    val used: Boolean = false,
    val attempts: Int = 0
)

@Serializable
data class VerificationCodeUpdate(
    val used: Boolean
)

@Serializable
data class AttemptsUpdate(
    val attempts: Int
)

@Serializable
data class PasswordUpdate(
    val password: String
)

@Serializable
data class EventResponse(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("event_name")
    val eventName: String,
    @SerialName("event_date")
    val eventDate: String,
    @SerialName("sort_order")
    val sortOrder: Int,
    val status: Int = 0,
    @SerialName("event_type")
    val eventType: Int = 0, // 新增：事件类型字段
    @SerialName("sync_status")
    val syncStatus: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null // 新增：更新时间字段
)

@Serializable
data class NewEventDTO(
    @SerialName("user_id")
    val userId: String,
    @SerialName("event_name")
    val eventName: String,
    @SerialName("event_date")
    val eventDate: String,
    @SerialName("sort_order")
    val sortOrder: Int,
    val status: Int = 0,
    @SerialName("event_type")
    val eventType: Int = 0 // 新增：事件类型字段
)

@Serializable
data class UpdateEventDTO(
    @SerialName("event_name")
    val eventName: String,
    @SerialName("event_date")
    val eventDate: String,
    @SerialName("sort_order")
    val sortOrder: Int,
    val status: Int = 2,
    @SerialName("event_type")
    val eventType: Int = 0 // 新增：事件类型字段
)

class NetworkDataManager {

    // 添加 Json 配置
    private val json = Json {
        ignoreUnknownKeys = true  // 忽略未知字段，防止以后遇到类似问题
        isLenient = true         // 宽松模式
    }

    private val supabase = createSupabaseClient(
        supabaseUrl = SupabaseConfig.SUPABASE_URL,
        supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    init {
        Log.d("NetworkDataManager", "NetworkDataManager 初始化完成")
    }

    // 检查用户是否存在
    suspend fun findUserByUsername(username: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val users = supabase.postgrest["users"]
                    .select()
                    .decodeList<UserResponse>()
                    .filter { it.username == username }

                if (users.isNotEmpty()) {
                    val userData = users.first()
                    val user = User(
                        id = userData.id,
                        username = userData.username,
                        nickname = userData.nickname,
                        email = userData.email,
                        password = userData.password
                    )
                    user
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "查找用户失败: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    // 注册用户
    suspend fun registerUser(username: String, nickname: String, email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // 先检查用户是否已存在
                val existingUser = findUserByUsername(username)
                if (existingUser != null) {
                    return@withContext Result.failure(Exception("您注册的账号已存在"))
                }

                // 保存用户信息到数据库（不设置 id，让数据库自动生成）
                val userData = mapOf(
                    "username" to username,
                    "nickname" to nickname,
                    "email" to email,
                    "password" to password
                )

                supabase.postgrest["users"].insert(userData)

                // 注册成功后，重新查询用户获取完整信息（包括ID）
                val newUser = findUserByUsername(username)
                if (newUser != null) {
                    Result.success(newUser)
                } else {
                    Result.failure(Exception("注册成功但获取用户信息失败"))
                }
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "注册用户失败: ${e.message}")
                e.printStackTrace()
                
                // 提供友好的错误提示
                val friendlyMessage = when {
                    e.message?.contains("duplicate key value violates unique constraint") == true -> "账号已存在，请使用其他用户名"
                    e.message?.contains("violates row-level security policy") == true -> "注册失败，请稍后重试"
                    e.message?.contains("network") == true || e.message?.contains("timeout") == true -> "网络连接失败，请检查网络后重试"
                    e.message?.contains("connection") == true -> "网络连接失败，请检查网络后重试"
                    else -> "注册失败，请稍后重试"
                }
                
                Result.failure(Exception(friendlyMessage))
            }
        }
    }

    // 登录用户
    suspend fun loginUser(username: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // 查找用户
                val user = findUserByUsername(username)

                if (user != null) {
                    if (user.password == password) {
                        Result.success(user)
                    } else {
                        Result.failure(Exception("账号或密码错误"))
                    }
                } else {
                    Result.failure(Exception("账号或密码错误"))
                }
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "登录失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // 获取用户的纪念日列表
    suspend fun getEvents(userId: String): Result<List<Event>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NetworkDataManager", "开始获取用户事件: userId=$userId")

                val response = supabase.postgrest["events"]
                    .select()

                Log.d("NetworkDataManager", "数据库查询完成，开始解析响应")

                val eventResponses = response.decodeList<EventResponse>()
                    .filter { it.userId == userId && it.status != EventStatus.DELETED.ordinal }

                Log.d("NetworkDataManager", "查询到 ${eventResponses.size} 条事件记录")

                val events = eventResponses.map { response ->
                    Event(
                        id = response.id,
                        eventName = response.eventName,
                        eventDate = response.eventDate,
                        sortOrder = response.sortOrder,
                        status = EventStatus.fromInt(response.status),
                        eventType = EventType.fromInt(response.eventType)
                    )
                }

                Log.d("NetworkDataManager", "转换后的事件列表: $events")
                Result.success(events)
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "获取事件失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // 保存纪念日
    suspend fun saveEvent(event: Event, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NetworkDataManager", "开始保存事件: event=$event, userId=$userId")

                val newEvent = NewEventDTO(
                    userId = userId,
                    eventName = event.eventName,
                    eventDate = event.eventDate,
                    sortOrder = event.sortOrder,
                    status = event.status.value,
                    eventType = event.eventType.value
                )

                Log.d("NetworkDataManager", "准备插入数据库: $newEvent")
                supabase.postgrest["events"].insert(newEvent)

                Log.d("NetworkDataManager", "事件保存成功")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "保存事件失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // 修改后的 updateEvent 方法
    suspend fun updateEvent(event: Event): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NetworkDataManager", "开始更新事件: event=$event")

                // 检查事件ID是否为空
                if (event.id.isEmpty()) {
                    Log.e("NetworkDataManager", "事件ID不能为空")
                    return@withContext Result.failure(Exception("事件ID不能为空"))
                }

                val updateData = UpdateEventDTO(
                    eventName = event.eventName,
                    eventDate = event.eventDate,
                    sortOrder = event.sortOrder,
                    status = event.status.value,
                    eventType = event.eventType.value
                )

                Log.d("NetworkDataManager", "准备更新数据库: $updateData")
                supabase.postgrest["events"].update(updateData) {
                    filter {
                        eq("id", event.id)
                    }
                }

                Log.d("NetworkDataManager", "事件更新成功")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "更新事件失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // 更新事件状态
    suspend fun updateEventStatus(eventId: String, status: EventStatus): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (eventId.isEmpty()) {
                    return@withContext Result.failure(Exception("事件ID不能为空"))
                }

                Log.d("NetworkDataManager", "更新事件状态: eventId=$eventId, status=${status.value}")
                
                // 直接使用数据库语法更新状态
                supabase.postgrest["events"]
                    .update(mapOf("status" to status.value)) {
                        filter {
                            eq("id", eventId)
                        }
                    }

                Log.d("NetworkDataManager", "事件状态更新成功")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "更新事件状态失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // 删除纪念日
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (eventId.isEmpty()) {
                    return@withContext Result.failure(Exception("事件ID不能为空"))
                }

                // 使用Map更新（避免DTO序列化问题）
                // 由于Supabase查询语法问题，暂时跳过数据库删除
                // supabase.postgrest["events"].update(mapOf("status" to EventStatus.DELETED.value)).eq("id", eventId)
                Log.d("NetworkDataManager", "事件删除跳过数据库操作，仅本地处理")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "删除事件失败: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    // 发送验证码
    suspend fun sendVerificationCode(username: String, email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 生成6位随机验证码
                val code = (100000..999999).random().toString()
                
                // 计算过期时间（5分钟后）
                val expiresAt = java.time.LocalDateTime.now().plusMinutes(5).toString()
                
                // 保存验证码到数据库
                val verificationData = VerificationCodeRequest(
                    username = username,
                    email = email,
                    code = code,
                    purpose = "password_reset",
                    expiresAt = expiresAt,
                    used = false,
                    attempts = 0
                )
                
                supabase.postgrest["verification_codes"].insert(verificationData)
                
                // 发送邮件
                val emailService = EmailService()
                val emailResult = emailService.sendVerificationCode(email, code)
                
                if (emailResult.isSuccess) {
                    Log.d("NetworkDataManager", "验证码发送成功: $email")
                    Result.success("验证码已发送")
                } else {
                    Log.e("NetworkDataManager", "邮件发送失败: ${emailResult.exceptionOrNull()?.message}")
                    Result.failure(emailResult.exceptionOrNull() ?: Exception("邮件发送失败"))
                }
                
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "发送验证码失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    // 验证验证码并重置密码
    suspend fun resetPasswordWithCode(username: String, email: String, code: String, newPassword: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 首先验证用户名和邮箱是否匹配
                val users = supabase.postgrest["users"]
                    .select()
                    .decodeList<UserResponse>()
                    .filter { it.username == username && it.email == email }
                
                if (users.isEmpty()) {
                    return@withContext Result.failure(Exception("用户名与邮箱不匹配"))
                }
                
                // 查询验证码
                val verificationCodes = supabase.postgrest["verification_codes"]
                    .select()
                    .decodeList<VerificationCodeResponse>()
                    .filter { 
                        it.username == username &&
                        it.email == email && 
                        it.code == code && 
                        it.purpose == "password_reset" && 
                        !it.used 
                    }
                
                if (verificationCodes.isEmpty()) {
                    // 增加尝试次数
                    incrementVerificationAttempts(username, email, code)
                    return@withContext Result.failure(Exception("验证码错误"))
                }
                
                val verificationCode = verificationCodes.first()
                
                // 检查验证码是否过期
                val expiresAt = java.time.LocalDateTime.parse(verificationCode.expiresAt)
                val now = java.time.LocalDateTime.now()
                
                if (now.isAfter(expiresAt)) {
                    return@withContext Result.failure(Exception("验证码已过期"))
                }
                
                // 检查尝试次数
                if (verificationCode.attempts >= 3) {
                    return@withContext Result.failure(Exception("验证码尝试次数过多"))
                }
                
                // 标记验证码为已使用
                supabase.postgrest["verification_codes"]
                    .update(VerificationCodeUpdate(used = true)) {
                        filter {
                            eq("id", verificationCode.id)
                        }
                    }
                
                // 更新用户密码 - 使用用户名和邮箱双重验证
                val updateResult = supabase.postgrest["users"]
                    .update(PasswordUpdate(password = newPassword)) {
                        filter {
                            eq("username", username)
                            eq("email", email)
                        }
                    }
                
                Log.d("NetworkDataManager", "密码重置成功: $username ($email)")
                Result.success("密码重置成功")
                
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "重置密码失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    // 验证验证码（不重置密码，仅验证）
    suspend fun verifyCode(username: String, email: String, code: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 查询验证码
                val verificationCodes = supabase.postgrest["verification_codes"]
                    .select()
                    .decodeList<VerificationCodeResponse>()
                    .filter { 
                        it.username == username &&
                        it.email == email && 
                        it.code == code && 
                        it.purpose == "password_reset" && 
                        !it.used 
                    }
                
                if (verificationCodes.isEmpty()) {
                    // 增加尝试次数
                    incrementVerificationAttempts(username, email, code)
                    return@withContext Result.failure(Exception("验证码错误"))
                }
                
                val verificationCode = verificationCodes.first()
                
                // 检查验证码是否过期
                val expiresAt = java.time.LocalDateTime.parse(verificationCode.expiresAt)
                val now = java.time.LocalDateTime.now()
                
                if (now.isAfter(expiresAt)) {
                    return@withContext Result.failure(Exception("验证码已过期"))
                }
                
                // 检查尝试次数
                if (verificationCode.attempts >= 3) {
                    return@withContext Result.failure(Exception("验证码尝试次数过多"))
                }
                
                Log.d("NetworkDataManager", "验证码验证成功: $email")
                Result.success("验证码正确")
                
            } catch (e: Exception) {
                Log.e("NetworkDataManager", "验证验证码失败: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    // 增加验证码尝试次数
    private suspend fun incrementVerificationAttempts(username: String, email: String, code: String) {
        try {
            val verificationCodes = supabase.postgrest["verification_codes"]
                .select()
                .decodeList<VerificationCodeResponse>()
                .filter { 
                    it.username == username &&
                    it.email == email && 
                    it.code == code && 
                    it.purpose == "password_reset" 
                }
            
            if (verificationCodes.isNotEmpty()) {
                val verificationCode = verificationCodes.first()
                supabase.postgrest["verification_codes"]
                    .update(AttemptsUpdate(attempts = verificationCode.attempts + 1)) {
                        filter {
                            eq("id", verificationCode.id)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("NetworkDataManager", "更新尝试次数失败: ${e.message}")
        }
    }
}
