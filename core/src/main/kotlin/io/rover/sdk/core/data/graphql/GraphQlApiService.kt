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

package io.rover.sdk.core.data.graphql

import android.net.Uri
import io.rover.sdk.core.data.APIException
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.NetworkError
import io.rover.sdk.core.data.NetworkResult
import io.rover.sdk.core.data.authenticateRequest
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.operations.FetchExperienceRequest
import io.rover.sdk.core.data.graphql.operations.SendEventsRequest
import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.data.http.HttpRequest
import io.rover.sdk.core.data.http.HttpVerb
import io.rover.sdk.core.data.http.NetworkClient
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.core.streams.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.publish
import org.json.JSONException
import org.reactivestreams.Publisher
import java.io.IOException
import java.net.URL

/**
 * Responsible for providing access the Rover cloud API, powered by GraphQL.
 */
class GraphQlApiService(
    private val endpoint: URL,
    private val authenticationContext: AuthenticationContextInterface,
    private val networkClient: NetworkClient,
    private val dateFormatting: DateFormattingInterface
) : GraphQlApiServiceInterface {
    private suspend fun urlRequest(mutation: Boolean, queryParams: Map<String, String>): HttpRequest {
        val uri = Uri.parse(endpoint.toString())
        val builder = uri.buildUpon()
        queryParams.forEach { (k, v) -> builder.appendQueryParameter(k, v) }

        val request = HttpRequest(
            URL(builder.toString()),
            hashMapOf<String, String>().apply {
                if (mutation) {
                    this["Content-Type"] = "application/json"
                }

                if (authenticationContext.sdkToken != null) {
                    this["x-rover-account-token"] = authenticationContext.sdkToken!!
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

        return authenticationContext.authenticateRequest(request)
    }

    private fun <TEntity> httpResult(httpRequest: GraphQlRequest<TEntity>, httpResponse: HttpClientResponse): NetworkResult<TEntity> =
        when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> NetworkResult.Error(httpResponse.reason, true)
            is HttpClientResponse.ApplicationError -> {
                log.w("Given GraphQL error reason: ${httpResponse.reportedReason}")
                NetworkResult.Error(
                    NetworkError.InvalidStatusCode(httpResponse.responseCode, httpResponse.reportedReason),
                    when {
                        // actually won't see any 200 codes here; already filtered about in the
                        // HttpClient response mapping.
                        httpResponse.responseCode < 300 -> false
                        // 3xx redirects
                        httpResponse.responseCode < 400 -> false
                        // 4xx request errors (we don't want to retry these; onus is likely on
                        // request creator).
                        httpResponse.responseCode < 500 -> false
                        // 5xx - any transient errors from the backend.
                        else -> true
                    }
                )
            }
            is HttpClientResponse.Success -> {
                try {
                    val body = httpResponse.bufferedInputStream.use {
                        it.reader(Charsets.UTF_8).readText()
                    }

                    when (body) {
                        "" -> NetworkResult.Error(NetworkError.EmptyResponseData(), false)
                        else -> {
                            try {
                                NetworkResult.Success(
                                    httpRequest.decode(body)
                                )
                            } catch (e: APIException) {
                                log.w("API error: $e")
                                NetworkResult.Error<TEntity>(
                                    NetworkError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level
                                    // error from the GraphQL API.
                                    false
                                )
                            } catch (e: JSONException) {
                                // because the traceback information has some utility for diagnosing
                                // JSON decode errors, even though we're treating them as soft
                                // errors, throw the traceback onto the console:
                                log.w("JSON decode problem details: $e, ${e.stackTrace.joinToString("\n")}")

                                NetworkResult.Error<TEntity>(
                                    NetworkError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level
                                    // error from the GraphQL API.
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

    override fun <TEntity> operation(request: GraphQlRequest<TEntity>): Publisher<NetworkResult<TEntity>> {
        // TODO: once we change urlRequest() to use query parameters and GET for non-mutation
        // requests, replace true `below` with `request.mutation`.
        return if (authenticationContext.isAvailable()) {
            // wrap a coroutine into a reactive streams publisher
            return publish<HttpRequest>(Dispatchers.Main) {
                send(urlRequest(request.mutation, request.encodeQueryParameters()))
            }.flatMap { urlRequest ->
                val bodyData = request.encodeBody()
                networkClient.request(urlRequest, bodyData, gzip = true).map { httpClientResponse ->
                    httpResult(request, httpClientResponse)
                }
            }
        } else {
            Publishers.just(
                NetworkResult.Error(
                    Throwable("Rover API authentication not available."),
                    true
                )
            )
        }
    }

    override fun submitEvents(events: List<EventSnapshot>): Publisher<NetworkResult<String>> {
        return if (!authenticationContext.isAvailable()) {
            log.w("Events may not be submitted without a Rover authentication context being configured.")
            Publishers.just(
                NetworkResult.Error(
                    Exception("Attempt to submit Events without Rover authentication context being configured."),
                    false
                )
            )
        } else {
            operation(
                SendEventsRequest(
                    dateFormatting,
                    events
                )
            )
        }
    }

    /**
     * Retrieves the experience when subscribed and yields it to the subscriber.
     */
    override fun fetchExperience(
        query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<NetworkResult<ClassicExperienceModel>> {
        return this.operation(
            FetchExperienceRequest(query)
        )
    }
}
