package com.github.tvbox.osc.compose.data

import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.JsonParser
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * 搜索相关数据：拼音联想、热搜词、搜索历史与按数据源搜索。
 * 接口与旧搜索页一致（联想：腾讯视频 smart box；热搜：豆瓣热门剧集；历史：Hawk）。
 */
object SearchRepository {

    private const val SEARCH_TIMEOUT_MS = 20_000L
    private const val SUGGEST_URL = "https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box"
    private const val HOT_URL =
        "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0"

    /** 输入拼音首字母或片名片段，返回联想片名。失败返回空列表。 */
    suspend fun suggest(key: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = HttpUrl.parse(SUGGEST_URL)!!.newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("page_num", "0")
                .addQueryParameter("page_size", "20")
                .addQueryParameter("key", key)
                .build()
            val body = get(url.toString()) ?: return@runCatching emptyList()
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data").getAsJsonObject("search_data")
                .getAsJsonArray("vecGroupData")[0].asJsonObject
                .getAsJsonArray("group_data")
                .mapNotNull {
                    it.asJsonObject.getAsJsonObject("dtReportInfo")?.getAsJsonObject("reportData")
                        ?.get("keyword_txt")?.asString?.trim()?.takeIf { s -> s.isNotEmpty() }
                }
                .distinct()
        }.getOrDefault(emptyList())
    }

    suspend fun hotWords(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(HOT_URL) ?: return@runCatching emptyList()
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("subjects")
                .mapNotNull { it.asJsonObject.get("title")?.asString?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun history(): List<String> = Hawk.get(HawkConfig.SEARCH_HISTORY, ArrayList<String>()).orEmpty()

    fun addHistory(word: String) = HistoryHelper.setSearchHistory(word)

    fun clearHistory() = HistoryHelper.clearSearchHistory()

    /** 参与搜索的数据源 key（与旧搜索页的「搜索源」设置一致）。 */
    fun sourceKeys(): List<String> = SearchHelper.getSourcesForSearch().orEmpty().keys.toList()

    /** 全部可搜索的数据源 key。 */
    fun allSourceKeys(): List<String> = SearchHelper.getSources().orEmpty().keys.toList()

    /** 保存「搜索源」选择；全选时清除自定义（与旧版一致）。 */
    fun saveSourceKeys(selected: Collection<String>) {
        val all = allSourceKeys()
        SearchHelper.putCheckedSources(HashMap(selected.associateWith { "1" }), selected.size >= all.size)
    }

    fun sourceName(key: String): String = LegacySources.sourceName(key) ?: key

    /** 在单个数据源中搜索；失败或超时返回 null。 */
    suspend fun searchIn(sourceKey: String, word: String): List<Movie.Video>? {
        val viewModel = SourceViewModel()
        val result = LegacySources.awaitResult(viewModel.searchResult, timeoutMs = SEARCH_TIMEOUT_MS) {
            viewModel.getSearch(sourceKey, word, "compose-search")
        } ?: return null
        return result.movie?.videoList.orEmpty()
    }

    fun sortResults(videos: List<Movie.Video>, word: String): List<Movie.Video> =
        SearchHelper.sortSearchResults(ArrayList(videos), word).orEmpty()

    private fun get(url: String): String? {
        val client = OkGoHelper.getDefaultClient() ?: return null
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            return if (response.isSuccessful) response.body()?.string() else null
        }
    }
}
