package io.rover.sdk.data

import android.net.Uri
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.streams.map
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.graphql.*
import io.rover.sdk.data.operations.FetchExperienceRequest
import org.json.JSONException
import org.reactivestreams.Publisher
import java.io.IOException
import java.net.URL

/**
 * Responsible for providing access the Rover cloud API, powered by GraphQL.
 */

open class EventAnalyticsService(
    private val endpoint: URL,
    private val accountToken: String?,
    private val httpClient: HttpClient
) {
    open fun <T> sendRequest(request: Request<T>): Publisher<T> {
        val urlRequest = request.buildRequest(endpoint, accountToken)
        val bodyData = request.encodeBody()

        log.v("going to make events network request $urlRequest")

        return httpClient.request(urlRequest, bodyData).map { httpClientResponse -> request.onResponse(httpClientResponse) }
    }

    open fun sendEventAnalytics(eventInformation: Map<String, String>) = sendRequest(EventAnalyticsRequest(eventInformation))
}

interface Request<T> {
    fun encodeBody(): String?

    val requestVerb: HttpVerb

    fun buildRequest(endpoint: URL, accountToken: String?): HttpRequest {
        val uri = Uri.parse(endpoint.toString())
        val builder = uri.buildUpon()

        val headersMap = hashMapOf("Content-Type" to "application/json").apply {
            if (accountToken != null) put("x-rover-account-token", accountToken)
        }

        return HttpRequest(URL(builder.toString()), headersMap, requestVerb)
    }

    fun onResponse(httpResponse: HttpClientResponse): T
}

class EventAnalyticsRequest(val eventInformation: Map<String, String>) : Request<ApiResultWithoutResponseBody> {
    override fun encodeBody(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val requestVerb = HttpVerb.POST

    override fun onResponse(httpResponse: HttpClientResponse): ApiResultWithoutResponseBody {
        return when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> ApiResultWithoutResponseBody.Error(httpResponse.reason)
            is HttpClientResponse.ApplicationError -> {
                log.w("Given EventAnalytics error reason: ${httpResponse.reportedReason}")
                ApiResultWithoutResponseBody.Error(ApiError.InvalidStatusCode(httpResponse.responseCode, httpResponse.reportedReason))
            }
            is HttpClientResponse.Success -> { ApiResultWithoutResponseBody.Success }
        }
    }

}
