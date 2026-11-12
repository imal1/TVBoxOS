package com.github.tvbox.osc.compose.home

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.components.Shelf
import com.github.tvbox.osc.compose.theme.TvTokens

/**
 * 首页 tab：顶部 hero 随焦点展示当前影片（背景为其海报的模糊放大），
 * 下方为历史、收藏与当前数据源的推荐、各分类 shelf。
 */
@Composable
fun HomeTab(
    onOpenItem: (PosterItem) -> Unit,
    onOpenLegacy: () -> Unit,
    modifier: Modifier = Modifier,
    restoreFocusKey: String? = null,
    onFocusRestored: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val activity = LocalContext.current as Activity
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded(activity) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshLocal()
        onPauseOrDispose { }
    }

    when (val current = state) {
        HomeUiState.Loading -> HomeLoading(modifier)
        HomeUiState.NotConfigured -> HomeMessage(
            title = "尚未配置数据源",
            detail = "请先在旧版界面的「设置 → 配置地址」中填写数据源",
            primaryAction = "打开旧版界面" to onOpenLegacy,
            modifier = modifier,
        )
        is HomeUiState.Failed -> HomeMessage(
            title = "首页加载失败",
            detail = current.message,
            primaryAction = "重试" to { viewModel.retry(activity) },
            secondaryAction = "打开旧版界面" to onOpenLegacy,
            modifier = modifier,
        )
        is HomeUiState.Content -> HomeContent(current, onOpenItem, restoreFocusKey, onFocusRestored, modifier)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onOpenItem: (PosterItem) -> Unit,
    restoreFocusKey: String?,
    onFocusRestored: () -> Unit,
    modifier: Modifier,
) {
    val firstItem = state.shelves.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
    var focusedItem by remember { mutableStateOf<PosterItem?>(null) }
    val heroItem = focusedItem ?: firstItem
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        HeroBackdrop(item = heroItem)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = TvTokens.Spacing.contentTop, bottom = TvTokens.Spacing.shelfGap * 2),
            verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.shelfGap),
        ) {
            item(key = "hero") { HeroInfo(item = heroItem, sourceName = state.sourceName) }
            items(state.shelves, key = { it.key }) { shelf ->
                Shelf(
                    title = shelf.title,
                    items = shelf.items,
                    loading = shelf.loading,
                    onItemClick = onOpenItem,
                    onItemFocused = { focusedItem = it },
                    restoreFocusKey = restoreFocusKey,
                    onFocusRestored = onFocusRestored,
                )
            }
        }
    }
}

@Composable
private fun HomeLoading(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        HeroBackdrop(item = null)
        Column(
            modifier = Modifier.padding(top = TvTokens.Spacing.contentTop),
            verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.shelfGap),
        ) {
            Text(
                text = "正在加载数据源…",
                style = MaterialTheme.typography.headlineMedium,
                color = TvTokens.Colors.onBackgroundSecondary,
                modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
            )
            Shelf(title = "", items = emptyList(), onItemClick = {}, loading = true)
            Shelf(title = "", items = emptyList(), onItemClick = {}, loading = true)
        }
    }
}

@Composable
private fun HomeMessage(
    title: String,
    detail: String,
    primaryAction: Pair<String, () -> Unit>,
    modifier: Modifier,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TvTokens.Colors.onBackground)
            Text(text = detail, style = MaterialTheme.typography.bodyLarge, color = TvTokens.Colors.onBackgroundSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = primaryAction.second) { Text(primaryAction.first) }
                if (secondaryAction != null) {
                    OutlinedButton(onClick = secondaryAction.second) { Text(secondaryAction.first) }
                }
            }
        }
    }
}
