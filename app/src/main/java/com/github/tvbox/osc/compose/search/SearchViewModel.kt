package com.github.tvbox.osc.compose.search

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.data.AppSession
import com.github.tvbox.osc.compose.data.LegacySources
import com.github.tvbox.osc.compose.data.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SearchUiState(
    val input: String = "",
    val suggestions: List<String> = emptyList(),
    val hotWords: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    /** 当前（或最近一次）搜索的关键词；为 null 表示还未搜索。 */
    val searchedWord: String? = null,
    val results: List<PosterItem> = emptyList(),
    val totalSources: Int = 0,
    val finishedSources: Int = 0,
    val failedSources: Int = 0,
    val notConfigured: Boolean = false,
) {
    val searching: Boolean get() = searchedWord != null && finishedSources < totalSources
}

/** 搜索 tab：遥控器键盘输入、拼音联想、热搜与历史、跨数据源聚合搜索。 */
class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var started = false

    fun start(activity: Activity) {
        if (started) return
        started = true
        _state.update { it.copy(history = SearchRepository.history()) }
        viewModelScope.launch {
            val hot = SearchRepository.hotWords()
            _state.update { it.copy(hotWords = hot) }
        }
        viewModelScope.launch {
            val config = AppSession.ensureConfig(activity)
            if (config == LegacySources.ConfigResult.NotConfigured) _state.update { it.copy(notConfigured = true) }
        }
    }

    fun type(text: String) = setInput(_state.value.input + text)

    fun backspace() = setInput(_state.value.input.dropLast(1))

    fun clearInput() = setInput("")

    private fun setInput(value: String) {
        _state.update { it.copy(input = value) }
        suggestJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val words = SearchRepository.suggest(value)
            _state.update { if (it.input == value) it.copy(suggestions = words) else it }
        }
    }

    fun clearHistory() {
        SearchRepository.clearHistory()
        _state.update { it.copy(history = emptyList()) }
    }

    /** 跨数据源搜索：每个源独立请求，单源失败或超时不影响其他源，结果随到随显示。 */
    fun search(word: String) {
        val keyword = word.trim()
        if (keyword.isEmpty()) return
        SearchRepository.addHistory(keyword)
        val sources = SearchRepository.sourceKeys()
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchedWord = keyword,
                results = emptyList(),
                totalSources = sources.size,
                finishedSources = 0,
                failedSources = 0,
                history = SearchRepository.history(),
            )
        }
        if (sources.isEmpty()) return
        searchJob = viewModelScope.launch {
            val permits = Semaphore(MAX_PARALLEL_SOURCES)
            val collected = mutableListOf<Movie.Video>()
            sources.forEach { key ->
                launch {
                    val videos = permits.withPermit { runCatching { SearchRepository.searchIn(key, keyword) }.getOrNull() }
                    videos?.forEach { if (it.sourceKey.isNullOrEmpty()) it.sourceKey = key }
                    if (_state.value.searchedWord != keyword) return@launch
                    if (!videos.isNullOrEmpty()) collected += videos
                    val items = SearchRepository.sortResults(collected, keyword)
                        .mapNotNull { v -> v.toPosterItem() }
                        .distinctBy { it.id }
                    val failed = videos == null
                    _state.update { s ->
                        if (s.searchedWord != keyword) s
                        else s.copy(
                            results = items,
                            finishedSources = s.finishedSources + 1,
                            failedSources = s.failedSources + if (failed) 1 else 0,
                        )
                    }
                }
            }
        }
    }

    /** 从其他页面带入的关键词：填入输入框并直接搜索。 */
    fun searchExternal(word: String) {
        suggestJob?.cancel()
        _state.update { it.copy(input = word, suggestions = emptyList()) }
        search(word)
    }

    /** 回到输入状态（清空结果）。 */
    fun resetResults() {
        searchJob?.cancel()
        _state.update { it.copy(searchedWord = null, results = emptyList(), totalSources = 0, finishedSources = 0, failedSources = 0) }
    }

    companion object {
        private const val SUGGEST_DEBOUNCE_MS = 300L
        private const val MAX_PARALLEL_SOURCES = 8
    }
}

private fun Movie.Video.toPosterItem(): PosterItem? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    val source = sourceKey?.let(SearchRepository::sourceName)
    return PosterItem(
        id = "search|$sourceKey|${id ?: title}",
        title = title,
        subtitle = listOfNotNull(source, note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null },
        imageUrl = pic?.takeIf { it.isNotBlank() },
        sourceKey = sourceKey,
        vodId = id,
    )
}
