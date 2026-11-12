package com.github.tvbox.osc.compose.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.setSingletonImageLoaderFactory
import com.github.tvbox.osc.compose.components.GlassTabBar
import com.github.tvbox.osc.compose.components.PosterItem
import com.github.tvbox.osc.compose.detail.DetailScreen
import com.github.tvbox.osc.compose.home.HomeTab
import com.github.tvbox.osc.compose.image.newTvImageLoader
import com.github.tvbox.osc.compose.lan.PushScreen
import com.github.tvbox.osc.compose.lan.TransferScreen
import com.github.tvbox.osc.compose.mine.MineTab
import com.github.tvbox.osc.compose.movies.MoviesTab
import com.github.tvbox.osc.compose.search.SearchTab
import com.github.tvbox.osc.compose.nav.AppRoutes
import com.github.tvbox.osc.compose.nav.LegacyNavigator
import com.github.tvbox.osc.compose.theme.TvTheme
import com.github.tvbox.osc.compose.theme.TvTokens
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.compose.data.AppSession
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.StoragePermission
import com.orhanobut.hawk.Hawk
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 应用唯一的 Compose Activity：开机入口，所有 Compose 屏幕挂在这里的 NavHost 上，
 * tab 切换与屏幕切换均不产生 Activity 跳转。
 */
class MainActivity : ComponentActivity() {

    /** 其他页面请求在搜索 tab 中搜索的关键词。 */
    private val pendingSearch = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSearch.value = intent?.getStringExtra(EXTRA_SEARCH)
        StoragePermission.promptOnFirstLaunch(this)
        // 设置项「开机默认进入直播」（旧首页的行为），仅冷启动时生效
        if (savedInstanceState == null && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) LegacyNavigator.openLive(this)
        ControlManager.get().startServer() // 局域网推送服务，原先只在旧首页启动
        setContent {
            setSingletonImageLoaderFactory { context -> newTvImageLoader(context) }
            TvTheme {
                AppNavHost(
                    onExit = { finish() },
                    pendingSearch = pendingSearch.value,
                    onPendingSearchConsumed = { pendingSearch.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SEARCH)?.let { pendingSearch.value = it }
    }

    override fun onDestroy() {
        if (isFinishing) ControlManager.get().stopServer()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SEARCH = "search"

        /** 设置中修改了配置地址、数据源等：丢弃已加载的配置，重建主界面重新加载。 */
        @JvmStatic
        fun restart(context: Context) {
            AppSession.invalidate()
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }

        /** 供旧页面与广播接收器调用：回到主界面并在搜索 tab 中搜索 title（为空时只切到搜索 tab）。 */
        @JvmStatic
        fun openSearch(context: Context, title: String?) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    // NEW_TASK：可能从广播接收器等非 Activity 上下文调用
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_SEARCH, title.orEmpty()),
            )
        }
    }
}

private const val SCREEN_TRANSITION_MS = 320

