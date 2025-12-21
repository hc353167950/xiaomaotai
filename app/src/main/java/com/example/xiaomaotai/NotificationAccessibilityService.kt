package com.example.xiaomaotai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务 - 用于保活和实时更新通知栏
 *
 * 原理：
 * 1. 无障碍服务由系统 AccessibilityManagerService 管理，优先级极高
 * 2. 系统通过 bindService() 绑定，oom_adj 值很低（约 -700），几乎不会被杀
 * 3. 即使被杀，系统会在几秒内自动重新绑定启动
 * 4. 可以监听系统事件（如解锁屏幕），实现"用户看手机时立即更新"
 *
 * 本服务不直接创建通知，而是通过触发 PersistentNotificationService 来更新通知
 * 这样可以确保只有一个常驻通知，且由前台服务管理
 */
class NotificationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NotificationA11yService"
        private const val UPDATE_INTERVAL = 60 * 60 * 1000L // 1小时检查一次

        /**
         * 检查无障碍服务是否已启用
         */
        fun isServiceEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${NotificationAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(serviceName) == true
        }

        /**
         * 跳转到无障碍设置页面
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateDate: String? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            checkAndUpdateNotification()
            // 继续下一次定时检查
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        // 配置服务（最小化权限请求，只监听窗口状态变化）
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }

        // 自动开启无障碍通知设置
        val dataManager = DataManager(this)
        dataManager.setAccessibilityNotification(true)

        // 如果常驻通知已开启，立即更新一次
        if (dataManager.isPersistentNotificationEnabled()) {
            triggerNotificationUpdate()
        }

        // 启动定时检查任务
        handler.post(updateRunnable)

        Log.d(TAG, "无障碍服务初始化完成，定时更新已启动")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听窗口状态变化（包括解锁屏幕、切换应用等）
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 检查是否跨天了，如果是则更新通知
                checkAndUpdateNotification()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)

        // 标记无障碍通知已关闭
        val dataManager = DataManager(this)
        dataManager.setAccessibilityNotification(false)

        Log.d(TAG, "无障碍服务被销毁")
    }

    /**
     * 检查是否需要更新通知（日期变化时更新）
     */
    private fun checkAndUpdateNotification() {
        val today = getCurrentDateString()
        // 只有日期变化时才真正更新，避免频繁操作
        if (lastUpdateDate != today) {
            Log.d(TAG, "检测到日期变化: $lastUpdateDate -> $today，更新通知")
            triggerNotificationUpdate()
            lastUpdateDate = today
        }
    }

    /**
     * 触发通知更新（通过 PersistentNotificationService）
     * 只有在常驻通知开启时才执行更新
     */
    private fun triggerNotificationUpdate() {
        try {
            val dataManager = DataManager(this)
            // 只有常驻通知开启时才更新
            if (!dataManager.isPersistentNotificationEnabled()) {
                Log.d(TAG, "常驻通知未开启，跳过更新")
                return
            }

            // 通过 PersistentNotificationService 更新通知
            PersistentNotificationService.updateNotification(this)
            Log.d(TAG, "已触发常驻通知更新")
        } catch (e: Exception) {
            Log.e(TAG, "触发通知更新失败", e)
        }
    }

    /**
     * 获取当前日期字符串
     */
    private fun getCurrentDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH) + 1}-${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}
