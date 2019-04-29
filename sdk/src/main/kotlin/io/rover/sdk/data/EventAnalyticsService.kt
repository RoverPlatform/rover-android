package io.rover.sdk.data

import android.os.Build
import io.rover.sdk.data.graphql.ApiError
import io.rover.sdk.data.graphql.encodeJson
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Responsible for dispatching Analytics events.
 */

open class EventAnalyticsService(
    private val endpoint: URL,
    private val accountToken: String?,
    private val httpClient: HttpClient,
    eventEmitter: EventEmitter
) {
    private fun buildRequest(endpoint: URL, accountToken: String?): HttpRequest {
        val headersMap = hashMapOf("Content-Type" to "application/json").apply {
            if (accountToken != null) put("x-rover-account-token", accountToken)
        }

        return HttpRequest(endpoint, headersMap, HttpVerb.POST)
    }

    private fun encodeBody(eventInformation: EventEmitter.Event): String {
        return JSONObject().apply {
            put("anonymousID", "")
            put("event", eventInformation.name)
            put("timestamp", dateAsIso8601(Date()))
            put("properties", eventInformation.attributes.encodeJson())
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

        log.v("going to make events network request $urlRequest, $bodyData")

        return httpClient.request(urlRequest, bodyData).map { httpClientResponse -> onResponse(httpClientResponse) }
    }

    init {
        eventEmitter.trackedEvents.flatMap { sendRequest(it) }.subscribe {}
    }

    private fun dateAsIso8601(date: Date): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(date)
        } else {
            // On legacy Android, we are using the RFC 822 (email) vs ISO 8601 date format, and
            // we use the following regex to transform it to something 8601 compatible.
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(date)
                .replace(Regex("(\\d\\d)(\\d\\d)$"), "$1:$2")
        }
    }
}

sealed class ApiResultWithoutResponseBody {
    data class Error(val throwable: Throwable) : ApiResultWithoutResponseBody()
    object Success : ApiResultWithoutResponseBody()
}
