package com.github.tvbox.osc.compose.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.data.LegacySources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MineUiState(
    val loaded: Boolean = false,
    val history: List<PosterItem> = emptyList(),
    val favorites: List<PosterItem> = emptyList(),
)

/** 我的 tab：观看历史与收藏（浏览、继续播放、删除单项、清空）。 */
class MineViewModel : ViewModel() {

    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    private var historyRecords: Map<String, VodInfo> = emptyMap()
    private var favoriteRecords: Map<String, VodCollect> = emptyMap()

    fun refresh() {
        viewModelScope.launch {
            val history = LegacySources.history(HISTORY_LIMIT)
            val favorites = LegacySources.favorites()
            historyRecords = history.associateBy { "history|${it.sourceKey}|${it.id}" }
            favoriteRecords = favorites.associateBy { "favorite|${it.sourceKey}|${it.vodId}" }
            _state.value = MineUiState(
                loaded = true,
                history = history.mapNotNull { it.toItem() }.distinctBy { it.id },
                favorites = favorites.mapNotNull { it.toItem() }.distinctBy { it.id },
            )
        }
    }

    fun deleteHistory(item: PosterItem) {
        val record = historyRecords[item.id] ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RoomDataManger.deleteVodRecord(record.sourceKey, record) }
            refresh()
        }
    }

    fun deleteFavorite(item: PosterItem) {
        val record = favoriteRecords[item.id] ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RoomDataManger.deleteVodCollect(record.id) }
            refresh()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RoomDataManger.deleteVodRecordAll() }
            refresh()
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RoomDataManger.deleteVodCollectAll() }
            refresh()
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 100
    }
}

private fun VodInfo.toItem(): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return PosterItem(
        id = "history|$sourceKey|$id",
        title = title,
        subtitle = playNote?.takeIf { it.isNotBlank() }?.let { "看到 $it" } ?: "未开始播放",
        imageUrl = pic?.takeIf { it.isNotBlank() },
        sourceKey = sourceKey,
        vodId = id,
    )
}

private fun VodCollect.toItem(): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return PosterItem(
        id = "favorite|$sourceKey|$vodId",
        title = title,
        subtitle = LegacySources.sourceName(sourceKey),
        imageUrl = pic?.takeIf { it.isNotBlank() },
        sourceKey = sourceKey,
        vodId = vodId,
    )
}
