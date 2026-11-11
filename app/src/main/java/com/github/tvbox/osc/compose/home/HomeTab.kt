package com.github.tvbox.osc.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.components.Shelf
import com.github.tvbox.osc.compose.theme.TvTokens

/**
 * 首页 tab（06 号票原型）：多条横向 shelf，刻意使用假数据，
 * 只用于判断观感，真实数据在 07 号票接入。
 */
@Composable
fun HomeTab(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = TvTokens.Spacing.contentTop, bottom = TvTokens.Spacing.shelfGap * 2),
        verticalArrangement = Arrangement.spacedBy(TvTokens.Spacing.shelfGap),
    ) {
        items(FakeShelves, key = { it.title }) { shelf ->
            Shelf(title = shelf.title, items = shelf.items, onItemClick = {})
        }
    }
}

private class FakeShelf(val title: String, val items: List<PosterItem>)

private fun fakeShelf(key: String, title: String, titles: List<String>, subtitle: (Int) -> String?): FakeShelf =
    FakeShelf(
        title = title,
        items = titles.mapIndexed { i, t ->
            PosterItem(
                id = "$key-$i",
                title = t,
                subtitle = subtitle(i),
                imageUrl = "https://picsum.photos/seed/tvbox-$key-$i/344/516",
            )
        },
    )

private val FakeShelves = listOf(
    fakeShelf(
        "history", "继续观看",
        listOf("长夜将尽", "山海之间", "星河旅人", "雾中城", "南方小镇", "白昼之光", "归途", "风起时"),
    ) { i -> "看到第 ${i + 3} 集" },
    fakeShelf(
        "favorite", "我的收藏",
        listOf("深海回声", "北境", "少年的你们", "午夜列车", "红色信号", "无名之地", "远方来信"),
    ) { null },
    fakeShelf(
        "movie", "热门电影",
        listOf("银色月光", "最后一班车", "时间之外", "海上花园", "沉默的证人", "孤岛", "破晓", "第七天", "边境线", "天空之城"),
    ) { i -> listOf("2024 · 剧情", "2023 · 悬疑", "2025 · 科幻", "2022 · 爱情")[i % 4] },
    fakeShelf(
        "drama", "热播剧集",
        listOf("长安十二时辰", "繁花似锦", "人间烟火", "大江大河", "山河月明", "清平乐", "开端", "漫长的季节", "隐秘的角落"),
    ) { i -> "更新至第 ${12 + i * 2} 集" },
    fakeShelf(
        "variety", "综艺",
        listOf("向往的生活", "乘风破浪", "奇葩说", "极限挑战", "中国好声音", "这就是街舞", "脱口秀大会"),
    ) { null },
    fakeShelf(
        "anime", "动漫",
        listOf("时光代理人", "雾山五行", "罗小黑战记", "天官赐福", "一人之下", "灵笼", "魔道祖师", "全职高手"),
    ) { null },
)
