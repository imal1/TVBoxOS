package com.github.tvbox.osc.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.tvbox.osc.compose.theme.TvTokens

/** shelf 中一张卡片的展示数据。 */
data class PosterItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    /** hero 区展示的元信息，如「2024 · 大陆 · 剧情」。 */
    val meta: String? = null,
    val description: String? = null,
    /** 打开详情所需：所属数据源与影片 id。 */
    val sourceKey: String? = null,
    val vodId: String? = null,
)

/**
 * 横向 shelf：标题 + 一行海报卡片。
 * 卡片获得焦点时放大、带弹簧回弹与投影，同一 shelf 中的其余卡片变暗。
 */
@Composable
fun Shelf(
    title: String,
    items: List<PosterItem>,
    onItemClick: (PosterItem) -> Unit,
    modifier: Modifier = Modifier,
    onItemFocused: (PosterItem) -> Unit = {},
    loading: Boolean = false,
    /** 需要恢复焦点的卡片 id（例如从详情页返回时回到原卡片）。 */
    restoreFocusKey: String? = null,
    onFocusRestored: () -> Unit = {},
) {
    var focusedIndex by remember { mutableStateOf(-1) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvTokens.Colors.onBackground,
            modifier = Modifier.padding(start = TvTokens.Spacing.screenHorizontal),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            // 上下留白给放大后的卡片与投影，避免被裁切
            contentPadding = PaddingValues(
                horizontal = TvTokens.Spacing.screenHorizontal,
                vertical = TvTokens.Spacing.shelfTitleGap + 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap),
        ) {
            if (loading) {
                items(SKELETON_COUNT) { SkeletonCard() }
            } else {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    PosterCard(
                        item = item,
                        dimmed = focusedIndex >= 0 && focusedIndex != index,
                        onFocusChanged = { focused ->
                            if (focused) {
                                focusedIndex = index
                                onItemFocused(item)
                            } else if (focusedIndex == index) {
                                focusedIndex = -1
                            }
                        },
                        onClick = { onItemClick(item) },
                        requestFocus = item.id == restoreFocusKey,
                        onFocusRequested = onFocusRestored,
                    )
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    item: PosterItem,
    dimmed: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusRequested: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusRequested()
        }
    }
    val floatSpring = spring<Float>(TvTokens.Focus.springDampingRatio, TvTokens.Focus.springStiffness)
    val scale by animateFloatAsState(if (focused) TvTokens.Focus.cardScale else 1f, floatSpring, label = "cardScale")
    val alpha by animateFloatAsState(if (dimmed) TvTokens.Focus.dimmedAlpha else 1f, label = "cardAlpha")
    val elevation by animateDpAsState(if (focused) TvTokens.Focus.cardShadowElevation else 0.dp, label = "cardShadow")
    val shape = RoundedCornerShape(TvTokens.Radius.card)

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f) // 放大后盖住相邻卡片
            .size(TvTokens.Poster.width, TvTokens.Poster.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = true
                ambientShadowColor = TvTokens.Colors.shadow
                spotShadowColor = TvTokens.Colors.shadow
            }
            .background(TvTokens.Colors.placeholder)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        PosterImage(url = item.imageUrl, title = item.title, modifier = Modifier.fillMaxSize())
        // 底部渐变 + 标题，保证任意海报上标题都可读
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TvTokens.Colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvTokens.Colors.onBackgroundSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val SKELETON_COUNT = 8

/** 加载中的占位卡片。 */
@Composable
private fun SkeletonCard() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier = Modifier
            .size(TvTokens.Poster.width, TvTokens.Poster.height)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(TvTokens.Radius.card))
            .background(TvTokens.Colors.placeholder),
    )
}

/**
 * 海报图：加载中显示占位底色，无图或加载失败时显示片名首字，保证不出现空白。
 */
@Composable
fun PosterImage(url: String?, title: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    Box(modifier = modifier.background(TvTokens.Colors.placeholder), contentAlignment = Alignment.Center) {
        if (failed) {
            Text(
                text = title.trim().take(1).ifEmpty { "?" },
                style = MaterialTheme.typography.displayLarge,
                color = TvTokens.Colors.onBackgroundTertiary,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
