package com.github.tvbox.osc.compose.lan

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.tvbox.osc.compose.theme.TvTokens
import com.github.tvbox.osc.receiver.PushReceiver
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.server.RemoteServer
import com.github.tvbox.osc.ui.tv.QRCodeGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val QR_SIZE_PX = 480

/** 局域网推送：手机或电脑扫码打开推送页，把视频链接或配置推到电视。 */
@Composable
fun PushScreen() {
    val context = LocalContext.current
    val address = remember { ControlManager.get().getAddress(false) }
    val page = address + "push.html"
    val qr = rememberQr(page)
    val focus = remember { FocusRequester() }
    LanLayout(
        title = "局域网推送",
        qr = qr,
        hint = "手机或电脑扫描二维码，或在浏览器访问：\n$page",
    ) {
        Button(onClick = { pushClipboard(context) }, modifier = Modifier.focusRequester(focus)) {
            Text("推送剪贴板内容")
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** 局域网文件传输：扫码上传文件到 TVBox/Transfer，展示进度与文件列表，可打开或删除。 */
@Composable
fun TransferScreen() {
    val context = LocalContext.current
    val address = remember { ControlManager.get().getAddress(false) }
    val page = address + "transfer.html"
    val qr = rememberQr(page)
    var files by remember { mutableStateOf(listTransferFiles()) }
    var progress by remember { mutableStateOf<TransferProgress?>(null) }

    LifecycleResumeEffect(Unit) {
        files = listTransferFiles()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            progress = fetchProgress(address)
            files = listTransferFiles()
            delay(1_000)
        }
    }

    LanLayout(
        title = "局域网文件传输",
        qr = qr,
        hint = "手机或电脑扫描二维码，或在浏览器访问：\n$page",
    ) {
        Text(
            text = progress.describe(files),
            style = MaterialTheme.typography.bodyLarge,
            color = TvTokens.Colors.onBackgroundSecondary,
        )
        if (files.isEmpty()) {
            Text("暂无文件", style = MaterialTheme.typography.bodyLarge, color = TvTokens.Colors.onBackgroundTertiary)
        } else {
            LazyColumn(
                modifier = Modifier
                    .height(320.dp)
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.name }) { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { openFile(context, file) }, modifier = Modifier.width(420.dp)) {
                            Text(
                                "${file.name}  ${readable(file.length())}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(onClick = { if (file.delete()) files = listTransferFiles() }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanLayout(title: String, qr: Bitmap?, hint: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(TvTokens.Colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvTokens.Spacing.screenHorizontal, vertical = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(TvTokens.Lan.qrSize)
                    .clip(RoundedCornerShape(TvTokens.Radius.card))
                    .background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) Image(qr.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, style = MaterialTheme.typography.displayLarge, color = TvTokens.Colors.onBackground)
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = TvTokens.Colors.onBackgroundSecondary)
                content()
            }
        }
    }
}

@Composable
private fun rememberQr(content: String): Bitmap? = remember(content) {
    runCatching { QRCodeGen.generateBitmap(content, QR_SIZE_PX, QR_SIZE_PX, 4) }.getOrNull()
}

private fun pushClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
    if (text.isNullOrEmpty()) {
        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
        return
    }
    PushReceiver.send(context, text)
}

private data class TransferProgress(val percent: Int, val name: String)

private fun TransferProgress?.describe(files: List<File>): String {
    if (this == null) return "等待网页上传"
    val ready = percent >= 100 && files.any { it.name == name }
    return when {
        percent in 1..99 && name.isNotEmpty() -> "正在上传 $name（$percent%）"
        ready -> "最近一次上传完成"
        percent >= 100 -> "正在确认 ${name.ifEmpty { "文件" }}"
        else -> "等待网页上传"
    }
}

private suspend fun fetchProgress(address: String): TransferProgress? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(address + "transfer/progress").openConnection() as HttpURLConnection
        try {
            val line = connection.inputStream.bufferedReader().use { it.readLine() }
            val json = JSONObject(line)
            TransferProgress(json.optInt("progress", 0), json.optString("name", ""))
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun listTransferFiles(): List<File> =
    RemoteServer.getTransferDirectory().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()

private fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "没有找到可以打开此文件的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun mimeType(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".apk") -> "application/vnd.android.package-archive"
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") -> "video/*"
        lower.endsWith(".mp3") || lower.endsWith(".flac") -> "audio/*"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") -> "image/*"
        else -> "*/*"
    }
}

private fun readable(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    else -> "${n / (1024 * 1024)} MB"
}
