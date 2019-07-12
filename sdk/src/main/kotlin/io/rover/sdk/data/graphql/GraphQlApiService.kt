package io.rover.sdk.data.graphql

import android.net.Uri
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.streams.map
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.operations.FetchExperienceRequest
import org.json.JSONException
import org.reactivestreams.Publisher
import java.io.IOException
import java.net.URL

/**
 * Responsible for providing access the Rover cloud API, powered by GraphQL.
 */
internal class GraphQlApiService(
    private val endpoint: URL,
    private val accountToken: String?,
    private val httpClient: HttpClient,
    private val httpResultMapper: HttpResultMapper = HttpResultMapper()
) {
    private fun urlRequest(mutation: Boolean, queryParams: Map<String, String>): HttpRequest {
        val uri = Uri.parse(endpoint.toString())
        val builder = uri.buildUpon()
        queryParams.forEach { (k, v) -> builder.appendQueryParameter(k, v) }

        return HttpRequest(
            URL(builder.toString()),
            hashMapOf<String, String>().apply {
                if (mutation) {
                    this["Content-Type"] = "application/json"
                }

                when {
                    accountToken != null -> this["x-rover-account-token"] = accountToken
                }

                this.entries.forEach { (key, value) ->
                    this[key] = value
                }
            },
            if (mutation) {
                HttpVerb.POST
            } else {
                HttpVerb.GET
            }
        )
    }

    /**
     * Performs the given [GraphQlRequest] when subscribed and yields the result to the subscriber.
     */
    fun <TEntity> operation(request: GraphQlRequest<TEntity>): Publisher<ApiResult<TEntity>> {
        // TODO: once we change urlRequest() to use query parameters and GET for non-mutation
        // requests, replace true `below` with `request.mutation`.
        val urlRequest = urlRequest(request.mutation, request.encodeQueryParameters())
        val bodyData = request.encodeBody()

        log.v("going to make network request $urlRequest")

        return httpClient.request(urlRequest, bodyData).map { httpClientResponse ->
            httpResultMapper.mapResultWithBody(httpClientResponse) {
                request.decode(it)
            }
        }
    }

    /**
     * Retrieves the experience when subscribed and yields it to the subscriber.
     */
    fun fetchExperience(
        query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<ApiResult<Experience>> {
        return this.operation(
            FetchExperienceRequest(query)
        )
    }
}

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
