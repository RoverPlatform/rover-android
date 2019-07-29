package io.rover.sdk.polls

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpResultMapper
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.streams.Publishers
import io.rover.sdk.ui.blocks.poll.URLBuilder
import io.rover.sdk.ui.blocks.poll.VotingService
import org.json.JSONObject
import org.reactivestreams.Publisher
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.URL

object VotingServiceSpec : Spek({
    val endPoint = "endpoint"

    val httpResultMapper: HttpResultMapper = mock()
    val urlBuilder: URLBuilder = mock()
    val url: URL = mock()
    val httpClientResponse: HttpClientResponse = mock()
    val jsonObject: JSONObject = mock()
    val requestBody = "request body"

    val pollId = "poll id"
    val optionIds = listOf("option id")

    describe("fetch results") {
        val httpClient: HttpClient = mock()
        val votingService = VotingService(endPoint, httpClient, httpResultMapper, urlBuilder)
        whenever(urlBuilder.build(endPoint, listOf(pollId), optionIds.map { "options" to it })).thenReturn(url)
        whenever(httpClient.request(HttpRequest(url, hashMapOf(), HttpVerb.GET), null)).thenReturn(Publishers.just(httpClientResponse))

        votingService.fetchResults(pollId, optionIds)
        it("makes request with expected arguments") {
            verify(httpClient).request(HttpRequest(url, hashMapOf(), HttpVerb.GET), null)
        }
    }

    describe("cast vote makes request with expected args") {
        val publisher: Publisher<HttpClientResponse> = mock()
        val httpClient: HttpClient = mock()
        val votingService = VotingService(endPoint, httpClient, httpResultMapper, urlBuilder)

        whenever(urlBuilder.build(endPoint, listOf(pollId, "vote"))).thenReturn(url)
        whenever(jsonObject.put("option", optionIds.first())).thenReturn(jsonObject)
        whenever(jsonObject.toString()).thenReturn(requestBody)
        whenever(httpClient.request(any(), any(), any())).thenReturn(publisher)

        votingService.castVote(pollId, optionIds.first(), jsonObject)

        it("makes request with expected arguments") {
            verify(httpClient).request(HttpRequest(url, hashMapOf("Content-Type" to "application/json"), HttpVerb.POST), requestBody, false)
        }
    }
})
