package io.rover.sdk.data

import io.rover.sdk.data.graphql.ApiError
import io.rover.sdk.data.graphql.ApiResultWithoutResponseBody
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.subscribe
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.net.URL

/**
 * Responsible for dispatching Analytics events.
 */

open class EventAnalyticsService(
    private val endpoint: URL,
    private val accountToken: String?,
    private val httpClient: HttpClient,
    private val eventEmitter: EventEmitter
) {
    private fun buildRequest(endpoint: URL, accountToken: String?): HttpRequest {
        val headersMap = hashMapOf("Content-Type" to "application/json").apply {
            if (accountToken != null) put("x-rover-account-token", accountToken)
        }

        return HttpRequest(endpoint, headersMap, HttpVerb.POST)
    }

    private fun encodeBody(eventInformation: EventEmitter.Event): String {
        return JSONObject().apply {
            put("event", eventInformation.action)
            put("timestamp", "")
            put("properties", eventInformation.attributes)
        }.toString()
    }

    private fun onResponse(httpResponse: HttpClientResponse): ApiResultWithoutResponseBody {
        return when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> ApiResultWithoutResponseBody.Error(httpResponse.reason)
            is HttpClientResponse.ApplicationError -> {
                log.w("Given EventAnalytics error reason: ${httpResponse.reportedReason}")
                ApiResultWithoutResponseBody.Error(ApiError.InvalidStatusCode(httpResponse.responseCode, httpResponse.reportedReason))
            }
            is HttpClientResponse.Success -> { ApiResultWithoutResponseBody.Success }
        }
    }

    private fun sendRequest(eventInformation: EventEmitter.Event): Publisher<ApiResultWithoutResponseBody> {
        val urlRequest = buildRequest(endpoint, accountToken)
        val bodyData = encodeBody(eventInformation)

        log.v("going to make events network request $urlRequest")

        return httpClient.request(urlRequest, bodyData).map { httpClientResponse -> onResponse(httpClientResponse) }
    }

    open fun sendEventAnalytics(event: EventEmitter.Event) = sendRequest(event)

    fun initialize() {
        eventEmitter.trackedEvents.subscribe { sendEventAnalytics(it).subscribe {  } }
    }
}
