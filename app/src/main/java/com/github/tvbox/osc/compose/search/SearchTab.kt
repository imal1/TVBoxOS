@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.github.tvbox.osc.compose.search

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.components.PosterCard
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.data.SearchRepository
import com.github.tvbox.osc.compose.theme.TvTokens

private val Letters = ('a'..'z').map { it.toString() }
private val Digits = ('0'..'9').map { it.toString() }

/**
 * 搜索 tab：全屏遥控器键盘（tvOS 风格横向字母排布）。中文通过拼音首字母或片段 + 联想词输入。
 * 未搜索时展示联想、热搜与历史；搜索后跨数据源聚合展示结果，显示各源进度。
 */
@Composable
fun SearchTab(
    onOpenItem: (PosterItem) -> Unit,
    externalQuery: String? = null,
    onExternalQueryConsumed: () -> Unit = {},
    restoreFocusKey: String? = null,
    onFocusRestored: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val activity = LocalContext.current as Activity
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSourcePicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.start(activity) }
    LaunchedEffect(externalQuery) {
        if (!externalQuery.isNullOrBlank()) {
            viewModel.searchExternal(externalQuery)
            onExternalQueryConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvTokens.Spacing.contentTop),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(end = TvTokens.Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.weight(1f)) { QueryField(state.input) }
            OutlinedButton(onClick = { showSourcePicker = true }) { Text("搜索源") }
        }
        Keyboard(
            onKey = viewModel::type,
            onBackspace = viewModel::backspace,
            onClear = viewModel::clearInput,
            onSearch = { viewModel.search(state.input) },
        )
        if (state.searchedWord == null) {
            WordsPanel(state, onPick = viewModel::search, onClearHistory = viewModel::clearHistory)
        } else {
            ResultsPanel(state, onOpenItem, viewModel::resetResults, restoreFocusKey, onFocusRestored)
        }
    }

    if (showSourcePicker) SearchSourcePicker(onDismiss = { showSourcePicker = false })
}

/** 选择参与搜索的数据源（与旧搜索页的「搜索源」设置共用存储）。 */
@Composable
private fun SearchSourcePicker(onDismiss: () -> Unit) {
    val all = remember { SearchRepository.allSourceKeys().map { it to SearchRepository.sourceName(it) } }
    var selected by remember { mutableStateOf(SearchRepository.sourceKeys().toSet().ifEmpty { all.map { it.first }.toSet() }) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(TvTokens.Radius.panel))
                .background(TvTokens.Colors.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "搜索源（已选 ${selected.size}/${all.size}）",
                style = MaterialTheme.typography.headlineMedium,
                color = TvTokens.Colors.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { selected = all.map { it.first }.toSet() }) { Text("全选") }
                OutlinedButton(onClick = { selected = emptySet() }) { Text("全不选") }
                Button(
                    onClick = {
                        if (selected.isNotEmpty()) SearchRepository.saveSourceKeys(selected)
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                ) { Text("确定") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .height(360.dp)
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(all, key = { it.first }) { (key, name) ->
                    FilterChip(
                        selected = key in selected,
                        onClick = { selected = if (key in selected) selected - key else selected + key },
                    ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun QueryField(input: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = TvTokens.Spacing.screenHorizontal)
            .fillMaxWidth()
            .height(TvTokens.Search.fieldHeight)
            .clip(RoundedCornerShape(TvTokens.Radius.tab))
            .background(TvTokens.Colors.surface)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = input.ifEmpty { "输入拼音首字母或片名片段，再从联想词中选择" },
            style = MaterialTheme.typography.titleLarge,
            color = if (input.isEmpty()) TvTokens.Colors.onBackgroundTertiary else TvTokens.Colors.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun Keyboard(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onSearch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyRow(Letters.map { it to { onKey(it) } })
        KeyRow(
            Digits.map { it to { onKey(it) } } + listOf(
                "空格" to { onKey(" ") },
                "删除" to onBackspace,
                "清空" to onClear,
                "搜索" to onSearch,
            ),
        )
    }
}

@Composable
private fun KeyRow(keys: List<Pair<String, () -> Unit>>) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(keys, key = { it.first }) { (label, action) -> KeyButton(label, action) }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (focused) TvTokens.Colors.focusedTabBackground else TvTokens.Colors.surface,
        label = "keyBackground",
    )
    val textColor by animateColorAsState(
        if (focused) TvTokens.Colors.onFocusedTab else TvTokens.Colors.onBackground,
        label = "keyText",
    )
    Box(
        modifier = Modifier
            .widthIn(min = TvTokens.Search.keySize)
            .height(TvTokens.Search.keySize)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}

@Composable
private fun WordsPanel(state: SearchUiState, onPick: (String) -> Unit, onClearHistory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.notConfigured) {
            SectionTitle("尚未配置数据源，请先在旧版界面「设置」中填写")
        }
        if (state.suggestions.isNotEmpty()) WordRow("猜你想搜", state.suggestions, onPick)
        if (state.hotWords.isNotEmpty()) WordRow("热门搜索", state.hotWords, onPick)
        if (state.history.isNotEmpty()) {
            WordRow("搜索历史", state.history, onPick, trailing = "清空历史" to onClearHistory)
        }
    }
}

@Composable
private fun WordRow(title: String, words: List<String>, onPick: (String) -> Unit, trailing: Pair<String, () -> Unit>? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(words, key = { it }) { word ->
                FilterChip(selected = false, onClick = { onPick(word) }) {
                    Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                item(key = "__trailing__") {
                    OutlinedButton(onClick = trailing.second) { Text(trailing.first) }
                }
            }
        }
    }
}

@Composable
private fun ResultsPanel(
    state: SearchUiState,
    onOpenItem: (PosterItem) -> Unit,
    onReset: () -> Unit,
    restoreFocusKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val progress = when {
                state.totalSources == 0 -> "没有可搜索的数据源"
                state.searching -> "正在搜索 ${state.finishedSources}/${state.totalSources} 个数据源"
                else -> "已搜索 ${state.totalSources} 个数据源"
            } + if (state.failedSources > 0) "（${state.failedSources} 个失败）" else ""
            Text(
                text = "「${state.searchedWord}」· $progress",
                style = MaterialTheme.typography.titleLarge,
                color = TvTokens.Colors.onBackgroundSecondary,
            )
            OutlinedButton(onClick = onReset) { Text("重新输入") }
        }
        when {
            state.results.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(TvTokens.Poster.width),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                contentPadding = PaddingValues(
                    start = TvTokens.Spacing.screenHorizontal,
                    end = TvTokens.Spacing.screenHorizontal,
                    top = 16.dp,
                    bottom = 48.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap),
                verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap + 8.dp),
            ) {
                items(state.results, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        dimmed = false,
                        onFocusChanged = {},
                        onClick = { onOpenItem(item) },
                        requestFocus = item.id == restoreFocusKey,
                        onFocusRequested = onFocusRestored,
                    )
                }
            }
            !state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到相关内容", style = MaterialTheme.typography.headlineMedium, color = TvTokens.Colors.onBackgroundSecondary)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TvTokens.Colors.onBackgroundTertiary,
        modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
    )
}
