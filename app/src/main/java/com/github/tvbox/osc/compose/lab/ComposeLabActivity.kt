package com.github.tvbox.osc.compose.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import com.github.tvbox.osc.compose.image.newTvImageLoader
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 05 号票的临时技术验证屏：验证 Compose for TV 焦点体系、Haze Glass 折射、Coil 网络图片
 * 三者在目标设备上可用。不进入正式导航，入口为设置页长按「关于」，后续由 06 号票替换。
 */
class ComposeLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            setSingletonImageLoaderFactory { context -> newTvImageLoader(context) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                ComposeLabScreen()
            }
        }
    }
}

private const val SAMPLE_IMAGE_URL = "https://picsum.photos/seed/tvbox/400/600"

private val cardColors = listOf(
    listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
    listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
    listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
)

@Composable
private fun ComposeLabScreen() {
    val hazeState = rememberHazeState()
    val firstCardFocus = remember { FocusRequester() }
    var clicks by remember { mutableIntStateOf(0) }
    var lastClicked by remember { mutableIntStateOf(-1) }
    val fps = rememberFps()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F)),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(count = 8) { row ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(count = 12) { column ->
                        val index = row * 100 + column
                        val focusModifier =
                            if (row == 0 && column == 0) Modifier.focusRequester(firstCardFocus) else Modifier
                        LabCard(
                            modifier = focusModifier,
                            index = index,
                            colors = cardColors[(row + column) % cardColors.size],
                            showNetworkImage = row == 0 && column == 0,
                            onClick = {
                                clicks++
                                lastClicked = index
                            },
                        )
                    }
                }
            }
        }

        // Liquid Glass 面板：其下 LazyColumn 滚动时应可见实时折射与镜面高光
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .width(760.dp)
                .height(112.dp)
                .hazeGlass(
                    input = HazeInput.Backdrop(hazeState),
                    style = GlassStyle {
                        tint(Color.White.copy(alpha = 0.10f))
                        optics(
                            GlassOptics(
                                refractionStrength = 0.8f,
                                refractionHeightFraction = 0.32f,
                            ),
                        )
                        specularIntensity(0.7f)
                        ambientResponse(0.6f)
                        edgeSoftness(14.dp)
                        shape(RoundedCornerShape(36.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Compose for TV · Haze Glass · Coil", color = Color.White, fontSize = 22.sp)
                    Text(
                        text = if (lastClicked >= 0) "确认键已触发 $clicks 次（最近：卡片 $lastClicked）" else "方向键移动焦点，确认键点击卡片",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("$fps FPS", color = Color.White, fontSize = 28.sp)
            }
        }
    }

    LaunchedEffect(Unit) {
        firstCardFocus.requestFocus()
    }
}

@Composable
private fun LabCard(
    modifier: Modifier,
    index: Int,
    colors: List<Color>,
    showNetworkImage: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.size(width = 180.dp, height = 260.dp),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        scale = CardDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (showNetworkImage) {
                AsyncImage(
                    model = SAMPLE_IMAGE_URL,
                    contentDescription = "Coil 网络图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = if (showNetworkImage) "网络图片" else "#$index",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/** 每秒统计一次实际渲染帧数，便于在真机上直接读取帧率。 */
@Composable
private fun rememberFps(): Int {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var windowStart = 0L
        while (true) {
            withFrameNanos { now ->
                if (windowStart == 0L) windowStart = now
                frames++
                if (now - windowStart >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    windowStart = now
                }
            }
        }
    }
    return fps
}
