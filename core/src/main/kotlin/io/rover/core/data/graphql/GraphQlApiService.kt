package io.rover.core.data.graphql

import android.net.Uri
import io.rover.core.data.APIException
import io.rover.core.data.AuthenticationContext
import io.rover.core.data.GraphQlRequest
import io.rover.core.data.NetworkError
import io.rover.core.data.NetworkResult
import io.rover.core.data.domain.EventSnapshot
import io.rover.core.data.domain.Experience
import io.rover.core.data.graphql.operations.FetchExperienceRequest
import io.rover.core.data.graphql.operations.SendEventsRequest
import io.rover.core.data.http.HttpClientResponse
import io.rover.core.data.http.HttpRequest
import io.rover.core.data.http.HttpVerb
import io.rover.core.data.http.NetworkClient
import io.rover.core.logging.log
import io.rover.core.streams.Publishers
import io.rover.core.streams.map
import io.rover.core.platform.DateFormattingInterface
import org.json.JSONException
import org.reactivestreams.Publisher
import java.io.IOException
import java.net.URL

/**
 * Responsible for providing access the Rover cloud API, powered by GraphQL.
 *
 * If you would like to override or augment any of the behaviour here, you may override it in
 * [DataPlugin].
 */
class GraphQlApiService(
    private val endpoint: URL,
    private val authenticationContext: AuthenticationContext,
    private val networkClient: NetworkClient,
    private val dateFormatting: DateFormattingInterface
): GraphQlApiServiceInterface {
    private fun urlRequest(mutation: Boolean, queryParams: Map<String, String>): HttpRequest {
        val uri = Uri.parse(endpoint.toString())
        val builder = uri.buildUpon()
        queryParams.forEach { k, v -> builder.appendQueryParameter(k, v) }

        return HttpRequest(
            URL(builder.toString()),
            hashMapOf<String, String>().apply {
                if(mutation) {
                    this["Content-Type"] = "application/json"
                }

                when {
                    authenticationContext.sdkToken != null -> this["x-rover-account-token"] = authenticationContext.sdkToken!!
                    authenticationContext.bearerToken != null -> this["authorization"] = "Bearer ${authenticationContext.bearerToken}"
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

                    log.v("RESPONSE BODY: $body")
                    when (body) {
                        "" -> NetworkResult.Error(NetworkError.EmptyResponseData(), false)
                        else -> {
                            try {
                                NetworkResult.Success(
                                    // TODO This could emit, say, a JSON decode exception!  Need a story.
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
        val urlRequest = urlRequest(request.mutation, request.encodeQueryParameters())
        val bodyData = request.encodeBody()

        log.v("going to make network request $urlRequest")

        return if(authenticationContext.isAvailable()) {
            networkClient.request(urlRequest, bodyData).map { httpClientResponse ->
                httpResult(request, httpClientResponse)
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

    override fun fetchExperience(query: FetchExperienceRequest.ExperienceQueryIdentifier): Publisher<NetworkResult<Experience>> {
        return operation(
            FetchExperienceRequest(query)
        )
    }

    override fun submitEvents(events: List<EventSnapshot>): Publisher<NetworkResult<String>> {
        return if(!authenticationContext.isAvailable()) {
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
}
