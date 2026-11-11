package com.github.tvbox.osc.compose.main

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.setSingletonImageLoaderFactory
import com.github.tvbox.osc.compose.components.GlassTabBar
import com.github.tvbox.osc.compose.home.HomeTab
import com.github.tvbox.osc.compose.image.newTvImageLoader
import com.github.tvbox.osc.compose.theme.TvTheme
import com.github.tvbox.osc.compose.theme.TvTokens
import com.github.tvbox.osc.ui.activity.HomeActivity
import com.github.tvbox.osc.util.StoragePermission
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Compose 主界面（单 Activity）。开机即进入，取代旧的 View 首页。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoragePermission.promptOnFirstLaunch(this)
        setContent {
            setSingletonImageLoaderFactory { context -> newTvImageLoader(context) }
            TvTheme {
                MainScreen(
                    onOpenLegacy = { startActivity(Intent(this, HomeActivity::class.java)) },
                    onExit = { finish() },
                )
            }
        }
    }
}

private val Tabs = listOf("首页", "影视", "直播", "搜索", "我的")
private const val TAB_MINE = 4

@Composable
private fun MainScreen(onOpenLegacy: () -> Unit, onExit: () -> Unit) {
    val hazeState = rememberHazeState()
    val tabStateHolder = rememberSaveableStateHolder()
    val firstTabFocus = remember { FocusRequester() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var tabBarHasFocus by remember { mutableStateOf(true) }

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
                    0 -> HomeTab()
                    TAB_MINE -> MinePlaceholder(onOpenLegacy)
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
