package com.github.tvbox.osc.compose.nav

import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.HomeActivity

/**
 * 尚未用 Compose 重写的页面仍跳转到旧 Activity，保证重写期间功能完整。
 * 各页面重写完成后，对应方法改为 Compose Navigation 路由并在此删除。
 */
object LegacyNavigator {

    /** 与旧首页一致：无 id 或 msearch: 开头（如豆瓣推荐）按片名搜索，否则进入详情。 */
    fun openItem(context: Context, item: PosterItem) {
        val vodId = item.vodId
        if (vodId.isNullOrEmpty() || vodId.startsWith("msearch:")) {
            context.startActivity(Intent(context, FastSearchActivity::class.java).putExtra("title", item.title))
            return
        }
        context.startActivity(
            Intent(context, DetailActivity::class.java)
                .putExtra("id", vodId)
                .putExtra("sourceKey", item.sourceKey ?: "")
                .putExtra("title", item.title)
                .putExtra("picture", item.imageUrl ?: ""),
        )
    }

    fun openLegacyHome(context: Context) {
        context.startActivity(Intent(context, HomeActivity::class.java))
    }
}
