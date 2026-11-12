package com.github.tvbox.osc.compose.mine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.components.PosterCard
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.nav.LegacyNavigator
import com.github.tvbox.osc.compose.theme.TvTokens
import com.github.tvbox.osc.util.ScreenUtils

private sealed interface PendingConfirm {
    data class DeleteHistory(val item: PosterItem) : PendingConfirm
    data class DeleteFavorite(val item: PosterItem) : PendingConfirm
    data object ClearHistory : PendingConfirm
    data object ClearFavorites : PendingConfirm
}

/**
 * 我的 tab：功能入口（设置、直播、推送、传输）+ 观看历史 + 收藏。
 * 历史确认键直接续播，收藏确认键进入详情；「编辑」模式下确认键删除单项，均有确认提示。
 */
@Composable
fun MineTab(
    onOpenDetail: (PosterItem) -> Unit,
    onOpenPush: () -> Unit,
    onOpenTransfer: () -> Unit,
    restoreFocusKey: String? = null,
    onFocusRestored: () -> Unit = {},
    viewModel: MineViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editHistory by remember { mutableStateOf(false) }
    var editFavorites by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = TvTokens.Spacing.contentTop, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.shelfGap),
    ) {
        item(key = "entries") {
            Row(
                modifier = Modifier
                    .padding(horizontal = TvTokens.Spacing.screenHorizontal)
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(onClick = { LegacyNavigator.openSettings(context) }) { Text("设置") }
                OutlinedButton(onClick = { LegacyNavigator.openLive(context) }) { Text("直播") }
                OutlinedButton(onClick = onOpenPush) { Text("推送") }
                // 与上游一致：文件传输仅在电视或盒子设备上提供
                if (remember { ScreenUtils.isTv(context) }) {
                    OutlinedButton(onClick = onOpenTransfer) { Text("传输") }
                }
            }
        }
        item(key = "history") {
            RecordShelf(
                title = "观看历史",
                emptyText = "还没有观看记录",
                items = state.history,
                loaded = state.loaded,
                editing = editHistory,
                onToggleEdit = { editHistory = !editHistory },
                onClear = { confirm = PendingConfirm.ClearHistory },
                onItemClick = { item ->
                    if (editHistory) {
                        confirm = PendingConfirm.DeleteHistory(item)
                    } else if (!item.sourceKey.isNullOrEmpty() && !item.vodId.isNullOrEmpty()) {
                        // 按历史记录中的线路与集数直接续播
                        LegacyNavigator.resume(context, item.sourceKey, item.vodId, item.title, item.imageUrl)
                    }
                },
                restoreFocusKey = restoreFocusKey,
                onFocusRestored = onFocusRestored,
            )
        }
        item(key = "favorites") {
            RecordShelf(
                title = "我的收藏",
                emptyText = "还没有收藏，在详情页点「收藏」即可加入",
                items = state.favorites,
                loaded = state.loaded,
                editing = editFavorites,
                onToggleEdit = { editFavorites = !editFavorites },
                onClear = { confirm = PendingConfirm.ClearFavorites },
                onItemClick = { item ->
                    if (editFavorites) confirm = PendingConfirm.DeleteFavorite(item) else onOpenDetail(item)
                },
                restoreFocusKey = restoreFocusKey,
                onFocusRestored = onFocusRestored,
            )
        }
    }

    confirm?.let { pending ->
        val message = when (pending) {
            is PendingConfirm.DeleteHistory -> "删除「${pending.item.title}」的观看记录？"
            is PendingConfirm.DeleteFavorite -> "取消收藏「${pending.item.title}」？"
            PendingConfirm.ClearHistory -> "清空全部观看历史？此操作不可撤销"
            PendingConfirm.ClearFavorites -> "清空全部收藏？此操作不可撤销"
        }
        ConfirmDialog(
            message = message,
            onConfirm = {
                when (pending) {
                    is PendingConfirm.DeleteHistory -> viewModel.deleteHistory(pending.item)
                    is PendingConfirm.DeleteFavorite -> viewModel.deleteFavorite(pending.item)
                    PendingConfirm.ClearHistory -> viewModel.clearHistory().also { editHistory = false }
                    PendingConfirm.ClearFavorites -> viewModel.clearFavorites().also { editFavorites = false }
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun RecordShelf(
    title: String,
    emptyText: String,
    items: List<PosterItem>,
    loaded: Boolean,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onClear: () -> Unit,
    onItemClick: (PosterItem) -> Unit,
    restoreFocusKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = TvTokens.Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TvTokens.Colors.onBackground)
            if (items.isNotEmpty()) {
                OutlinedButton(onClick = onToggleEdit) { Text(if (editing) "完成" else "编辑") }
                OutlinedButton(onClick = onClear) { Text("清空") }
            }
            if (editing) {
                Text("编辑中：按确认键删除", style = MaterialTheme.typography.bodyMedium, color = TvTokens.Colors.onBackgroundTertiary)
            }
        }
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = TvTokens.Spacing.screenHorizontal)
                    .fillMaxWidth()
                    .height(TvTokens.Poster.height / 2)
                    .clip(RoundedCornerShape(TvTokens.Radius.card))
                    .background(TvTokens.Colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loaded) emptyText else "加载中…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvTokens.Colors.onBackgroundTertiary,
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = TvTokens.Spacing.screenHorizontal, vertical = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(TvTokens.Spacing.cardGap),
            ) {
                items(items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        dimmed = editing,
                        onFocusChanged = {},
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
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(TvTokens.Radius.panel))
                .background(TvTokens.Colors.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge, color = TvTokens.Colors.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 默认焦点在「取消」上，避免误删
                OutlinedButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = onConfirm) { Text("确定") }
            }
        }
    }
}
