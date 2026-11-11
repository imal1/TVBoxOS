package com.github.tvbox.osc.compose.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.setSingletonImageLoaderFactory
import com.github.tvbox.osc.compose.components.GlassTabBar
import com.github.tvbox.osc.compose.home.HomeTab
import com.github.tvbox.osc.compose.image.newTvImageLoader
import com.github.tvbox.osc.compose.nav.AppRoutes
import com.github.tvbox.osc.compose.nav.LegacyNavigator
import com.github.tvbox.osc.compose.theme.TvTheme
import com.github.tvbox.osc.compose.theme.TvTokens
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.StoragePermission
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 应用唯一的 Compose Activity：开机入口，所有 Compose 屏幕挂在这里的 NavHost 上，
 * tab 切换与屏幕切换均不产生 Activity 跳转。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoragePermission.promptOnFirstLaunch(this)
        ControlManager.get().startServer() // 局域网推送服务，原先只在旧首页启动
        setContent {
            setSingletonImageLoaderFactory { context -> newTvImageLoader(context) }
            TvTheme {
                AppNavHost(onExit = { finish() })
            }
        }
    }
}

@Composable
private fun AppNavHost(onExit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoutes.MAIN) {
        composable(AppRoutes.MAIN) { MainScreen(onExit = onExit) }
    }
}

private val Tabs = listOf("首页", "影视", "直播", "搜索", "我的")
private const val TAB_HOME = 0
private const val TAB_MINE = 4

@Composable
private fun MainScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val tabStateHolder = rememberSaveableStateHolder()
    val firstTabFocus = remember { FocusRequester() }
    var selectedTab by rememberSaveable { mutableStateOf(TAB_HOME) }
    var tabBarHasFocus by remember { mutableStateOf(true) }
    val openLegacy = { LegacyNavigator.openLegacyHome(context) }

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
                        onOpenItem = { LegacyNavigator.openItem(context, it) },
                        onOpenLegacy = openLegacy,
                    )
                    TAB_MINE -> MinePlaceholder(openLegacy)
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

    LaunchedEffect(Unit) { firstTabFocus.requestFocus() }
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

/** 「我的」tab 在 11 号票重写前的占位；提供回到旧版界面的入口，保证重写期间应用可用。 */
@Composable
private fun MinePlaceholder(onOpenLegacy: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "新界面重写中，完整功能请使用旧版界面",
                style = MaterialTheme.typography.titleLarge,
                color = TvTokens.Colors.onBackgroundSecondary,
            )
            Button(onClick = onOpenLegacy) {
                Text("打开旧版界面")
            }
        }
    }
}
