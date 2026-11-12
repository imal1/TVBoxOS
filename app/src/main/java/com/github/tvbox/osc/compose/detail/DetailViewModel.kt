package com.github.tvbox.osc.compose.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.compose.data.LegacySources
import com.github.tvbox.osc.compose.nav.AppRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Episode(val index: Int, val name: String)

data class DetailContent(
    val title: String,
    val poster: String?,
    val meta: String?,
    val sourceName: String?,
    val director: String?,
    val actor: String?,
    val description: String?,
    /** 播放源（线路）名称，顺序与旧详情页一致。 */
    val flags: List<String>,
    val selectedFlag: String?,
    val episodes: List<Episode>,
    /** 历史记录中的线路与集数，用于「继续播放」。 */
    val resumeFlag: String?,
    val resumeIndex: Int?,
    val resumeNote: String?,
    val favorite: Boolean,
)

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Failed(val message: String) : DetailUiState
    data class Content(val data: DetailContent) : DetailUiState
}

/** 详情页数据：复用旧数据层拉取详情，由 VodInfo 生成线路与剧集。 */
class DetailViewModel(savedState: SavedStateHandle) : ViewModel() {

    val sourceKey: String = savedState.get<String>(AppRoutes.ARG_SOURCE_KEY).orEmpty()
    val vodId: String = savedState.get<String>(AppRoutes.ARG_VOD_ID).orEmpty()
    private val fallbackTitle: String = savedState.get<String>(AppRoutes.ARG_TITLE).orEmpty()
    private val fallbackPoster: String? = savedState.get<String>(AppRoutes.ARG_POSTER)?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var vodInfo: VodInfo? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            val video = runCatching { LegacySources.detail(sourceKey, vodId) }.getOrNull()
            if (video == null) {
                _state.value = DetailUiState.Failed("详情加载失败或超时")
                return@launch
            }
            if (video.name.isNullOrBlank()) video.name = fallbackTitle.ifBlank { "TVBox" }
            if (video.pic.isNullOrBlank()) video.pic = fallbackPoster
            video.id = vodId
            val info = VodInfo().apply {
                setVideo(video)
                sourceKey = video.sourceKey ?: this@DetailViewModel.sourceKey
            }
            vodInfo = info
            val history = LegacySources.historyRecord(sourceKey, vodId)
            val flags = info.seriesFlags.orEmpty().mapNotNull { it.name }
            val resumeFlag = history?.playFlag?.takeIf { it in flags }
            val selectedFlag = resumeFlag ?: flags.firstOrNull()
            _state.value = DetailUiState.Content(
                DetailContent(
                    title = video.name,
                    poster = video.pic?.takeIf { it.isNotBlank() },
                    meta = listOfNotNull(
                        video.year.takeIf { it > 0 }?.toString(),
                        video.area?.takeIf { it.isNotBlank() },
                        video.type?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifEmpty { null },
                    sourceName = LegacySources.sourceName(sourceKey),
                    director = video.director.cleanHtml(),
                    actor = video.actor.cleanHtml(),
                    description = video.des.cleanHtml(),
                    flags = flags,
                    selectedFlag = selectedFlag,
                    episodes = episodesOf(info, selectedFlag),
                    resumeFlag = resumeFlag,
                    resumeIndex = resumeIndexOf(info, history, resumeFlag),
                    resumeNote = history?.playNote?.takeIf { it.isNotBlank() },
                    favorite = LegacySources.isFavorite(sourceKey, vodId),
                ),
            )
        }
    }

    fun selectFlag(flag: String) {
        val info = vodInfo ?: return
        _state.update { current ->
            if (current !is DetailUiState.Content) current
            else current.copy(data = current.data.copy(selectedFlag = flag, episodes = episodesOf(info, flag)))
        }
    }

    fun toggleFavorite() {
        val info = vodInfo ?: return
        val current = (_state.value as? DetailUiState.Content)?.data ?: return
        val target = !current.favorite
        viewModelScope.launch {
            LegacySources.setFavorite(sourceKey, info, target)
            _state.update { s -> if (s is DetailUiState.Content) s.copy(data = s.data.copy(favorite = target)) else s }
        }
    }

    /** 从播放返回后刷新「继续播放」的位置。 */
    fun refreshHistory() {
        val current = (_state.value as? DetailUiState.Content)?.data ?: return
        viewModelScope.launch {
            val history = LegacySources.historyRecord(sourceKey, vodId)
            val resumeFlag = history?.playFlag?.takeIf { it in current.flags }
            _state.update { s ->
                if (s !is DetailUiState.Content) s
                else s.copy(
                    data = s.data.copy(
                        resumeFlag = resumeFlag,
                        resumeIndex = vodInfo?.let { resumeIndexOf(it, history, resumeFlag) },
                        resumeNote = history?.playNote?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    /**
     * 历史记录中的集数换算为原始顺序：旧详情页开启倒序时，playIndex 是倒序列表中的下标。
     */
    private fun resumeIndexOf(info: VodInfo, history: VodInfo?, resumeFlag: String?): Int? {
        if (history == null || resumeFlag == null || history.playIndex < 0) return null
        val size = info.seriesMap?.get(resumeFlag)?.size ?: return null
        if (size == 0) return null
        val index = if (history.reverseSort) size - 1 - history.playIndex else history.playIndex
        return index.coerceIn(0, size - 1)
    }

    private fun episodesOf(info: VodInfo, flag: String?): List<Episode> =
        flag?.let { info.seriesMap?.get(it) }.orEmpty()
            .mapIndexed { i, series -> Episode(i, series.name?.takeIf { it.isNotBlank() } ?: "第 ${i + 1} 集") }

    private fun String?.cleanHtml(): String? =
        this?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim()?.takeIf { it.isNotEmpty() }
}
