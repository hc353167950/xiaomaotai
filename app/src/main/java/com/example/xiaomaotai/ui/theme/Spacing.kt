package com.example.xiaomaotai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 间距与尺寸的设计令牌。
 *
 * 此前 padding/height/radius 的数值散落在各文件里（2/4/6/7/8/10/12/14/16/18/20/22dp
 * 混用），导致同一屏内的呼吸感不一致。这里统一到 4dp 基准的阶梯：
 * 4 / 8 / 12 / 16 / 20 / 24 / 32，所有布局只从这里取值。
 *
 * 触控目标统一遵守 [MinTouchTarget] = 48dp（Material / WCAG 建议值），
 * 图标可以画得小，但可点区域必须够大。
 */
object Space {
    /** 2dp：仅用于文字与紧邻元素的微调（如数字与其单位） */
    val xxs = 2.dp

    /** 4dp：图标与文字之间 */
    val xs = 4.dp

    /** 8dp：同组元素之间 */
    val sm = 8.dp

    /** 12dp：卡片内部次要分组、列表项之间 */
    val md = 12.dp

    /** 16dp：页面左右边距、卡片内主内边距 */
    val lg = 16.dp

    /** 20dp：卡片内宽松内边距 */
    val xl = 20.dp

    /** 24dp：区块之间 */
    val xxl = 24.dp

    /** 32dp：大区块、空状态上下留白 */
    val huge = 32.dp

    /** 页面水平安全边距（全应用统一） */
    val screenH = 16.dp

    /** 最小可点击尺寸 */
    val MinTouchTarget = 48.dp

    /** 列表项间距 */
    val listGap = 12.dp

    /** 图标尺寸阶梯 */
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
}

/**
 * 圆角阶梯。此前 10/12/14/16/18/20/22dp 混用，
 * 现在统一为三档：小（按钮/输入）、中（卡片）、大（弹窗/容器）。
 */
object Shape {
    /** 12dp：按钮、输入框、小徽标 */
    val small = RoundedCornerShape(12.dp)

    /** 16dp：卡片 */
    val medium = RoundedCornerShape(16.dp)

    /** 20dp：大卡片、底部弹窗 */
    val large = RoundedCornerShape(20.dp)

    /** 10dp：紧凑图标按钮 */
    val tiny = RoundedCornerShape(10.dp)
}

/**
 * 常用尺寸。
 */
object Sizes {
    /** 首页卡片最小高度（内容多时可自然撑高） */
    val cardMinHeight = 112.dp

    /** 顶部搜索框高度 */
    val searchHeight = 44.dp

    /** 顶栏图标按钮的视觉尺寸 */
    val headerButton = 38.dp

    /** 底部导航栏高度 */
    val bottomBar = 60.dp

    /** 事件卡片右侧倒计时数字的字号 */
    val daysNumber = 30.dp
}
