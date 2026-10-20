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
            Version(name = "3.2.0", code = 9)
        }
    }

    const val NAME = "小茅台"

    const val SLOGAN = "把值得记住的日子，留在手心里"

    /** 关于页的「这个版本」更新说明 */
    val RELEASE_NOTES = listOf(
        "新增备注：记下这个日子背后的故事",
        "支持正数模式：「在一起第 1825 天」",
        "提醒可按事件配置：开关、语气、提前档位",
        "通知支持「稍后提醒」「今年不再提醒」",
        "备份前校验空数据，避免误清云端备份",
        "闰月提醒加「闰」角标，一眼看出为什么出现两次"
    )
}
