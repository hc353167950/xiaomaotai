package com.example.xiaomaotai

import android.app.Application
import android.content.Context

/**
 * 应用入口：装配各模块的单例并执行一次性启动引导。
 *
 * 纯本地存储版本：不再有 Account / NetworkDataManager。云端能力由
 * [WebDavSync] 提供，且只在用户主动点击时才工作（手动同步）。
 */
class XiaoMaoTaiApp : Application() {

    lateinit var settings: AppSettings
        private set
    lateinit var events: EventStore
        private set
    lateinit var webDavCredentials: WebDavCredentials
        private set
    lateinit var webDavSync: WebDavSync
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        events = EventStore(this)
        webDavCredentials = WebDavCredentials(this)
        webDavSync = WebDavSync(this, events, webDavCredentials)

        // 一次性启动引导：通知渠道、台账清理、WorkManager 周期补漏
        ReminderManager(this).bootstrapPeriodicWork()
    }

    companion object {
        /** 后台组件入口：从任意 Context 取 EventStore 单例 */
        fun eventStore(context: Context): EventStore =
            (context.applicationContext as XiaoMaoTaiApp).events

        fun settings(context: Context): AppSettings =
            (context.applicationContext as XiaoMaoTaiApp).settings

        fun webDavSync(context: Context): WebDavSync =
            (context.applicationContext as XiaoMaoTaiApp).webDavSync

        fun webDavCredentials(context: Context): WebDavCredentials =
            (context.applicationContext as XiaoMaoTaiApp).webDavCredentials
    }
}
