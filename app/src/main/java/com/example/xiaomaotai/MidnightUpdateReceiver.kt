package com.example.xiaomaotai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 零点更新广播接收器
 * 专门用于在零点时刻更新常驻通知的天数显示
 *
 * 使用 BroadcastReceiver 而不是直接启动 Service，
 * 因为 AlarmManager 在设备休眠时触发 BroadcastReceiver 更可靠
 */
class MidnightUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightUpdateReceiver"
        const val ACTION_MIDNIGHT_UPDATE = "com.example.xiaomaotai.ACTION_MIDNIGHT_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "零点更新广播接收到，action: ${intent.action}")

        when (intent.action) {
            ACTION_MIDNIGHT_UPDATE -> {
                handleMidnightUpdate(context)
            }
        }
    }

    private fun handleMidnightUpdate(context: Context) {
        try {
            Log.d(TAG, "处理零点更新")

            val dataManager = DataManager(context)

            // 检查常驻通知是否开启
            if (dataManager.isPersistentNotificationEnabled()) {
                Log.d(TAG, "常驻通知已开启，触发更新")

                // 通过启动服务来更新通知
                // 使用 ACTION_UPDATE_NOTIFICATION 而不是 ACTION_MIDNIGHT_UPDATE
                // 这样服务内部会处理更新逻辑
                val serviceIntent = Intent(context, PersistentNotificationService::class.java).apply {
                    action = PersistentNotificationService.ACTION_MIDNIGHT_UPDATE
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "已发送服务更新请求")
                } catch (e: Exception) {
                    Log.e(TAG, "启动服务失败，尝试直接更新通知: ${e.message}")
                    // 如果启动服务失败，尝试直接更新通知
                    PersistentNotificationService.updateNotification(context)
                }
            } else {
                Log.d(TAG, "常驻通知未开启，跳过更新")
            }

            // 重新设置明天零点的闹钟
            MidnightAlarmHelper.setupMidnightAlarm(context)

        } catch (e: Exception) {
            Log.e(TAG, "处理零点更新失败: ${e.message}", e)
        }
    }
}
