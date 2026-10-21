package com.boimin.beatradar.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A request the server answered with an error body (`{"error":{"code","message"}}`). [http] 0 = no answer. */
class ApiException(val http: Int, val code: String, override val message: String) : IOException(message) {
    val offline: Boolean get() = http == 0
}

/** Gives the bearer token for each request and deals with a 401 (refresh, re-register or sign out). */
interface TokenSource {
    suspend fun token(): String?

    /** The server refused [token]. Returns true when a new one is ready and the request should be retried once. */
    suspend fun onUnauthorized(token: String): Boolean
}

/**
 * Small JSON client on OkHttp. Paths are relative to [baseUrl] (e.g. "v1/songs"). Without network, calls throw
 * [ApiException] with http 0 and code "offline", so callers have one error type to handle.
 */
class ApiClient(
    baseUrl: String,
    private val tokens: TokenSource? = null,
    private val http: OkHttpClient = defaultHttp(),
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    suspend fun <T> call(
        method: String,
        path: String,
        response: KSerializer<T>,
        body: String? = null,
        query: Map<String, Any?> = emptyMap(),
        auth: Boolean = true,
        timeoutMs: Long? = null,
    ): T {
        val (_, raw) = send(method, path, body?.toRequestBody(JSON), emptyMap(), query, auth, timeoutMs, retried = false)
        // 202/204 answers may have no body; Unit (and JsonObject) read "{}".
        return ApiJson.decodeFromString(response, raw.ifBlank { "{}" })
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap(), auth: Boolean = true): T =
        call("GET", path, serializer(), query = query, auth = auth)

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true, timeoutMs: Long? = null): T =
        call("POST", path, serializer(), ApiJson.encodeToString(serializer<B>(), body), auth = auth, timeoutMs = timeoutMs)

    suspend inline fun <reified T> postEmpty(path: String, auth: Boolean = true): T =
        call("POST", path, serializer(), "{}", auth = auth)

    suspend inline fun <reified B, reified T> patch(path: String, body: B): T =
        call("PATCH", path, serializer(), ApiJson.encodeToString(serializer<B>(), body))

    suspend inline fun <reified T> put(path: String): T = call("PUT", path, serializer(), "{}")

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        call("PUT", path, serializer(), ApiJson.encodeToString(serializer<B>(), body))

    suspend inline fun <reified T> delete(path: String): T = call("DELETE", path, serializer())

    /** PUTs raw bytes (e.g. an audio clip) and returns the HTTP status (2xx); errors throw [ApiException]. */
    suspend fun putBytes(path: String, bytes: ByteArray, contentType: String, headers: Map<String, String> = emptyMap()): Int =
        send("PUT", path, bytes.toRequestBody(contentType.toMediaType()), headers, emptyMap(), auth = true, timeoutMs = 60_000, retried = false).first

    private suspend fun send(
        method: String,
        path: String,
        body: RequestBody?,
        headers: Map<String, String>,
        query: Map<String, Any?>,
        auth: Boolean,
        timeoutMs: Long?,
        retried: Boolean,
    ): Pair<Int, String> {
        val url = base.newBuilder().addPathSegments(path).apply {
            for ((k, v) in query) if (v != null) addQueryParameter(k, v.toString())
        }.build()
        val token = if (auth) tokens?.token() else null
        val request = Request.Builder().url(url).apply {
            if (token != null) header("Authorization", "Bearer $token")
            for ((k, v) in headers) header(k, v)
            method(method, body)
        }.build()
        val client = if (timeoutMs != null) http.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build() else http
        val (code, text) = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { it.code to (it.body.string()) }
            } catch (e: IOException) {
                throw ApiException(0, "offline", e.message ?: "No connection")
            }
        }
        if (code in 200..299) return code to text
        if (code == 401 && token != null && !retried && tokens?.onUnauthorized(token) == true) {
            return send(method, path, body, headers, query, auth, timeoutMs, retried = true)
        }
        throw parseError(code, text)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        internal fun parseError(code: Int, text: String): ApiException {
            val detail = runCatching { ApiJson.decodeFromString(ApiErrorBody.serializer(), text).error }.getOrNull()
            return ApiException(code, detail?.code ?: "http_$code", detail?.message?.takeIf { it.isNotBlank() } ?: "Request failed ($code)")
        }
    }
}
