@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.github.tvbox.osc.compose.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.components.PosterImage
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.home.HeroBackdrop
import com.github.tvbox.osc.compose.theme.TvTokens

/** 一段选集范围最多的集数，超过时按范围分组。 */
private const val EPISODE_PAGE = 50

/**
 * 详情页：海报、标题、元信息、简介、演职员、播放源与剧集、收藏。
 * 选集后交给播放页起播（13 号票重写播放器前仍为旧页面）。
 */
@Composable
fun DetailScreen(
    onPlay: (sourceKey: String, vodId: String, title: String, poster: String?, flag: String, index: Int) -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshHistory()
        onPauseOrDispose { }
    }
    when (val current = state) {
        DetailUiState.Loading -> DetailMessage("正在加载详情…", null, null)
        is DetailUiState.Failed -> DetailMessage(current.message, "重试") { viewModel.load() }
        is DetailUiState.Content -> DetailContentView(
            data = current.data,
            onSelectFlag = viewModel::selectFlag,
            onToggleFavorite = viewModel::toggleFavorite,
            onPlay = { flag, index ->
                onPlay(viewModel.sourceKey, viewModel.vodId, current.data.title, current.data.poster, flag, index)
            },
        )
    }
}

@Composable
private fun DetailContentView(
    data: DetailContent,
    onSelectFlag: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (flag: String, index: Int) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(item = PosterItem(id = "detail", title = data.title, imageUrl = data.poster))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                PosterImage(
                    url = data.poster,
                    title = data.title,
                    modifier = Modifier
                        .size(TvTokens.Detail.posterWidth, TvTokens.Detail.posterHeight)
                        .clip(RoundedCornerShape(TvTokens.Radius.card)),
                )
                Column(
                    modifier = Modifier.widthIn(max = TvTokens.Detail.textMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.displayLarge,
                        color = TvTokens.Colors.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(data.meta, data.sourceName).joinToString("  ·  ")
                    if (meta.isNotEmpty()) InfoText(meta)
                    data.description?.let { InfoText(it, maxLines = 4) }
                    data.director?.let { InfoText("导演：$it", maxLines = 1) }
                    data.actor?.let { InfoText("演员：$it", maxLines = 2) }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val playFlag = data.resumeFlag ?: data.selectedFlag
                        Button(
                            onClick = { if (playFlag != null) onPlay(playFlag, data.resumeIndex ?: 0) },
                            enabled = playFlag != null && data.episodes.isNotEmpty(),
                            modifier = Modifier.focusRequester(playFocus),
                        ) {
                            Text(
                                when {
                                    data.episodes.isEmpty() -> "暂无可播放的剧集"
                                    data.resumeIndex != null -> "继续播放" + (data.resumeNote?.let { " · $it" } ?: "")
                                    else -> "播放"
                                },
                            )
                        }
                        OutlinedButton(onClick = onToggleFavorite) {
                            Text(if (data.favorite) "已收藏" else "收藏")
                        }
                    }
                }
            }

            if (data.flags.size > 1) {
                Section(title = "播放源") {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.flags, key = { it }) { flag ->
                            FilterChip(selected = flag == data.selectedFlag, onClick = { onSelectFlag(flag) }) {
                                Text(flag)
                            }
                        }
                    }
                }
            }

            if (data.episodes.isNotEmpty() && data.selectedFlag != null) {
                EpisodeSection(
                    episodes = data.episodes,
                    // 「继续播放」的集数只在同一播放源下高亮
                    resumeIndex = data.resumeIndex.takeIf { data.resumeFlag == data.selectedFlag },
                    onPlay = { index -> onPlay(data.selectedFlag, index) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun EpisodeSection(episodes: List<Episode>, resumeIndex: Int?, onPlay: (Int) -> Unit) {
    val pages = (episodes.size + EPISODE_PAGE - 1) / EPISODE_PAGE
    var page by rememberSaveable(episodes.size) {
        mutableIntStateOf(((resumeIndex ?: 0) / EPISODE_PAGE).coerceIn(0, (pages - 1).coerceAtLeast(0)))
    }
    Section(title = "选集（共 ${episodes.size} 集）") {
        if (pages > 1) {
            LazyRow(
                modifier = Modifier
                    .focusRestorer()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pages) { p ->
                    val from = p * EPISODE_PAGE + 1
                    val to = minOf((p + 1) * EPISODE_PAGE, episodes.size)
                    FilterChip(selected = p == page, onClick = { page = p }) { Text("$from-$to") }
                }
            }
        }
        val shown = episodes.subList(page * EPISODE_PAGE, minOf((page + 1) * EPISODE_PAGE, episodes.size))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(shown, key = { _, e -> e.index }) { _, episode ->
                FilterChip(
                    selected = episode.index == resumeIndex,
                    onClick = { onPlay(episode.index) },
                ) {
                    Text(episode.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvTokens.Colors.onBackground,
            modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
        )
        content()
    }
}

@Composable
private fun InfoText(text: String, maxLines: Int = 1) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TvTokens.Colors.onBackgroundSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailMessage(text: String, action: String?, onAction: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.headlineMedium, color = TvTokens.Colors.onBackgroundSecondary)
            if (action != null && onAction != null) {
                val focus = remember { FocusRequester() }
                Button(onClick = onAction, modifier = Modifier.focusRequester(focus)) { Text(action) }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
        }
    }
}
