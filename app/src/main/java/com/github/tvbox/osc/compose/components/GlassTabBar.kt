package com.github.tvbox.osc.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.theme.TvTokens
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * 顶部 Liquid Glass tab bar：玻璃面板实时折射其下滚动的内容（hazeSource）。
 * 遵循 TV 习惯，tab 获得焦点即选中。
 */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    firstTabFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(TvTokens.Radius.tabBar)
    val glassStyle = remember {
        GlassStyle {
            tint(Color.White.copy(alpha = TvTokens.Glass.tintAlpha))
            optics(
                GlassOptics(
                    refractionStrength = TvTokens.Glass.refractionStrength,
                    refractionHeightFraction = TvTokens.Glass.refractionHeightFraction,
                ),
            )
            specularIntensity(TvTokens.Glass.specularIntensity)
            ambientResponse(TvTokens.Glass.ambientResponse)
            edgeSoftness(TvTokens.Glass.edgeSoftness)
            shape(shape)
        }
    }
    Row(
        modifier = modifier
            .height(TvTokens.Spacing.tabBarHeight)
            .hazeGlass(input = HazeInput.Backdrop(hazeState), style = glassStyle)
            .padding(TvTokens.Spacing.tabBarPadding)
            .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(TvTokens.Spacing.tabGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, title ->
            GlassTab(
                title = title,
                selected = index == selectedIndex,
                onFocused = { onSelect(index) },
                modifier = if (index == 0 && firstTabFocus != null) Modifier.focusRequester(firstTabFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun GlassTab(
    title: String,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val spec = spring<Float>(TvTokens.Focus.springDampingRatio, TvTokens.Focus.springStiffness)
    val scale by animateFloatAsState(if (focused) TvTokens.Focus.tabScale else 1f, spec, label = "tabScale")
    val background by animateColorAsState(
        when {
            focused -> TvTokens.Colors.focusedTabBackground
            selected -> Color.White.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        label = "tabBackground",
    )
    val textColor by animateColorAsState(
        when {
            focused -> TvTokens.Colors.onFocusedTab
            selected -> TvTokens.Colors.onBackground
            else -> TvTokens.Colors.onBackgroundSecondary
        },
        label = "tabText",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(TvTokens.Radius.tab))
            .background(background)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = TvTokens.Spacing.tabHorizontal)
            .height(TvTokens.Spacing.tabBarHeight - TvTokens.Spacing.tabBarPadding * 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}
