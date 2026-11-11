package com.github.tvbox.osc.compose.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.tvbox.osc.compose.components.PosterImage
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.theme.TvTokens

private const val HERO_FADE_MS = 450

/**
 * 全屏背景：当前聚焦影片的海报模糊并放大铺满。
 * 影视数据源通常只有竖版海报、没有横版剧照，这是缺素材时的标准处理。
 */
@Composable
fun HeroBackdrop(item: PosterItem?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(TvTokens.Colors.background)) {
        Crossfade(
            targetState = item?.imageUrl,
            animationSpec = tween(HERO_FADE_MS),
            label = "heroBackdrop",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = TvTokens.Hero.backdropScale
                            scaleY = TvTokens.Hero.backdropScale
                            alpha = TvTokens.Hero.backdropAlpha
                        }
                        .blur(TvTokens.Hero.backdropBlur),
                )
            }
        }
        // 压暗：左侧与底部渐变，保证文字与 shelf 可读
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(TvTokens.Colors.background.copy(alpha = 0.85f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to TvTokens.Colors.background,
                    ),
                ),
        )
    }
}

/** hero 信息区：清晰海报 + 标题 + 元信息 + 简介，随焦点切换淡入淡出。 */
@Composable
fun HeroInfo(item: PosterItem?, sourceName: String?, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = item,
        animationSpec = tween(HERO_FADE_MS),
        label = "heroInfo",
        modifier = modifier
            .fillMaxWidth()
            .height(TvTokens.Hero.height)
            .padding(horizontal = TvTokens.Spacing.screenHorizontal),
    ) { current ->
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                Text(
                    text = sourceName ?: "TVBox",
                    style = MaterialTheme.typography.displayLarge,
                    color = TvTokens.Colors.onBackground,
                )
            }
            return@Crossfade
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PosterImage(
                url = current.imageUrl,
                title = current.title,
                modifier = Modifier
                    .size(TvTokens.Hero.posterWidth, TvTokens.Hero.posterHeight)
                    .clip(RoundedCornerShape(TvTokens.Radius.card)),
            )
            Column(
                modifier = Modifier.widthIn(max = TvTokens.Hero.textMaxWidth),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = TvTokens.Colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(current.meta, current.subtitle).joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTokens.Colors.onBackgroundSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (current.description != null) {
                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTokens.Colors.onBackgroundSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
