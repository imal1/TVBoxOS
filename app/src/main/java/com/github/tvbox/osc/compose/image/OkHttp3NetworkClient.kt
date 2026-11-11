package com.github.tvbox.osc.compose.image

import coil3.network.NetworkClient
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 让 Coil 复用应用现有的 OkHttp 3 客户端（含 DoH、代理等配置）。
 *
 * 官方的 coil-network-okhttp 依赖 OkHttp 4，引入后会把整个应用的 OkHttp 3.12 一并升级，
 * 影响 okgo 等依赖 OkHttp 3 的组件，因此这里自行适配。
 */
class OkHttp3NetworkClient(
    private val client: () -> OkHttpClient,
) : NetworkClient {

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val response = client().newCall(request.toOkHttpRequest()).await()
        return response.use { block(it.toNetworkResponse()) }
    }

    private suspend fun NetworkRequest.toOkHttpRequest(): Request {
        val builder = Request.Builder().url(url)
        headers.asMap().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        val requestBody = body?.let { networkBody ->
            val buffer = Buffer()
            networkBody.writeTo(buffer)
            RequestBody.create(null, buffer.readByteArray())
        }
        builder.method(method, requestBody)
        return builder.build()
    }

    private fun Response.toNetworkResponse(): NetworkResponse {
        val headersBuilder = NetworkHeaders.Builder()
        val okHeaders = headers()
        for (name in okHeaders.names()) {
            headersBuilder.set(name, okHeaders.values(name))
        }
        return NetworkResponse(
            code = code(),
            requestMillis = sentRequestAtMillis(),
            responseMillis = receivedResponseAtMillis(),
            headers = headersBuilder.build(),
            body = body()?.source()?.let(::NetworkResponseBody),
            delegate = this,
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}
