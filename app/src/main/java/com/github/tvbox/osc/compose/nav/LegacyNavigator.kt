package com.github.tvbox.osc.compose.nav

import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.LivePlayActivity
import com.github.tvbox.osc.ui.activity.PushActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.activity.TransferActivity

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
            playIntent(context, sourceKey, vodId, title, poster)
                .putExtra(DetailActivity.EXTRA_PLAY_FLAG, flag)
                .putExtra(DetailActivity.EXTRA_PLAY_INDEX, index),
        )
    }

    /** 从观看历史继续播放：不指定线路与集数，由播放页按历史记录续播。 */
    fun resume(context: Context, sourceKey: String, vodId: String, title: String, poster: String?) {
        context.startActivity(playIntent(context, sourceKey, vodId, title, poster))
    }

    private fun playIntent(context: Context, sourceKey: String, vodId: String, title: String, poster: String?) =
        Intent(context, DetailActivity::class.java)
            .putExtra("id", vodId)
            .putExtra("sourceKey", sourceKey)
            .putExtra("title", title)
            .putExtra("picture", poster.orEmpty())
            .putExtra(DetailActivity.EXTRA_AUTO_FULL_PLAY, true)

    /** 设置页（14 号票重写前为旧页面）。 */
    fun openSettings(context: Context) = context.startActivity(Intent(context, SettingActivity::class.java))

    /** 直播（12 号票重写前为旧页面）。 */
    fun openLive(context: Context) = context.startActivity(Intent(context, LivePlayActivity::class.java))

    /** 局域网推送（#26 重写前为旧页面）。 */
    fun openPush(context: Context) = context.startActivity(Intent(context, PushActivity::class.java))

    /** 局域网文件传输（#26 重写前为旧页面）。 */
    fun openTransfer(context: Context) = context.startActivity(Intent(context, TransferActivity::class.java))
}
