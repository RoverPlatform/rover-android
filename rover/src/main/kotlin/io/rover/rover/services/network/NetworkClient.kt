package io.rover.rover.services.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.HttpResponseCache
import android.os.AsyncTask
import io.rover.rover.core.logging.log
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * An implementation of [NetworkClient] powered by Android's stock [HttpsURLConnection] and [AsyncTask].
 */
class AsyncTaskAndHttpUrlConnectionNetworkClient : NetworkClient {

    private var interceptor: AsyncTaskAndHttpUrlConnectionInterceptor? = null

    /**
     * Add an interceptor
     */
    fun registerInterceptor(newInterceptor: AsyncTaskAndHttpUrlConnectionInterceptor?) {
        interceptor = newInterceptor
    }

    override fun networkTask(request: HttpRequest, bodyData: String?, completionHandler: (HttpClientResponse) -> Unit): NetworkTask {
        // @SuppressLint turned on because this does not have any Android contexts, activities, or
        // anything else in scope.
        val asyncTask = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?) {
                this@AsyncTaskAndHttpUrlConnectionNetworkClient.log.d("Starting request: $request")
                val connection = request.url
                    .openConnection() as HttpsURLConnection

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

                    // TODO: exception message should refer to a façade method once we have one
                    throw RuntimeException("An HTTPUrlConnection cache is not enabled.\n" +
                        "Please see the Rover documentation and the Google documentation: https://developer.android.com/reference/android/net/http/HttpResponseCache.html\n" +
                        "As a quick fix you may call io.rover.rover.network.AsyncTaskAndHttpUrlConnectionNetworkClient.installSaneGlobalHttpCacheCache()")
                }

                val requestBody = bodyData?.toByteArray(Charsets.UTF_8)
                val requestHasBody = when (request.verb) {
                    HttpVerb.POST, HttpVerb.PUT -> requestBody != null
                    else -> false
                }

                connection
                    .apply {
                        // TODO: set read and connect timeouts.
                        // TODO: set a nice user agent.
                        setFixedLengthStreamingMode(requestBody?.size ?: 0)

                        // add the request headers.
                        request.headers.onEach { (field, value) -> setRequestProperty(field, value) }

                        doOutput = requestHasBody
                        requestMethod = request.verb.wireFormat
                    }

                val intercepted = interceptor?.onOpened(connection, request.url.path, requestBody ?: kotlin.ByteArray(0))

                // synchronously write the body to the connection.
                if (requestHasBody) try {
                    DataOutputStream(connection.outputStream).write(requestBody)
                } catch (e: IOException) {
                    intercepted?.onError(e)
                    completionHandler(HttpClientResponse.ConnectionFailure(
                        e
                    ))
                    return
                }

                // ensure the connection is up!
                val responseCode = try {
                    connection.connect()

                    intercepted?.onConnected()
                    connection.responseCode
                } catch (e: IOException) {
                    intercepted?.onError(e)
                    completionHandler(HttpClientResponse.ConnectionFailure(
                        e
                    ))
                    return
                }

                this@AsyncTaskAndHttpUrlConnectionNetworkClient.log.d("$request : $responseCode")

                val result = when (responseCode) {
                    in 200..299 -> {
                        try {
                            HttpClientResponse.Success(
                                BufferedInputStream(
                                    intercepted?.sniffStream(connection.inputStream) ?: connection.inputStream
                                )
                            )
                        } catch (e: IOException) {
                            HttpClientResponse.ConnectionFailure(
                                e
                            )
                        }
                    }
                    else -> {
                        // we don't support handling redirects as anything other than an onError for now.
                        try {
                            HttpClientResponse.ApplicationError(
                                responseCode,
                                BufferedInputStream(
                                    intercepted?.sniffStream(connection.errorStream) ?: connection.errorStream
                                ).reader(Charsets.UTF_8).readText()
                            )
                        } catch (e: IOException) {
                            HttpClientResponse.ConnectionFailure(
                                e
                            )
                        }
                    }
                }
                // TODO: detect if this was a cache hit and log as such.
                completionHandler(result)
                this@AsyncTaskAndHttpUrlConnectionNetworkClient.log.v("Cache hit count currently is: ${HttpResponseCache.getInstalled().hitCount}")
            }
        }

        return AsyncTaskNetworkTask(asyncTask)
    }

    companion object {
        /**
         * Sets up a *global* HTTP cache for any users of HttpUrlConnection in your app,
         * including Rover.
         */
        @JvmStatic
        fun installSaneGlobalHttpCacheCache(context: Context) {
            val httpCacheDir = File(context.cacheDir, "http")
            val httpCacheSize = (50 * 1024 * 1024).toLong() // 50 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize)
            log.v("Global HttpUrlConnection cache installed.")
        }
    }
}

interface AsyncTaskAndHttpUrlConnectionInterception {
    fun onConnected() {}

    fun onError(exception: IOException) {}

    fun sniffStream(source: InputStream): InputStream = source
}

/**
 * Rover uses [HttpsURLConnection] internally to avoid dependencies on any third party HTTP client
 * libraries.  If client code would like to sniff/intercept the connections (perhaps to power
 * a Stetho HTTP interceptor, for instance), they may provide a single interceptor.
 */
interface AsyncTaskAndHttpUrlConnectionInterceptor {
    fun onOpened(httpUrlConnection: HttpURLConnection, requestPath: String, body: ByteArray): AsyncTaskAndHttpUrlConnectionInterception
}