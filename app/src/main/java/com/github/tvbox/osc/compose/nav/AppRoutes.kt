package com.github.tvbox.osc.compose.nav

import android.net.Uri

/** Compose Navigation 路由。后续页面（设置等）重写后在此新增。 */
object AppRoutes {
    const val MAIN = "main"

    const val ARG_SOURCE_KEY = "sourceKey"
    const val ARG_VOD_ID = "vodId"
    const val ARG_TITLE = "title"
    const val ARG_POSTER = "poster"
    const val DETAIL = "detail/{$ARG_SOURCE_KEY}/{$ARG_VOD_ID}?$ARG_TITLE={$ARG_TITLE}&$ARG_POSTER={$ARG_POSTER}"

    fun detail(sourceKey: String, vodId: String, title: String, poster: String?): String =
        "detail/${Uri.encode(sourceKey)}/${Uri.encode(vodId)}" +
            "?$ARG_TITLE=${Uri.encode(title)}&$ARG_POSTER=${Uri.encode(poster.orEmpty())}"
}
