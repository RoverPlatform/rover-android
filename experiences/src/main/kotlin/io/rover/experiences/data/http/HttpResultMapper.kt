package io.rover.experiences.data.http

import io.rover.core.data.APIException
import io.rover.core.data.NetworkError
import io.rover.core.data.NetworkResult
import io.rover.core.data.http.HttpClientResponse
import io.rover.experiences.logging.log
import org.json.JSONException
import java.io.IOException

internal class HttpResultMapper {
    fun <TEntity> mapResultWithBody(httpResponse: HttpClientResponse, decode: (String) -> TEntity): NetworkResult<TEntity> =
        when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> NetworkResult.Error(httpResponse.reason, true)
            is HttpClientResponse.ApplicationError -> {
                log.w("Given error reason: ${httpResponse.reportedReason}")
                // actually won't see any 200 codes here; already filtered about in the
                // HttpClient response mapping.
                NetworkResult.Error(NetworkError.InvalidStatusCode(httpResponse.responseCode, httpResponse.reportedReason), httpResponse.responseCode > 500)
            }
            is HttpClientResponse.Success -> {
                try {
                    val body = httpResponse.bufferedInputStream.use {
                        it.reader(Charsets.UTF_8).readText()
                    }

                    log.v("RESPONSE BODY: $body")
                    when (body) {
                        "" -> NetworkResult.Error(NetworkError.EmptyResponseData(), false)
                        else -> {
                            try {
                                NetworkResult.Success(
                                    decode(body)
                                )
                            } catch (e: APIException) {
                                log.w("API error: $e")
                                NetworkResult.Error<TEntity>(
                                        NetworkError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level error.
                                    false
                                )
                            } catch (e: JSONException) {
                                // because the traceback information has some utility for diagnosing
                                // JSON decode errors, even though we're treating them as soft
                                // errors, throw the traceback onto the console:
                                log.w("JSON decode problem details: $e, ${e.stackTrace.joinToString("\n")}")

                                NetworkResult.Error<TEntity>(
                                        NetworkError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level error.
                                    false
                                )
                            }
                        }
                    }
                } catch (exception: IOException) {
                    NetworkResult.Error<TEntity>(exception, true)
                }
            }
        }
}