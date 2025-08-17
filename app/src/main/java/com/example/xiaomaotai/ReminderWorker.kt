package com.example.xiaomaotai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

/**
 * 提醒检查工作器
 * 使用WorkManager进行周期性检查，确保提醒不会被遗漏
 * 系统级调度，比AlarmManager更可靠，特别是在Android 15上
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "reminder_check_work"
        const val TAG = "ReminderWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "开始执行提醒检查任务")
            
            // 检查即将到期的提醒
            checkUpcomingReminders()
            
            // 检查是否需要启动前台服务
            checkAndStartForegroundService()
            
            Log.d(TAG, "提醒检查任务完成")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "提醒检查任务失败: ${e.message}")
            e.printStackTrace()
            
            // 失败时重试，最多重试3次
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * 检查即将到期的提醒
     */
    private suspend fun checkUpcomingReminders() {
        try {
            val dataManager = DataManager(applicationContext)
            val reminderManager = ReminderManager(applicationContext)
            
            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            if (allEvents.isEmpty()) {
                Log.d(TAG, "没有需要检查的事件")
                return
            }
            
            val now = Calendar.getInstance()
            var upcomingCount = 0
            
            allEvents.forEach { event ->
                try {
                    val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                    if (nextReminderDate != null) {
                        val eventCalendar = Calendar.getInstance().apply {
                            time = nextReminderDate
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        // 检查是否在未来7天内
                        val daysDiff = ((eventCalendar.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                        
                        if (daysDiff in 0..7) {
                            upcomingCount++
                            
                            // 确保AlarmManager提醒已设置（防止丢失）
                            if (!reminderManager.hasReminderSentToday(event.id, daysDiff)) {
                                reminderManager.safeScheduleReminder(event)
                                Log.d(TAG, "重新设置提醒: ${event.eventName}, 剩余${daysDiff}天")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理事件失败: ${event.eventName}, ${e.message}")
                }
            }
            
            Log.d(TAG, "检查完成，发现${upcomingCount}个即将到期的提醒")
            
        } catch (e: Exception) {
            Log.e(TAG, "检查即将到期提醒失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 检查是否需要启动前台服务
     */
    private suspend fun checkAndStartForegroundService() {
        try {
            // 只在Android 8.0+上启动前台服务
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                return
            }
            
            val dataManager = DataManager(applicationContext)
            val reminderManager = ReminderManager(applicationContext)
            
            // 获取所有事件
            val allEvents = mutableListOf<Event>()
            allEvents.addAll(dataManager.getOfflineEvents())
            dataManager.getCurrentUser()?.let {
                allEvents.addAll(dataManager.getLocalEvents())
            }
            
            val now = Calendar.getInstance()
            val twentyFourHoursLater = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 24)
            }
            
            // 检查是否有24小时内的提醒
            val hasUpcomingReminders = allEvents.any { event ->
                val nextReminderDate = reminderManager.getNextReminderDate(event.eventDate)
                if (nextReminderDate != null) {
                    val eventCalendar = Calendar.getInstance().apply {
                        time = nextReminderDate
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    eventCalendar.timeInMillis in now.timeInMillis..twentyFourHoursLater.timeInMillis
                } else {
                    false
                }
            }
            
            if (hasUpcomingReminders) {
                // 检查前台服务是否正在运行
                if (!isForegroundServiceRunning()) {
                    Log.d(TAG, "发现24小时内的提醒，启动前台服务")
                    ReminderForegroundService.startService(applicationContext)
                } else {
                    Log.d(TAG, "前台服务已在运行")
                }
            } else {
                Log.d(TAG, "没有24小时内的提醒，无需启动前台服务")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查前台服务状态失败: ${e.message}")
        }
    }
    
    /**
     * 检查前台服务是否正在运行
     */
    private fun isForegroundServiceRunning(): Boolean {
        return try {
            val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) 
                as android.app.ActivityManager
            
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            services.any { serviceInfo ->
                serviceInfo.service.className == ReminderForegroundService::class.java.name &&
                serviceInfo.foreground
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查前台服务状态异常: ${e.message}")
            false
        }
    }
}
