package com.github.tvbox.osc.compose.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.data.LegacySources
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeShelf(
    val key: String,
    val title: String,
    val items: List<PosterItem>,
    val loading: Boolean = false,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object NotConfigured : HomeUiState
    data class Failed(val message: String) : HomeUiState
    data class Content(val sourceName: String?, val shelves: List<HomeShelf>) : HomeUiState
}

/**
 * 首页数据：本地的历史、收藏 + 当前数据源的推荐与各分类。
 * 先出本地数据与分类骨架，各分类内容到达后逐条填充。
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var localShelves: List<HomeShelf> = emptyList()
    private var remoteShelves: List<HomeShelf> = emptyList()
    private var sourceName: String? = null
    private var loadJob: Job? = null
    private var loadedOnce = false

    fun loadIfNeeded(activity: Activity) {
        if (!loadedOnce) load(activity, useCache = false)
    }

    fun retry(activity: Activity) = load(activity, useCache = false)

    private fun load(activity: Activity, useCache: Boolean) {
        loadedOnce = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = HomeUiState.Loading
            when (val config = LegacySources.loadConfig(activity, useCache)) {
                LegacySources.ConfigResult.NotConfigured -> {
                    _state.value = HomeUiState.NotConfigured
                    return@launch
                }
                is LegacySources.ConfigResult.Failed -> {
                    _state.value = HomeUiState.Failed(config.message)
                    return@launch
                }
                LegacySources.ConfigResult.Ready -> Unit
            }
            sourceName = LegacySources.homeSourceName()
            localShelves = loadLocalShelves()
            remoteShelves = emptyList()
            publish()

            val sort = LegacySources.homeSort()
            val sourceKey = LegacySources.homeSourceKey()
            val recommend = sort?.videoList.orEmpty().mapNotNull { it.toPosterItem(sourceKey, "rec") }.distinctBy { it.id }
            val categories = sort?.classes?.sortList.orEmpty()
                .filter { !it.id.isNullOrEmpty() && !it.name.isNullOrEmpty() }
                .distinctBy { it.id }
                .take(MAX_CATEGORY_SHELVES)

            remoteShelves = buildList {
                if (recommend.isNotEmpty()) add(HomeShelf("rec", "站点推荐", recommend))
                categories.forEach { add(HomeShelf("cat-${it.id}", it.name, emptyList(), loading = true)) }
            }
            if (remoteShelves.isEmpty() && localShelves.isEmpty()) {
                _state.value = HomeUiState.Failed(
                    if (sort == null) "数据源加载失败或超时" else "当前数据源没有可展示的内容",
                )
                return@launch
            }
            publish()

            categories.forEach { category ->
                val key = "cat-${category.id}"
                val items = runCatching { LegacySources.categoryVideos(category) }.getOrDefault(emptyList())
                    .mapNotNull { it.toPosterItem(sourceKey, key) }
                    .distinctBy { it.id }
                remoteShelves = remoteShelves.mapNotNull { shelf ->
                    when {
                        shelf.key != key -> shelf
                        items.isEmpty() -> null // 拉取失败或为空的分类不显示
                        else -> shelf.copy(items = items, loading = false)
                    }
                }
                publish()
            }
        }
    }

    /** 从详情等页面返回时刷新历史与收藏。 */
    fun refreshLocal() {
        if (_state.value !is HomeUiState.Content) return
        viewModelScope.launch {
            localShelves = loadLocalShelves()
            publish()
        }
    }

    private suspend fun loadLocalShelves(): List<HomeShelf> {
        val history = LegacySources.history(HISTORY_LIMIT).mapNotNull { it.toPosterItem() }.distinctBy { it.id }
        val favorites = LegacySources.favorites().mapNotNull { it.toPosterItem() }.distinctBy { it.id }
        return buildList {
            if (history.isNotEmpty()) add(HomeShelf("history", "继续观看", history))
            if (favorites.isNotEmpty()) add(HomeShelf("favorite", "我的收藏", favorites))
        }
    }

    private fun publish() {
        _state.update { HomeUiState.Content(sourceName, localShelves + remoteShelves) }
    }

    companion object {
        private const val HISTORY_LIMIT = 30
        private const val MAX_CATEGORY_SHELVES = 8
    }
}

private fun Movie.Video.toPosterItem(defaultSourceKey: String?, shelfKey: String): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    val meta = listOfNotNull(
        year.takeIf { it > 0 }?.toString(),
        area?.takeIf { it.isNotBlank() },
        type?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifEmpty { null }
    val key = sourceKey ?: defaultSourceKey
    return PosterItem(
        id = "$shelfKey|$key|${id ?: title}",
        title = title,
        subtitle = note?.takeIf { it.isNotBlank() },
        imageUrl = pic?.takeIf { it.isNotBlank() },
        meta = meta,
        description = des?.trim()?.takeIf { it.isNotEmpty() },
        sourceKey = key,
        vodId = id,
    )
}

private fun VodInfo.toPosterItem(): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return PosterItem(
        id = "history|$sourceKey|$id",
        title = title,
        subtitle = playNote?.takeIf { it.isNotBlank() }?.let { "看到 $it" },
        imageUrl = pic?.takeIf { it.isNotBlank() },
        meta = listOfNotNull(year.takeIf { it > 0 }?.toString(), area, type).filter { it.isNotBlank() }
            .joinToString(" · ").ifEmpty { null },
        description = des?.trim()?.takeIf { it.isNotEmpty() },
        sourceKey = sourceKey,
        vodId = id,
    )
}

private fun VodCollect.toPosterItem(): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return PosterItem(
        id = "favorite|$sourceKey|$vodId",
        title = title,
        imageUrl = pic?.takeIf { it.isNotBlank() },
        sourceKey = sourceKey,
        vodId = vodId,
    )
}
