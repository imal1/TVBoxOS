package com.github.tvbox.osc.compose.image

import android.content.Context
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import com.github.tvbox.osc.util.OkGoHelper

/** Compose 侧统一的 Coil ImageLoader：网络请求走应用现有的 OkHttp 客户端。 */
fun newTvImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(
                NetworkFetcher.Factory(
                    networkClient = { OkHttp3NetworkClient { OkGoHelper.getDefaultClient() } },
                ),
            )
        }
        .build()
