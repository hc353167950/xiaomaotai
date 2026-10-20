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
            Version(name = "3.4.1", code = 14)
        }
    }

    const val NAME = "小茅台"

    const val SLOGAN = "把值得记住的日子，留在手心里"

    /** 关于页的「这个版本」更新说明 */
    val RELEASE_NOTES = listOf(
        "修复杂乱首屏：标题不再被图标挤到换行，副标题改为通栏一行",
        "从排序页 / 设置页返回首页时，保留你原来所在的标签",
        "当前标签没有纪念日时，点排序直接提示，不再进入空白的排序页",
        "搜索收进顶部图标，点开即搜，首页顶部更清爽",
        "首页标签更名：倒数→将至，已过去→走过",
        "排序页只带当前标签的事件，两组排序彻底独立",
        "修复「今年不再提醒」后固定时间点仍会弹出的问题（含轮询兜底路径）",
        "「稍后提醒」只顺延那一次提醒，不影响后续提醒节点"
    )
}
