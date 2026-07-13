/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.appscreens.network

import android.content.Context
import android.net.Uri
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.roverUserAgent
import io.rover.sdk.experiences.appscreens.AppScreenDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The always-anonymous HTTP client for the App Screen document channel.
 *
 * This client mirrors [io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient]'s
 * anonymity — the only header it adds is `User-Agent`. It never carries `Authorization`,
 * `x-rover-account-token`, or any device/user identifier, because the document channel is shared
 * and cacheable for all identities.
 *
 * Unlike the data client, it is fitted with a disk [Cache] so `Cache-Control`/`ETag` directives
 * from the server are honoured natively by OkHttp: a document fresh within its `max-age` is served
 * without touching the network; once stale, OkHttp issues a conditional `If-None-Match` request and
 * transparently handles the `304 Not Modified` reply, freshening the stored response headers (so
 * the `x-rover-app-screen-data-scope` header remains readable off a cache-served response).
 */
internal open class AppScreensDocumentClient(
    private val context: Context
) {
    /**
     * How aggressively to revalidate a cached document.
     */
    enum class DocumentCachePolicy {
        /** Honour the stored freshness lifetime; only hit the network once stale. */
        Default,

        /**
         * Force a conditional revalidation (`If-None-Match`) even while the cached entry is fresh,
         * without discarding it. Used by the hash-handshake refetch.
         */
        ForceRevalidate
    }

    private val userAgent: String by lazy {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.roverUserAgent
    }

    internal val client: OkHttpClient by lazy {
        OkHttpClient()
            .newBuilder()
            .cache(Cache(File(context.cacheDir, CACHE_DIR_NAME), CACHE_SIZE_BYTES))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            .build()
    }

    /**
     * Fetch the HTML document at [url]. Throws [IOException] on transport failure or a non-2xx
     * response (a `304` served from cache is surfaced by OkHttp as a synthesized 200 with the
     * cached body, so it is never seen here as an error).
     */
    open suspend fun fetchDocument(
        url: Uri,
        policy: DocumentCachePolicy = DocumentCachePolicy.Default
    ): AppScreenDocument = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url.toString()).get()
        if (policy == DocumentCachePolicy.ForceRevalidate) {
            // Force an If-None-Match revalidation while keeping (and re-serving) the cached entry on
            // a 304. A request `max-age=0` marks the stored response immediately stale, so OkHttp
            // issues a conditional GET and merges the 304 with the cache. (A request `no-cache`
            // directive would instead be network-only and would NOT merge the 304 — it would leak a
            // raw 304 — so it is deliberately not used here despite being the naive choice.)
            requestBuilder.cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("App Screen document fetch failed: HTTP ${response.code} for $url")
            }
            val html = response.body?.string()
                ?: throw IOException("App Screen document response had no body for $url")

            val etag = response.header("ETag")
            val scope = AppScreenDataScope.fromHeader(response.header(AppScreenDataScope.HEADER_NAME))

            log.i(
                "App Screen document loaded: $url etag=$etag scope=$scope " +
                    "(network=${response.networkResponse?.code}, cache=${response.cacheResponse != null})"
            )

            AppScreenDocument(html = html, etag = etag, dataScope = scope)
        }
    }

    companion object {
        private const val CACHE_DIR_NAME = "rover-appscreens-http"
        private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024
    }
}
