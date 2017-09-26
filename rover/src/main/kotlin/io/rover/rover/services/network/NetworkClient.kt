package io.rover.rover.services.network

import android.os.AsyncTask
import io.rover.rover.core.logging.log
import java.io.BufferedInputStream
import java.io.DataOutputStream
import javax.net.ssl.HttpsURLConnection

/**
 * An implementation of [NetworkClient] powered by Android's stock [HttpsURLConnection] and [AsyncTask].
 */
class AsyncTaskAndHttpUrlConnectionNetworkClient: NetworkClient {
    override fun networkTask(request: HttpRequest, bodyData: String?, completionHandler: (HttpClientResponse) -> Unit): NetworkTask {
        val asyncTask = object : AsyncTask<Void, Void, HttpClientResponse>() {
            override fun doInBackground(vararg params: Void?): HttpClientResponse {
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

                // synchronously write the body to the connection.
                DataOutputStream(connection.outputStream).write(requestBody)

                val responseCode = connection.responseCode

                log.d("POST $request : $responseCode")

                return when (responseCode) {
                    in 200..299 -> {
                        HttpClientResponse.Success(
                            BufferedInputStream(
                                connection.inputStream
                            )
                        )
                    }
                    else -> {
                        // we don't support handling redirects as anything other than an onError for now.
                        HttpClientResponse.ApplicationError(
                            responseCode,
                            BufferedInputStream(
                                connection.errorStream
                            ).reader(Charsets.UTF_8).readText()
                        )
                    }
                }
            }

            override fun onPostExecute(result: HttpClientResponse) {
                super.onPostExecute(result)
                completionHandler(result)
            }
        }

        return AsyncTaskNetworkTask(asyncTask)
    }
}
