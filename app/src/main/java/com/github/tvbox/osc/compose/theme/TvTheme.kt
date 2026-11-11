package com.github.tvbox.osc.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** 应用级 Compose 主题：暗色配色 + 内嵌字体排版，取值全部来自 TvTokens。 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember {
        fun style(size: TextUnit, weight: FontWeight) = TextStyle(
            fontFamily = TvFonts.family(context, weight),
            fontWeight = weight,
            fontSize = size,
        )
        Typography(
            displayLarge = style(TvTokens.Type.display, FontWeight.SemiBold),
            headlineMedium = style(TvTokens.Type.headline, FontWeight.SemiBold),
            titleLarge = style(TvTokens.Type.title, FontWeight.Medium),
            titleMedium = style(TvTokens.Type.tab, FontWeight.Medium),
            bodyLarge = style(TvTokens.Type.body, FontWeight.Normal),
            bodyMedium = style(TvTokens.Type.label, FontWeight.Normal),
            labelLarge = style(TvTokens.Type.label, FontWeight.Medium),
        )
    }
    val colors = remember {
        darkColorScheme(
            primary = TvTokens.Colors.accent,
            background = TvTokens.Colors.background,
            onBackground = TvTokens.Colors.onBackground,
            surface = TvTokens.Colors.surface,
            onSurface = TvTokens.Colors.onBackground,
        )
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
