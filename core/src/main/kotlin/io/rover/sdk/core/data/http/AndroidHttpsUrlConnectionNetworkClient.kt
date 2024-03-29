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

package io.rover.sdk.core.data.http

import android.content.Context
import android.content.pm.PackageInfo
import android.net.http.HttpResponseCache
import android.util.Log
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.setRoverUserAgent
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.subscribeOn
import org.reactivestreams.Publisher
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection

/**
 * An implementation of [NetworkClient] powered by Android's stock [HttpsURLConnection].
 */
class AndroidHttpsUrlConnectionNetworkClient(
    private val ioScheduler: Scheduler,
    private val appPackageInfo: PackageInfo
) : NetworkClient {

    override fun request(
        request: HttpRequest,
        bodyData: String?
    ): Publisher<HttpClientResponse> {
        return request(request, bodyData, true)
    }

    fun request(
        request: HttpRequest,
        bodyData: String?,
        gzip: Boolean
    ): Publisher<HttpClientResponse> {
        // synchronous API.

        // Using the Android HttpsUrlConnection HTTP client inherited from Java, which
        // has a synchronous API.

        return Publishers.create<HttpClientResponse> { subscriber ->
            this@AndroidHttpsUrlConnectionNetworkClient.log.d("Starting request: $request")
            val connection = request.url
                .openConnection() as HttpURLConnection

            if (!connection.useCaches || HttpResponseCache.getInstalled() == null) {
                // We expect the user to agree to and implement setting up a global
                // HttpUrlConnection cache. While we would much prefer to maintain our own,
                // however, the global-side-effect nature of the Android HttpClient API means
                // that we won't be able to achieve that without just building our own cache
                // regime rather than using the stock Android cache, so we'll stick with this
                // approach for now.
                //
                // This is made even more unfortunate because it would have been to our
                // advantage to set up parallel NetworkClients with different caches in order to
                // cache different request payloads separately: when the goal is to save
                // perceptible delay for users small JSON payloads are more valuable to cache
                // byte-for-byte compared with large bulky asset (say, images) payloads. Leaving
                // them in the same LRU cache pool will mean that rotating through just a few
                // large photos will cause the small payloads to be evicted even though their
                // contribution to consumption of the cache is tiny.
                emitMissingCacheWarning()
            }

            val requestBody = if (gzip) bodyData?.toByteArray(Charsets.UTF_8)?.asGzip() else bodyData?.toByteArray(Charsets.UTF_8)
            val requestHasBody = when (request.verb) {
                HttpVerb.POST, HttpVerb.PUT -> requestBody != null
                else -> false
            }

            connection
                .apply {
                    // TODO: set read and connect timeouts.
                    // TODO: set a nice user agent.
                    if (requestHasBody) {
                        setFixedLengthStreamingMode(requestBody?.size ?: 0)
                        if (gzip) { setRequestProperty("Content-Encoding", "gzip") }
                    }

                    setRoverUserAgent(appPackageInfo)

                    // add the request headers.
                    request.headers.onEach { (field, value) -> setRequestProperty(field, value) }

                    doOutput = requestHasBody
                    requestMethod = request.verb.wireFormat
                }

            // synchronously write the body to the connection.
            if (requestHasBody) try {
                connection.outputStream.use { stream ->
                    DataOutputStream(stream).use { dataOutputStream ->
                        dataOutputStream.write(requestBody)
                    }
                }
            } catch (e: IOException) {
                subscriber.onNext(
                    HttpClientResponse.ConnectionFailure(
                        e
                    )
                )
                return@create
            }

            // ensure the connection is up!
            val responseCode = try {
                connection.responseCode
            } catch (e: IOException) {
                subscriber.onNext(
                    HttpClientResponse.ConnectionFailure(
                        e
                    )
                )
                return@create
            }

            this@AndroidHttpsUrlConnectionNetworkClient.log.d("$request : $responseCode")

            val result = when (responseCode) {
                // success codes, and also handle 304 cached handling treat body as normal
                // because the cache set up below is handling it.
                in 200..299, 304 -> {
                    try {
                        HttpClientResponse.Success(
                            BufferedInputStream(
                                connection.inputStream
                            )
                        )
                    } catch (e: IOException) {
                        HttpClientResponse.ConnectionFailure(
                            e
                        )
                    }
                }
                else -> {
                    // note: we don't support handling redirects as anything other than an
                    // onError for now.
                    try {
                        // just greedily load the entire error stream.
                        connection.errorStream.use { errorStream ->
                            errorStream.use {
                                val stream = BufferedInputStream(
                                    it
                                ).reader(Charsets.UTF_8)
                                val result = HttpClientResponse.ApplicationError(
                                    responseCode,
                                    stream.use { it.readText() }
                                )

                                result
                            }
                        }
                    } catch (e: IOException) {
                        HttpClientResponse.ConnectionFailure(
                            e
                        )
                    }
                }
            }
            // TODO: detect if this was a cache hit and log as such.
            // completionHandler(result)
            this@AndroidHttpsUrlConnectionNetworkClient.log.v(
                "Cache hit count currently is: ${HttpResponseCache.getInstalled()?.hitCount}"
            )

            subscriber.onNext(result)
            subscriber.onComplete()
        }.subscribeOn(ioScheduler)
    }

    companion object {
        /**
         * Sets up a *global* HTTP cache for any users of HttpUrlConnection in your app,
         * including Rover.
         */
        @JvmStatic
        fun installSaneGlobalHttpCache(context: Context) {
            val httpCacheDir = File(context.cacheDir, "http")
            val httpCacheSize = (50 * 1024 * 1024).toLong() // 50 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize)
            Log.v("NetworkClient", "Global HttpUrlConnection cache installed.")
        }

        @JvmStatic
        fun emitMissingCacheWarning() {
            log.e(
                "An HTTPUrlConnection cache is not enabled.\n" +
                    "Please see the Rover documentation for Installation and Initialization of the Rover SDK: https://developer.rover.io/v2/android/\n" +
                    "Ensure you are calling Rover.installSaneGlobalHttpCache() before Rover.initialize().\n" +
                    "Currently installed cache appears to be: ${HttpResponseCache.getInstalled()}"
            )
        }
    }

    private fun ByteArray.asGzip(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val gzipStream = GZIPOutputStream(outputStream)
        gzipStream.write(this)
        gzipStream.finish()

        return outputStream.toByteArray().apply {
            outputStream.close()
            gzipStream.close()
        }
    }
}
