package io.rover.sdk.data.http

import io.rover.sdk.data.graphql.APIException
import io.rover.sdk.data.graphql.ApiError
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.logging.log
import org.json.JSONException
import java.io.IOException

internal class HttpResultMapper {
    fun <TEntity> mapResultWithBody(httpResponse: HttpClientResponse, decode: (String) -> TEntity): ApiResult<TEntity> =
        when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> ApiResult.Error(httpResponse.reason, true)
            is HttpClientResponse.ApplicationError -> {
                log.w("Given error reason: ${httpResponse.reportedReason}")
                // actually won't see any 200 codes here; already filtered about in the
                // HttpClient response mapping.
                ApiResult.Error(ApiError.InvalidStatusCode(httpResponse.responseCode, httpResponse.reportedReason), httpResponse.responseCode > 500)
            }
            is HttpClientResponse.Success -> {
                try {
                    val body = httpResponse.bufferedInputStream.use {
                        it.reader(Charsets.UTF_8).readText()
                    }

                    log.v("RESPONSE BODY: $body")
                    when (body) {
                        "" -> ApiResult.Error(ApiError.EmptyResponseData(), false)
                        else -> {
                            try {
                                ApiResult.Success(
                                    decode(body)
                                )
                            } catch (e: APIException) {
                                log.w("API error: $e")
                                ApiResult.Error<TEntity>(
                                    ApiError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level
                                    // error from the GraphQL API.
                                    false
                                )
                            } catch (e: JSONException) {
                                // because the traceback information has some utility for diagnosing
                                // JSON decode errors, even though we're treating them as soft
                                // errors, throw the traceback onto the console:
                                log.w("JSON decode problem details: $e, ${e.stackTrace.joinToString("\n")}")

                                ApiResult.Error<TEntity>(
                                    ApiError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level
                                    // error from the GraphQL API.
                                    false
                                )
                            }
                        }
                    }
                } catch (exception: IOException) {
                    ApiResult.Error<TEntity>(exception, true)
                }
            }
        }
}