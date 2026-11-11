package com.github.tvbox.osc.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                PosterCard(
                    item = item,
                    dimmed = focusedIndex >= 0 && focusedIndex != index,
                    onFocusChanged = { focused ->
                        if (focused) focusedIndex = index else if (focusedIndex == index) focusedIndex = -1
                    },
                    onClick = { onItemClick(item) },
                )
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
) {
    var focused by remember { mutableStateOf(false) }
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
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
