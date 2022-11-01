package io.rover.experiences.data.events

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.AsyncTask
import io.rover.core.data.http.HttpRequest
import io.rover.core.data.http.HttpVerb
import io.rover.experiences.data.domain.Attributes
import io.rover.experiences.data.graphql.encodeJson
import io.rover.experiences.logging.log
import io.rover.experiences.platform.dateAsIso8601
import io.rover.experiences.platform.debugExplanation
import io.rover.experiences.platform.setRoverUserAgent
import io.rover.experiences.services.EventEmitter
import io.rover.experiences.streams.subscribe
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID

/**
 * Responsible for dispatching Analytics events.
 */
internal class AnalyticsService(
    context: Context,
    private val packageInfo: PackageInfo,
    private val accountToken: String?,
    eventEmitter: EventEmitter
) {

    private val prefs: SharedPreferences? =
        context.getSharedPreferences(BASE_CONTEXT_NAME, Context.MODE_PRIVATE)

    private val installationIdentifier by lazy {
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        prefs?.getString(STORAGE_IDENTIFIER, null) ?: createNewUUID()
    }

    companion object {
        private const val STORAGE_IDENTIFIER = "device-identification"
        private const val BASE_CONTEXT_NAME: String = "io.rover.experiences.local-storage"
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

    private fun encodeBody(eventInformation: RoverEvent): String {
        val (eventName, flatEvent) = when (eventInformation) {
            is RoverEvent.ExperiencePresented -> "Experience Presented" to eventInformation.toFlat()
            is RoverEvent.ExperienceDismissed -> "Experience Dismissed" to eventInformation.toFlat()
            is RoverEvent.ExperienceViewed -> "Experience Viewed" to eventInformation.toFlat()
            is RoverEvent.ScreenPresented -> "Screen Presented" to eventInformation.toFlat()
            is RoverEvent.ScreenDismissed -> "Screen Dismissed" to eventInformation.toFlat()
            is RoverEvent.ScreenViewed -> "Screen Viewed" to eventInformation.toFlat()
            is RoverEvent.BlockTapped -> "Block Tapped" to eventInformation.toFlat()
            is RoverEvent.PollAnswered -> "Poll Answered" to eventInformation.toFlat()
            is RoverEvent.AppOpened -> "App Opened" to mapOf()
        }

        return JSONObject().apply {
            put("anonymousID", installationIdentifier)
            put("event", eventName)
            put("timestamp", Date().dateAsIso8601())
            if (flatEvent.isNotEmpty()) put("properties", flatEvent.encodeJson())
        }.toString()
    }

    private fun sendRequest(eventInformation: RoverEvent) {
        try {
            val urlRequest = buildRequest(URL(ANALYTICS_ENDPOINT), accountToken)
            val bodyData = encodeBody(eventInformation)

            request(urlRequest, bodyData)
        } catch (e: Exception) {
            log.w("Problem sending analytics: ${e.message}")
        }
    }

    /**
     * Initiates the [AnalyticsService] sending of analytics events.
     */
    init {
        eventEmitter.trackedEvents.subscribe { sendRequest(it) }
    }

    private fun request(
        request: HttpRequest,
        bodyData: String
    ) {
        AsyncTask.execute {
            try {
                val connection = request.url.openConnection() as HttpURLConnection
                val requestBody = bodyData.toByteArray(Charsets.UTF_8)

                connection.apply {
                    setFixedLengthStreamingMode(requestBody.size)
                    request.headers.onEach { (field, value) -> setRequestProperty(field, value) }

                    this.setRoverUserAgent(packageInfo)

                    doOutput = true
                    requestMethod = request.verb.wireFormat
                }

                connection.outputStream.use { stream ->
                    DataOutputStream(stream).use { dataOutputStream ->
                        dataOutputStream.write(requestBody)
                    }
                }
            } catch (e: Exception) {
                this@AnalyticsService.log.w("$request : event analytics request failed ${e.debugExplanation()}")
            }
        }
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

private fun RoverEvent.ExperiencePresented.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun RoverEvent.ExperienceDismissed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun RoverEvent.ExperienceViewed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        DURATION to duration
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenPresented.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenDismissed.toFlat(): Attributes {
    return hashMapOf(
        EXPERIENCE_ID to experience.id,
        EXPERIENCE_NAME to experience.name,
        EXPERIENCE_TAGS to experience.tags,
        SCREEN_NAME to screen.name,
        SCREEN_ID to screen.id,
        SCREEN_TAGS to screen.tags
    ) + if (campaignId != null) hashMapOf(CAMPAIGN_ID to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenViewed.toFlat(): Attributes {
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

private fun RoverEvent.BlockTapped.toFlat(): Attributes {
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

private fun RoverEvent.PollAnswered.toFlat(): Attributes {
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
