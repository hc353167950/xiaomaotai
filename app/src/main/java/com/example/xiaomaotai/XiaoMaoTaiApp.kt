package com.example.xiaomaotai

import android.app.Application
import android.content.Context

/**
 * 应用入口：装配三个模块的单例并执行一次性启动引导。
 *
 * 此前 DataManager(context) 在 30 处被重复实例化，且构造函数隐藏副作用
 * （每次 new 都会注册 WorkManager、建通知渠道）。现在：
 * - 单例装配在这里，一处构造、处处复用；
 * - 启动副作用显式化为 [ReminderManager.bootstrapPeriodicWork]，只跑一次；
 * - 后台组件（广播/服务/Worker）通过 [eventStore] 等静态入口取模块，
 *   即使进程被系统冷启动（此时 Application.onCreate 已执行）也能拿到。
 */
class XiaoMaoTaiApp : Application() {

    lateinit var settings: AppSettings
        private set
    lateinit var account: Account
        private set
    lateinit var events: EventStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        account = Account(this)
        events = EventStore(this, account)

        // 一次性启动引导：通知渠道、台账清理、WorkManager 周期补漏
        ReminderManager(this).bootstrapPeriodicWork()
    }

    companion object {
        /** 后台组件入口：从任意 Context 取 EventStore 单例 */
        fun eventStore(context: Context): EventStore =
            (context.applicationContext as XiaoMaoTaiApp).events

        fun account(context: Context): Account =
            (context.applicationContext as XiaoMaoTaiApp).account

        fun settings(context: Context): AppSettings =
            (context.applicationContext as XiaoMaoTaiApp).settings
    }
}
