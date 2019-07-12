package io.rover.sdk.ui.blocks.poll

import android.net.Uri
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.data.graphql.HttpResultMapper
import io.rover.sdk.data.graphql.toStringIntHash
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.streams.map
import io.rover.sdk.streams.subscribe
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.net.URL

internal class VotingService(
    private val endpoint: String,
    private val httpClient: HttpClient,
    private val httpResultMapper: HttpResultMapper = HttpResultMapper()
) {
    fun getResults(pollId: String, optionIds: List<String>): Publisher<ApiResult<OptionResults>> {
        val uri = Uri.parse(endpoint).buildUpon().apply {
            appendPath(pollId)
            optionIds.forEach {
                appendQueryParameter("options", it)
            }

        }
        val url = URL(uri.toString())
        val urlRequest = HttpRequest(url, hashMapOf("Content-Type" to "application/json"), HttpVerb.POST)

        return httpClient.request(urlRequest, null).map { httpClientResponse ->
            httpResultMapper.mapResultWithBody(httpClientResponse) {
                OptionResults.decodeJson(JSONObject(it))
            }
        }
    }

    fun castVote(pollId: String, optionId: String) {
        val uri = Uri.parse(endpoint).buildUpon().apply {
            appendPath(pollId)
            appendPath("vote")
        }
        val urlRequest = HttpRequest(URL(uri.toString()), hashMapOf(), HttpVerb.POST)

        val body = JSONObject().apply {
            put("option", optionId)
        }.toString()

        httpClient.request(urlRequest, body, false).subscribe {
            when (it) {
                is HttpClientResponse.Success -> {
                    log.v("vote in poll $pollId with option $optionId succeeded")
                }
                else -> {
                    log.w("voting failed")
                }
            }
        }
    }

}

internal data class OptionResults(val results: Map<String, Int>) {
    companion object {
        fun decodeJson(jsonObject: JSONObject): OptionResults {
            return OptionResults(
                results = jsonObject.getJSONObject("results").toStringIntHash()
            )
        }
    }
}