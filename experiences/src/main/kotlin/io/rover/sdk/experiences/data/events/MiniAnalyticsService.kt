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

package io.rover.sdk.experiences.data.events

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.data.graphql.operations.data.encodeJson
import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.data.http.HttpRequest
import io.rover.sdk.core.data.http.HttpVerb
import io.rover.sdk.core.data.http.NetworkClient
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.platform.dateAsIso8601
import io.rover.sdk.experiences.platform.debugExplanation
import io.rover.sdk.experiences.services.ClassicEventEmitter
import org.json.JSONObject
import java.net.URL
import java.util.*

/**
 * Responsible for dispatching Analytics events observed from the Rover event queue into the legacy
 * Analytics service (analytics.rover.io).
 */
internal class MiniAnalyticsService(
    context: Context,
    private val networkClient: NetworkClient,
    private val accountToken: String?,
    classicEventEmitter: ClassicEventEmitter
) {

    private val prefs: SharedPreferences? =
        context.getSharedPreferences(BASE_CONTEXT_NAME, Context.MODE_PRIVATE)

    private val installationIdentifier by lazy {
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        prefs?.getString(STORAGE_IDENTIFIER, null) ?: createNewUUID()
    }

    companion object {
        private const val STORAGE_IDENTIFIER = "device-identification"
        private const val BASE_CONTEXT_NAME: String = "io.rover.local-storage"
        private const val ANALYTICS_ENDPOINT: String = "https://analytics.rover.io/"
    }

    private fun createNewUUID(): String {
        return UUID.randomUUID().toString()
            .apply { prefs?.edit()?.putString(STORAGE_IDENTIFIER, this)?.apply() }
    }

    private fun buildRequest(endpoint: URL, accountToken: String?): HttpRequest {
        val headersMap = hashMapOf("Content-Type" to "application/json").apply {
            if (accountToken != null) put("x-rover-account-token", accountToken)
        }

        return HttpRequest(endpoint, headersMap, HttpVerb.POST)
    }

    /**
     * If the mini analytics pipeline wants to receive an event for the given [MiniAnalyticsEvent], returns
     * a message body.
     */
    private fun encodeBody(eventInformation: MiniAnalyticsEvent): String {
        val (eventName, flatEvent) = when (eventInformation) {
            is MiniAnalyticsEvent.ExperiencePresented -> "Experience Presented" to eventInformation.toFlat()
            is MiniAnalyticsEvent.ExperienceDismissed -> "Experience Dismissed" to eventInformation.toFlat()
            is MiniAnalyticsEvent.ExperienceViewed -> "Experience Viewed" to eventInformation.toFlat()
            is MiniAnalyticsEvent.ScreenPresented -> "Screen Presented" to eventInformation.toFlat()
            is MiniAnalyticsEvent.ScreenDismissed -> "Screen Dismissed" to eventInformation.toFlat()
            is MiniAnalyticsEvent.ScreenViewed -> "Screen Viewed" to eventInformation.toFlat()
            is MiniAnalyticsEvent.BlockTapped -> "Block Tapped" to eventInformation.toFlat()
            is MiniAnalyticsEvent.PollAnswered -> "Poll Answered" to eventInformation.toFlat()
            is MiniAnalyticsEvent.AppOpened -> "App Opened" to mapOf()
        }

        return JSONObject().apply {
            put("anonymousID", installationIdentifier)
            put("event", eventName)
            put("timestamp", Date().dateAsIso8601())
            if (flatEvent.isNotEmpty()) put("properties", flatEvent.encodeJson())
        }.toString()
    }

    private fun sendRequest(eventInformation: MiniAnalyticsEvent) {
        try {
            val urlRequest = buildRequest(URL(ANALYTICS_ENDPOINT), accountToken)
            val bodyData = encodeBody(eventInformation)

            networkClient.request(urlRequest, bodyData).subscribe { response ->
                when (response) {
                    is HttpClientResponse.Success -> log.v("Mini-Analytics event sent.")
                    is HttpClientResponse.ApplicationError -> log.w("Mini-Analytics event failed: ${response.reportedReason}")
                    is HttpClientResponse.ConnectionFailure -> log.w("Mini-Analytics event failed: ${response.reason.debugExplanation()}")
                }
            }
        } catch (e: Exception) {
            log.w("Problem sending analytics: ${e.message}")
        }
    }

    /**
     * Initiates the [AnalyticsService] sending of analytics events.
     */
    init {
        classicEventEmitter.trackedEvents.subscribe { sendRequest(it) }
    }
}

private const val EXPERIENCE_ID = "experienceID"
private const val EXPERIENCE_NAME = "experienceName"
private const val EXPERIENCE_TAGS = "experienceTags"
private const val CAMPAIGN_ID = "campaignID"
private const val DURATION = "duration"
private const val SCREEN_NAME = "screenName"
private const val SCREEN_ID = "screenID"
private const val SCREEN_TAGS = "screenTags"
private const val BLOCK_ID = "blockID"
private const val BLOCK_NAME = "blockName"
private const val BLOCK_TAGS = "blockTags"
private const val OPTION_ID = "optionID"
private const val OPTION_TEXT = "optionText"
private const val OPTION_IMAGE = "optionImage"

private fun MiniAnalyticsEvent.ExperiencePresented.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.ExperienceDismissed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.ExperienceViewed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        DURATION to duration
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.ScreenPresented.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.ScreenDismissed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.ScreenViewed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags,
        DURATION to duration
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.BlockTapped.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags,
        BLOCK_ID to block.id,
        BLOCK_NAME to block.name,
        BLOCK_TAGS to block.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun MiniAnalyticsEvent.PollAnswered.toFlat(): Attributes {
    val attributes = mutableMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags,
        BLOCK_ID to block.id,
        BLOCK_NAME to block.name,
        BLOCK_TAGS to block.tags,
        OPTION_ID to option.id,
        OPTION_TEXT to option.text
    )
    option.image?.let { attributes[OPTION_IMAGE] = it }
    campaignId?.let { attributes[CAMPAIGN_ID] = it }

    return attributes
}
