package com.github.tvbox.osc.compose.data

import android.app.Activity
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Compose 界面共享的会话状态：
 * - 数据源配置加载成功后不再重复加载，多个页面同时请求时排队共用结果；
 * - 「当前数据源」全应用唯一（与旧版 ApiConfig.setSourceBean 一致），切换后各页面据此刷新。
 */
object AppSession {

    private val configLock = Mutex()
    private var configResult: LegacySources.ConfigResult? = null

    private val _currentSourceKey = MutableStateFlow<String?>(null)
    val currentSourceKey: StateFlow<String?> = _currentSourceKey.asStateFlow()

    /** 确保配置已加载；force 为 true 时重新加载（重试）。 */
    suspend fun ensureConfig(activity: Activity, force: Boolean = false): LegacySources.ConfigResult =
        configLock.withLock {
            configResult?.takeIf { !force && it == LegacySources.ConfigResult.Ready }?.let { return@withLock it }
            val result = LegacySources.loadConfig(activity, useCache = false)
            configResult = result
            if (result == LegacySources.ConfigResult.Ready) _currentSourceKey.value = LegacySources.homeSourceKey()
            result
        }

    fun sources(): List<SourceBean> = ApiConfig.get().switchSourceBeanList.orEmpty()

    fun switchSource(source: SourceBean) {
        if (source.key == _currentSourceKey.value) return
        ApiConfig.get().setSourceBean(source)
        _currentSourceKey.value = source.key
    }
}
