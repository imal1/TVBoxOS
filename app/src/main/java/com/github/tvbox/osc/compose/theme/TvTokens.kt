package com.github.tvbox.osc.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * tvOS 风格暗色设计 token 的唯一定义处。
 * 改这里的任一值即全局生效；界面代码只引用 token，不写字面量。
 */
object TvTokens {

    object Colors {
        val background = Color(0xFF0B0B0F)
        val surface = Color(0xFF1C1C22)
        val surfaceElevated = Color(0xFF2A2A32)
        val onBackground = Color(0xFFF5F5F7)
        val onBackgroundSecondary = Color(0xA6F5F5F7)
        val onBackgroundTertiary = Color(0x66F5F5F7)
        val accent = Color(0xFF0A84FF)
        val focusedTabBackground = Color(0xFFF5F5F7)
        val onFocusedTab = Color(0xFF0B0B0F)
        val shadow = Color(0xFF000000)
        val placeholder = Color(0xFF26262E)
    }

    object Radius {
        val card = 14.dp
        val tab = 22.dp
        val tabBar = 32.dp
        val panel = 28.dp
    }

    object Spacing {
        val screenHorizontal = 64.dp
        val screenTop = 28.dp
        val tabBarHeight = 64.dp
        val contentTop = 120.dp           // 内容起始位置：留出玻璃 tab bar
        val shelfGap = 36.dp              // shelf 之间
        val shelfTitleGap = 14.dp         // shelf 标题与卡片之间
        val cardGap = 22.dp               // 卡片之间
        val tabGap = 6.dp
        val tabHorizontal = 26.dp
        val tabBarPadding = 8.dp
    }

    object Type {
        val display = 44.sp
        val headline = 30.sp
        val title = 22.sp
        val body = 18.sp
        val label = 15.sp
        val tab = 20.sp
    }

    object Poster {
        val width = 172.dp
        val height = 258.dp               // 2:3 竖版海报
    }

    object Hero {
        val height = 248.dp
        val posterWidth = 124.dp
        val posterHeight = 186.dp
        val textMaxWidth = 640.dp
        const val backdropScale = 1.25f   // 背景海报放大倍率
        const val backdropAlpha = 0.55f
        val backdropBlur = 48.dp          // 背景模糊半径
    }

    object Detail {
        val posterWidth = 200.dp
        val posterHeight = 300.dp
        val textMaxWidth = 720.dp
    }

    object Movies {
        val categoryWidth = 220.dp   // 左侧分类列宽（含左边距）
    }

    object Focus {
        const val cardScale = 1.12f       // 获得焦点时的放大倍率
        const val tabScale = 1.05f
        const val dimmedAlpha = 0.55f     // 同一 shelf 中非焦点卡片的透明度
        val cardShadowElevation = 28.dp
        const val springStiffness = 380f  // 弹簧刚度：越大越快
        const val springDampingRatio = 0.62f // 阻尼比：小于 1 时有回弹
    }

    object Glass {
        const val tintAlpha = 0.10f
        const val refractionStrength = 0.8f       // 折射强度
        const val refractionHeightFraction = 0.32f
        const val specularIntensity = 0.7f        // 镜面高光
        const val ambientResponse = 0.6f
        val edgeSoftness = 14.dp
    }
}
