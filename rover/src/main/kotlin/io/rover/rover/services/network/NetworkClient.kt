package io.rover.rover.services.network

import android.annotation.SuppressLint
import android.os.AsyncTask
import io.rover.rover.core.logging.log
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * An implementation of [NetworkClient] powered by Android's stock [HttpsURLConnection] and [AsyncTask].
 */
class AsyncTaskAndHttpUrlConnectionNetworkClient: NetworkClient {

    private var interceptor: AsyncTaskAndHttpUrlConnectionInterceptor? = null

    fun registerInterceptor(newInterceptor: AsyncTaskAndHttpUrlConnectionInterceptor?) {
        interceptor = newInterceptor
    }

    override fun networkTask(request: HttpRequest, bodyData: String?, completionHandler: (HttpClientResponse) -> Unit): NetworkTask {
        // @SuppressLint turned on because this does not have any Android contexts, activities, or
        // anything else in scope.
        val asyncTask = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?) {
                log.d("POST $request")
                val connection = request.url
                    .openConnection() as HttpsURLConnection

                val requestBody = bodyData?.toByteArray(Charsets.UTF_8)

                connection
                    .apply {
                        // TODO: set read and connect timeouts.
                        // TODO: set a nice user agent.
                        setFixedLengthStreamingMode(requestBody?.size ?: 0)

                        // add the request headers.
                        request.headers.onEach { (field, value) -> setRequestProperty(field, value) }

                        // sets HttpUrlConnection to use POST.
                        doOutput = true
                        requestMethod = "POST"
                    }

                val intercepted = interceptor?.onOpened(connection, request.url.path, requestBody ?: kotlin.ByteArray(0))

                // synchronously write the body to the connection.
                try {
                    DataOutputStream(connection.outputStream).write(requestBody)
                } catch (e: IOException) {
                    intercepted?.onError(e)
                    completionHandler(HttpClientResponse.ConnectionFailure(
                        e
                    ))
                    return
                }

                // ensure the connection is up!
                try {
                    connection.connect()
                } catch (e: IOException) {
                    intercepted?.onError(e)
                    completionHandler(HttpClientResponse.ConnectionFailure(
                        e
                    ))
                    return
                }
                intercepted?.onConnected()

                val responseCode = connection.responseCode

                log.d("POST $request : $responseCode")

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
                completionHandler(result)
            }
        }

        return AsyncTaskNetworkTask(asyncTask)
    }
}

interface AsyncTaskAndHttpUrlConnectionInterception {
    fun onConnected() { }

    fun onError(exception: IOException) { }

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