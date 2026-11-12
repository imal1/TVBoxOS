package com.github.tvbox.osc.compose.nav

import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.HomeActivity

/**
 * 尚未用 Compose 重写的页面仍跳转到旧 Activity，保证重写期间功能完整。
 * 各页面重写完成后，对应方法改为 Compose Navigation 路由并在此删除。
 */
object LegacyNavigator {

    /** 是否进入详情页；与旧首页一致，无 id 或 msearch: 开头（如豆瓣推荐）的按片名搜索。 */
    fun hasDetail(item: PosterItem): Boolean {
        val vodId = item.vodId
        return !vodId.isNullOrEmpty() && !vodId.startsWith("msearch:") && !item.sourceKey.isNullOrEmpty()
    }

    /** 选集后起播：旧 DetailActivity 的直接全屏播放模式（13 号票重写播放器后替换）。 */
    fun play(context: Context, sourceKey: String, vodId: String, title: String, poster: String?, flag: String, index: Int) {
        context.startActivity(
            Intent(context, DetailActivity::class.java)
                .putExtra("id", vodId)
                .putExtra("sourceKey", sourceKey)
                .putExtra("title", title)
                .putExtra("picture", poster.orEmpty())
                .putExtra(DetailActivity.EXTRA_AUTO_FULL_PLAY, true)
                .putExtra(DetailActivity.EXTRA_PLAY_FLAG, flag)
                .putExtra(DetailActivity.EXTRA_PLAY_INDEX, index),
        )
    }

    fun openLegacyHome(context: Context) {
        context.startActivity(Intent(context, HomeActivity::class.java))
    }
}
