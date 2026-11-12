package com.github.tvbox.osc.compose.data

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.viewmodel.SourceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 把旧数据层（ApiConfig 回调、SourceViewModel 的 LiveData、Room）包装成挂起函数，供 Compose 界面使用。
 * 旧代码保持不动；每次请求使用独立的 SourceViewModel，避免多个分类的结果互相串扰。
 */
object LegacySources {

    sealed interface ConfigResult {
        data object Ready : ConfigResult
        data object NotConfigured : ConfigResult
        data class Failed(val message: String) : ConfigResult
    }

    private const val REQUEST_TIMEOUT_MS = 30_000L

    /** 加载数据源配置与 spider jar，行为与旧首页一致（jar 加载失败不阻断）。 */
    suspend fun loadConfig(activity: Activity, useCache: Boolean): ConfigResult = withContext(Dispatchers.Main) {
        val config = suspendCancellableCoroutine { cont ->
            ApiConfig.get().loadConfig(useCache, object : ApiConfig.LoadConfigCallback {
                override fun success() {
                    if (cont.isActive) cont.resume(ConfigResult.Ready)
                }

                override fun error(msg: String) {
                    if (!cont.isActive) return
                    cont.resume(if (msg == "-1") ConfigResult.NotConfigured else ConfigResult.Failed(msg))
                }

                override fun notice(msg: String) = Unit
            }, activity)
        }
        if (config != ConfigResult.Ready) return@withContext config
        val spider = ApiConfig.get().spider
        if (spider.isNullOrEmpty()) return@withContext config
        suspendCancellableCoroutine { cont ->
            ApiConfig.get().loadJar(useCache, spider, object : ApiConfig.LoadConfigCallback {
                override fun success() {
                    if (cont.isActive) cont.resume(ConfigResult.Ready)
                }

                // 旧首页同样只提示、不阻断：部分站点仍可用
                override fun error(msg: String) {
                    if (cont.isActive) cont.resume(ConfigResult.Ready)
                }

                override fun notice(msg: String) = Unit
            })
        }
    }

    fun homeSourceKey(): String? = ApiConfig.get().homeSourceBean?.key?.takeIf { it.isNotEmpty() }

    fun homeSourceName(): String? = ApiConfig.get().homeSourceBean?.name?.takeIf { it.isNotEmpty() }

    /** 当前数据源的分类与首页推荐。 */
    suspend fun homeSort(): AbsSortXml? {
        val viewModel = SourceViewModel()
        return awaitFirst(viewModel.sortResult) { viewModel.getSort(homeSourceKey()) }
    }

    /** 某个分类指定页的影片（含 page / pagecount）；失败或超时返回 null。 */
    suspend fun categoryPage(sort: MovieSort.SortData, page: Int): Movie? {
        val viewModel = SourceViewModel()
        return awaitFirst(viewModel.listResult) { viewModel.getList(sort, page) }?.movie
    }

    /** 某个分类第一页的影片。 */
    suspend fun categoryVideos(sort: MovieSort.SortData): List<Movie.Video> {
        val viewModel = SourceViewModel()
        val result = awaitFirst(viewModel.listResult) { viewModel.getList(sort, 1) }
        return result?.movie?.videoList.orEmpty()
    }

    /** 影片详情；失败或超时返回 null。 */
    suspend fun detail(sourceKey: String, vodId: String): Movie.Video? {
        val viewModel = SourceViewModel()
        val result = awaitFirst(viewModel.detailResult) { viewModel.getDetail(sourceKey, vodId) }
        return result?.movie?.videoList?.firstOrNull()
    }

    suspend fun historyRecord(sourceKey: String, vodId: String): VodInfo? = withContext(Dispatchers.IO) {
        RoomDataManger.getVodInfo(sourceKey, vodId)
    }

    suspend fun isFavorite(sourceKey: String, vodId: String): Boolean = withContext(Dispatchers.IO) {
        RoomDataManger.isVodCollect(sourceKey, vodId)
    }

    suspend fun setFavorite(sourceKey: String, vodInfo: VodInfo, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (favorite) RoomDataManger.insertVodCollect(sourceKey, vodInfo)
        else RoomDataManger.deleteVodCollect(sourceKey, vodInfo)
    }

    fun sourceName(sourceKey: String?): String? =
        sourceKey?.let { ApiConfig.get().getSource(it)?.name }?.takeIf { it.isNotEmpty() }

    suspend fun history(limit: Int): List<VodInfo> = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodRecord(limit).orEmpty()
    }

    suspend fun favorites(): List<VodCollect> = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodCollect().orEmpty()
    }

    /** 订阅 LiveData 后发起请求，取第一个结果；超时返回 null。 */
    private suspend fun <T> awaitFirst(liveData: LiveData<T>, request: () -> Unit): T? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val observer = object : Observer<T> {
                        override fun onChanged(value: T) {
                            liveData.removeObserver(this)
                            if (cont.isActive) cont.resume(value)
                        }
                    }
                    liveData.observeForever(observer)
                    cont.invokeOnCancellation {
                        // 取消可能发生在任意线程，移除观察者必须回到主线程
                        android.os.Handler(android.os.Looper.getMainLooper()).post { liveData.removeObserver(observer) }
                    }
                    request()
                }
            }
        }
}
