package com.example.xiaomaotai.ui.components

/**
 * 设置二级页路由。
 *
 * 用一个枚举而不是字符串常量，避免手写 "settings_webdav" 之类拼错。
 * 顺序即设置根页的显示顺序（界面风格 → 通知与权限 → 后台保活 → 备份与同步 → 关于）。
 */
enum class SettingsRoute(val title: String) {
    UiStyle("界面风格"),
    Notification("通知与权限"),
    KeepAlive("后台保活"),
    WebDav("备份与同步"),
    Sorting("排序与列表"),
    About("关于")
}
