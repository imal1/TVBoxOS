package com.github.tvbox.osc.compose.movies

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.data.AppSession
import com.github.tvbox.osc.compose.data.LegacySources
import com.github.tvbox.osc.util.DefaultConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUi(val id: String, val name: String)

/** 一组筛选条件：options 为「显示名 → 传给数据源的值」。 */
data class FilterUi(val key: String, val name: String, val options: List<Pair<String, String>>)

sealed interface MoviesStatus {
    data object Loading : MoviesStatus
    data object NotConfigured : MoviesStatus
    data class Failed(val message: String) : MoviesStatus
    data object Ready : MoviesStatus
}

data class MoviesUiState(
    val status: MoviesStatus = MoviesStatus.Loading,
    val sourceName: String? = null,
    val categories: List<CategoryUi> = emptyList(),
    val selectedCategoryId: String? = null,
    val filters: List<FilterUi> = emptyList(),
    val filterSelect: Map<String, String> = emptyMap(),
    val items: List<PosterItem> = emptyList(),
    val loadingFirstPage: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val listError: String? = null,
)

/** 影视 tab：当前数据源的分类浏览、动态筛选与分页加载；顶部可切换数据源。 */
class MoviesViewModel : ViewModel() {

    private val _state = MutableStateFlow(MoviesUiState())
    val state: StateFlow<MoviesUiState> = _state.asStateFlow()

    private var sorts: Map<String, MovieSort.SortData> = emptyMap()
    private var page = 0
    private var started = false
    private var pageJob: Job? = null
    private var categoriesJob: Job? = null

    fun start(activity: Activity) {
        if (started) return
        started = true
        load(activity, force = false)
        viewModelScope.launch {
            AppSession.currentSourceKey.drop(1).filterNotNull().distinctUntilChanged().collect {
                if (_state.value.status == MoviesStatus.Ready) loadCategories()
            }
        }
    }

    fun retry(activity: Activity) = load(activity, force = true)

    private fun load(activity: Activity, force: Boolean) {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            _state.update { it.copy(status = MoviesStatus.Loading) }
            when (val config = AppSession.ensureConfig(activity, force)) {
                LegacySources.ConfigResult.NotConfigured -> _state.update { it.copy(status = MoviesStatus.NotConfigured) }
                is LegacySources.ConfigResult.Failed -> _state.update { it.copy(status = MoviesStatus.Failed(config.message)) }
                LegacySources.ConfigResult.Ready -> loadCategories()
            }
        }
    }

    private fun loadCategories() {
        categoriesJob?.cancel()
        pageJob?.cancel()
        categoriesJob = viewModelScope.launch {
            _state.value = MoviesUiState(status = MoviesStatus.Loading, sourceName = LegacySources.homeSourceName())
            val sort = LegacySources.homeSort()
            val sourceKey = LegacySources.homeSourceKey()
            val list = DefaultConfig.adjustSort(sourceKey, sort?.classes?.sortList.orEmpty(), false)
                .filter { !it.id.isNullOrEmpty() && !it.name.isNullOrEmpty() }
                .distinctBy { it.id }
            sorts = list.associateBy { it.id }
            if (list.isEmpty()) {
                _state.update {
                    it.copy(status = MoviesStatus.Failed(if (sort == null) "数据源加载失败或超时" else "当前数据源没有分类"))
                }
                return@launch
            }
            _state.update {
                it.copy(status = MoviesStatus.Ready, categories = list.map { s -> CategoryUi(s.id, s.name) })
            }
            selectCategory(list.first().id)
        }
    }

    fun selectCategory(id: String) {
        val sort = sorts[id] ?: return
        val filters = sort.filters.orEmpty().mapNotNull { f ->
            val options = f.values?.entries?.map { it.key to it.value }.orEmpty().filter { it.first.isNotEmpty() }
            if (f.key.isNullOrEmpty() || options.isEmpty()) null else FilterUi(f.key, f.name ?: f.key, options)
        }
        _state.update {
            it.copy(
                selectedCategoryId = id,
                filters = filters,
                filterSelect = sort.filterSelect?.toMap().orEmpty(),
            )
        }
        reloadList()
    }

    /** value 为 null 表示「全部」，即清除该项筛选。 */
    fun selectFilter(key: String, value: String?) {
        val sort = _state.value.selectedCategoryId?.let { sorts[it] } ?: return
        val next = _state.value.filterSelect.toMutableMap().apply { if (value == null) remove(key) else put(key, value) }
        sort.filterSelect = HashMap(next)
        _state.update { it.copy(filterSelect = next) }
        reloadList()
    }

    private fun reloadList() {
        pageJob?.cancel()
        page = 0
        _state.update { it.copy(items = emptyList(), loadingFirstPage = true, loadingMore = false, endReached = false, listError = null) }
        loadNextPage()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingFirstPage || s.loadingMore || s.endReached || s.listError != null) return
        _state.update { it.copy(loadingMore = true) }
        loadNextPage()
    }

    private fun loadNextPage() {
        val sort = _state.value.selectedCategoryId?.let { sorts[it] } ?: return
        val categoryId = sort.id
        pageJob = viewModelScope.launch {
            val nextPage = page + 1
            val movie = runCatching { LegacySources.categoryPage(sort, nextPage) }.getOrNull()
            if (_state.value.selectedCategoryId != categoryId) return@launch
            val sourceKey = LegacySources.homeSourceKey()
            val newItems = movie?.videoList.orEmpty().mapNotNull { it.toPosterItem(sourceKey) }
            if (movie == null && page == 0) {
                _state.update { it.copy(loadingFirstPage = false, loadingMore = false, listError = "加载失败或超时") }
                return@launch
            }
            page = nextPage
            val end = newItems.isEmpty() || (movie != null && movie.pagecount in 1..nextPage)
            _state.update { s ->
                s.copy(
                    items = (s.items + newItems).distinctBy { it.id },
                    loadingFirstPage = false,
                    loadingMore = false,
                    endReached = end,
                )
            }
        }
    }

    fun switchSource(source: SourceBean) = AppSession.switchSource(source)

    fun sources(): List<SourceBean> = AppSession.sources()
}

private fun Movie.Video.toPosterItem(defaultSourceKey: String?): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    val key = sourceKey ?: defaultSourceKey
    return PosterItem(
        id = "movies|$key|${id ?: title}",
        title = title,
        subtitle = note?.takeIf { it.isNotBlank() },
        imageUrl = pic?.takeIf { it.isNotBlank() },
        sourceKey = key,
        vodId = id,
    )
}