@Composable
private fun AppNavHost(onExit: () -> Unit, pendingSearch: String?, onPendingSearchConsumed: () -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    // 屏与屏之间是连续的淡入淡出 + 轻微缩放，背景始终为暗色，不出现整屏黑闪
    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
        modifier = Modifier.background(TvTokens.Colors.background),
        enterTransition = { fadeIn(tween(SCREEN_TRANSITION_MS)) + scaleIn(tween(SCREEN_TRANSITION_MS), initialScale = 0.96f) },
        exitTransition = { fadeOut(tween(SCREEN_TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(SCREEN_TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(SCREEN_TRANSITION_MS)) + scaleOut(tween(SCREEN_TRANSITION_MS), targetScale = 0.96f) },
    ) {
        composable(AppRoutes.MAIN) {
            MainScreen(
                onExit = onExit,
                pendingSearch = pendingSearch,
                onPendingSearchConsumed = onPendingSearchConsumed,
                onOpenDetail = { item ->
                    navController.navigate(
                        AppRoutes.detail(item.sourceKey.orEmpty(), item.vodId.orEmpty(), item.title, item.imageUrl),
                    )
                },
                onOpenRoute = { route -> navController.navigate(route) },
            )
        }
        composable(AppRoutes.PUSH) { PushScreen() }
        composable(AppRoutes.TRANSFER) { TransferScreen() }
        composable(
            route = AppRoutes.DETAIL,
            arguments = listOf(
                navArgument(AppRoutes.ARG_SOURCE_KEY) { type = NavType.StringType },
                navArgument(AppRoutes.ARG_VOD_ID) { type = NavType.StringType },
                navArgument(AppRoutes.ARG_TITLE) { type = NavType.StringType; defaultValue = "" },
                navArgument(AppRoutes.ARG_POSTER) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            DetailScreen(
                onPlay = { sourceKey, vodId, title, poster, flag, index ->
                    LegacyNavigator.play(context, sourceKey, vodId, title, poster, flag, index)
                },
            )
        }
    }
}

private val Tabs = listOf("首页", "影视", "直播", "搜索", "我的")
private const val TAB_HOME = 0
private const val TAB_MOVIES = 1
private const val TAB_LIVE = 2
private const val TAB_SEARCH = 3
private const val TAB_MINE = 4

@Composable
private fun MainScreen(
    onExit: () -> Unit,
    onOpenDetail: (PosterItem) -> Unit,
    onOpenRoute: (String) -> Unit,
    pendingSearch: String? = null,
    onPendingSearchConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val tabStateHolder = rememberSaveableStateHolder()
    val firstTabFocus = remember { FocusRequester() }
    var selectedTab by rememberSaveable { mutableStateOf(TAB_HOME) }
    var tabBarHasFocus by remember { mutableStateOf(true) }
    // 从详情页返回时，焦点回到进入前的卡片
    var restoreFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openSettings = { LegacyNavigator.openSettings(context) }
    // 搜索 tab 待搜索的关键词：来自其他页面（Intent）或无详情的卡片（如豆瓣推荐）
    var searchQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingSearch) {
        if (pendingSearch != null) {
            if (pendingSearch.isNotBlank()) searchQuery = pendingSearch
            selectedTab = TAB_SEARCH
            onPendingSearchConsumed()
        }
    }
    val openItem: (PosterItem) -> Unit = { item ->
        if (LegacyNavigator.hasDetail(item)) {
            restoreFocusKey = item.id
            onOpenDetail(item)
        } else {
            searchQuery = item.title
            selectedTab = TAB_SEARCH
        }
    }

    // tvOS 习惯：内容区按返回先回到 tab bar，在 tab bar 上再按返回才退出
    BackHandler {
        if (tabBarHasFocus) onExit() else firstTabFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvTokens.Colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            tabStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    TAB_HOME -> HomeTab(
                        onOpenItem = openItem,
                        onOpenSettings = openSettings,
                        restoreFocusKey = restoreFocusKey,
                        onFocusRestored = { restoreFocusKey = null },
                    )
                    TAB_MOVIES -> MoviesTab(
                        onOpenItem = openItem,
                        onOpenSettings = openSettings,
                        restoreFocusKey = restoreFocusKey,
                        onFocusRestored = { restoreFocusKey = null },
                    )
                    TAB_SEARCH -> SearchTab(
                        onOpenItem = openItem,
                        externalQuery = searchQuery,
                        onExternalQueryConsumed = { searchQuery = null },
                        restoreFocusKey = restoreFocusKey,
                        onFocusRestored = { restoreFocusKey = null },
                    )
                    TAB_LIVE -> LivePlaceholder(onOpenLive = { LegacyNavigator.openLive(context) })
                    TAB_MINE -> MineTab(
                        onOpenDetail = openItem,
                        onOpenPush = { onOpenRoute(AppRoutes.PUSH) },
                        onOpenTransfer = { onOpenRoute(AppRoutes.TRANSFER) },
                        restoreFocusKey = restoreFocusKey,
                        onFocusRestored = { restoreFocusKey = null },
                    )
                    else -> EmptyTab(Tabs[selectedTab])
                }
            }
        }

        GlassTabBar(
            tabs = Tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            hazeState = hazeState,
            firstTabFocus = firstTabFocus,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TvTokens.Spacing.screenTop)
                .onFocusChanged { tabBarHasFocus = it.hasFocus },
        )
    }

    LaunchedEffect(Unit) {
        if (restoreFocusKey == null) runCatching { firstTabFocus.requestFocus() }
    }
}

@Composable
private fun EmptyTab(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$title · 即将推出",
            style = MaterialTheme.typography.headlineMedium,
            color = TvTokens.Colors.onBackgroundTertiary,
        )
    }
}

/** 直播 tab 在 12 号票重写前的占位：进入旧直播页。 */
@Composable
private fun LivePlaceholder(onOpenLive: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "直播界面重写中",
                style = MaterialTheme.typography.titleLarge,
                color = TvTokens.Colors.onBackgroundSecondary,
            )
            Button(onClick = onOpenLive) { Text("打开直播") }
        }
    }
}
