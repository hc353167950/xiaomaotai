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
            Version(name = "3.2.2", code = 11)
        }
    }

    const val NAME = "小茅台"

    const val SLOGAN = "把值得记住的日子，留在手心里"

    /** 关于页的「这个版本」更新说明 */
    val RELEASE_NOTES = listOf(
        "天数超过一个月时显示「约N月」，超过一年显示「约N年N月」",
        "正数模式单位改为「已经」，不再用「天前」",
        "长天数自动降字号，不再顶到卡片边缘",
        "修复旧数据卡片不显示的问题",
        "添加纪念日弹窗的取消/保存按钮固定在底部",
        "提醒时刻说明移入添加弹窗，设置页更清爽"
    )
}
