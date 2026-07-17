package com.example.xiaomaotai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

/**
 * WorkManager 周期补漏适配器：每30分钟醒来一次，
 * 对7天内到期的事件重设 AlarmManager 提醒，防止闹钟被系统清掉。
 *
 * 只做"发现丢失→重设"这一件事；何时提醒、如何去重由 ReminderManager 决定。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "reminder_periodic_check"
        private const val TAG = "ReminderWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "开始执行提醒补漏检查")
            checkUpcomingReminders()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "提醒补漏检查失败: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /** 对7天内到期的事件重新设置提醒（safeScheduleReminder 先取消再设置，天然幂等） */
    private suspend fun checkUpcomingReminders() {
        val eventStore = XiaoMaoTaiApp.eventStore(applicationContext)
        val reminderManager = ReminderManager(applicationContext)

        val allEvents = eventStore.currentEvents()
        if (allEvents.isEmpty()) {
            Log.d(TAG, "没有需要检查的事件")
            return
        }

        var upcomingCount = 0
        allEvents.forEach { event ->
            try {
                val days = Countdown.of(event.eventDate)?.days ?: return@forEach
                if (days in 0..7) {
                    upcomingCount++
                    reminderManager.safeScheduleReminder(event)
                    Log.d(TAG, "重新设置提醒: ${event.eventName}, 剩余${days}天")
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理事件失败: ${event.eventName}, ${e.message}")
            }
        }
        Log.d(TAG, "补漏完成，重设了${upcomingCount}个即将到期的提醒")
    }
}
