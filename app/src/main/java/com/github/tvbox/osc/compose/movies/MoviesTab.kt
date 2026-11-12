@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.github.tvbox.osc.compose.movies

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.compose.components.PosterCard
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.theme.TvTokens

/** 距离末尾还剩多少张卡片时预加载下一页。 */
private const val PREFETCH_DISTANCE = 6

/**
 * 影视 tab：顶部切换数据源，左侧分类，右侧动态筛选 + 影片网格（滚动到底自动加载更多）。
 * 多数据源作为本页顶部的筛选维度之一，而不单独占用一个 tab。
 */
@Composable
fun MoviesTab(
    onOpenItem: (PosterItem) -> Unit,
    onOpenSettings: () -> Unit,
    restoreFocusKey: String? = null,
    onFocusRestored: () -> Unit = {},
    viewModel: MoviesViewModel = viewModel(),
) {
    val activity = LocalContext.current as Activity
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSourcePicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.start(activity) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvTokens.Spacing.contentTop),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("数据源", style = MaterialTheme.typography.titleLarge, color = TvTokens.Colors.onBackgroundSecondary)
            OutlinedButton(onClick = { showSourcePicker = true }, enabled = state.status != MoviesStatus.NotConfigured) {
                Text(state.sourceName ?: "选择数据源")
            }
        }
        when (val status = state.status) {
            MoviesStatus.Loading -> CenterMessage("正在加载分类…")
            MoviesStatus.NotConfigured -> CenterMessage("尚未配置数据源", "打开设置", onOpenSettings)
            is MoviesStatus.Failed -> CenterMessage(status.message, "重试") { viewModel.retry(activity) }
            MoviesStatus.Ready -> MoviesBrowser(state, viewModel, onOpenItem, restoreFocusKey, onFocusRestored)
        }
    }

    if (showSourcePicker) {
        SourcePicker(
            sources = remember { viewModel.sources() },
            onPick = {
                showSourcePicker = false
                viewModel.switchSource(it)
            },
            onDismiss = { showSourcePicker = false },
        )
    }
}

@Composable
private fun MoviesBrowser(
    state: MoviesUiState,
    viewModel: MoviesViewModel,
    onOpenItem: (PosterItem) -> Unit,
    restoreFocusKey: String?,
    onFocusRestored: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(TvTokens.Movies.categoryWidth)
                .fillMaxHeight()
                .focusRestorer(),
            contentPadding = PaddingValues(start = TvTokens.Spacing.screenHorizontal, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = category.id == state.selectedCategoryId,
                    onClick = { viewModel.selectCategory(category.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.filters.forEach { filter ->
                FilterRow(filter, state.filterSelect[filter.key]) { value -> viewModel.selectFilter(filter.key, value) }
            }
            when {
                state.loadingFirstPage -> CenterMessage("正在加载…")
                state.listError != null -> CenterMessage(state.listError, "重试") {
                    state.selectedCategoryId?.let(viewModel::selectCategory)
                }
                state.items.isEmpty() -> CenterMessage("这个分类下暂无内容")
                else -> MoviesGrid(state, viewModel::loadMore, onOpenItem, restoreFocusKey, onFocusRestored)
            }
        }
    }
}

@Composable
private fun FilterRow(filter: FilterUi, selectedValue: String?, onSelect: (String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodyMedium,
            color = TvTokens.Colors.onBackgroundTertiary,
            modifier = Modifier
                .padding(start = 24.dp)
                .widthIn(min = 56.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = 12.dp, end = TvTokens.Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__all__") {
                FilterChip(selected = selectedValue == null, onClick = { onSelect(null) }) { Text("全部") }
            }
            items(filter.options, key = { it.first + "|" + it.second }) { (label, value) ->
                FilterChip(selected = selectedValue == value, onClick = { onSelect(value) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun MoviesGrid(
    state: MoviesUiState,
    onLoadMore: () -> Unit,
    onOpenItem: (PosterItem) -> Unit,
    restoreFocusKey: String?,
    onFocusRestored: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, state.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.items.size - PREFETCH_DISTANCE) onLoadMore() }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(TvTokens.Poster.width),
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(start = 24.dp, end = TvTokens.Spacing.screenHorizontal, top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap + 8.dp),
    ) {
        items(state.items, key = { it.id }) { item ->
            PosterCard(
                item = item,
                dimmed = false,
                onFocusChanged = {},
                onClick = { onOpenItem(item) },
                requestFocus = item.id == restoreFocusKey,
                onFocusRequested = onFocusRestored,
            )
        }
        if (state.loadingMore) {
            item(key = "__loading__") {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TvTokens.Colors.onBackgroundTertiary)
                }
            }
        }
    }
}

@Composable
private fun SourcePicker(sources: List<SourceBean>, onPick: (SourceBean) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(TvTokens.Radius.panel))
                .background(TvTokens.Colors.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("选择数据源", style = MaterialTheme.typography.headlineMedium, color = TvTokens.Colors.onBackground)
            LazyColumn(
                modifier = Modifier.focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sources, key = { it.key }) { source ->
                    FilterChip(
                        selected = source.key == com.github.tvbox.osc.compose.data.AppSession.currentSourceKey.value,
                        onClick = { onPick(source) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(source.name ?: source.key, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = TvTokens.Colors.onBackgroundSecondary)
            if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        }
    }
}
