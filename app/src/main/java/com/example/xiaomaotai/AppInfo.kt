package com.example.xiaomaotai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 应用元信息：版本号、构建时间等只读信息。
 *
 * 从 PackageManager 读取，保证与 build.gradle.kts 的 versionName 永远一致，
 * 不再在各页面里各写一份硬编码的 "3.0.0"。
 */
object AppInfo {

    data class Version(
        val name: String,
        val code: Long
    )

    fun version(context: Context): Version {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            Version(
                name = info.versionName ?: "3.1.0",
                code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            )
        } catch (e: Exception) {
            Version(name = "3.3.0", code = 12)
        }
    }

    const val NAME = "小茅台"

    const val SLOGAN = "把值得记住的日子，留在手心里"

    /** 关于页的「这个版本」更新说明 */
    val RELEASE_NOTES = listOf(
        "首页新增「倒数 / 已过去」标签页，两种模式分开排序不再混乱",
        "修复通知「稍后提醒」「今年不再提醒」点击无反应",
        "正数模式不再发送提醒（它没有「到期」这件事）",
        "卡片高度进一步收紧，列表更紧凑",
        "天数超过一个月显示「约N月」，超过一年显示「约N年N月」"
    )
}
