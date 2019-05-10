package io.rover.sdk.data.events

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import io.rover.sdk.data.domain.Attributes
import io.rover.sdk.data.graphql.encodeJson
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.streams.subscribe
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Responsible for dispatching Analytics events.
 */
class AnalyticsService(
    context: Context,
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
        private const val BASE_CONTEXT_NAME: String = "io.rover.sdk.local-storage"
        private const val ANALYTICS_ENDPOINT: String = "https://analytics.rover.io/track"
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
        val (eventName, flatEvent) = when(eventInformation) {
            is RoverEvent.ExperiencePresented -> "Experience Presented" to eventInformation.toFlat()
            is RoverEvent.ExperienceDismissed -> "Experience Dismissed" to eventInformation.toFlat()
            is RoverEvent.ExperienceViewed -> "Experience Viewed" to eventInformation.toFlat()
            is RoverEvent.ScreenPresented -> "Screen Presented" to eventInformation.toFlat()
            is RoverEvent.ScreenDismissed -> "Screen Dismissed" to eventInformation.toFlat()
            is RoverEvent.ScreenViewed -> "Screen Viewed" to eventInformation.toFlat()
            is RoverEvent.BlockTapped -> "Block Tapped" to eventInformation.toFlat()
        }

        return JSONObject().apply {
            put("anonymousID", installationIdentifier)
            put("event", eventName)
            put("timestamp", dateAsIso8601(Date()))
            put("properties", flatEvent.encodeJson())
        }.toString()
    }

    private fun sendRequest(eventInformation: RoverEvent) {
        val urlRequest = buildRequest(URL(ANALYTICS_ENDPOINT), accountToken)
        val bodyData = encodeBody(eventInformation)

        request(urlRequest, bodyData)
    }

    init {
        eventEmitter.trackedEvents.subscribe { sendRequest(it) }
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

    fun request(
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

                    doOutput = true
                    requestMethod = request.verb.wireFormat
                }

                connection.outputStream.use { stream ->
                    DataOutputStream(stream).use { dataOutputStream ->
                        dataOutputStream.write(requestBody)
                    }
                }
            } catch (e: Exception) {
                this@AnalyticsService.log.w("$request : event analytics request failed ${e.message}")
            }
        }
    }
}

private fun RoverEvent.ExperiencePresented.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.ExperienceDismissed.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.ExperienceViewed.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags,
        "duration" to duration
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenPresented.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags,
        "screenName" to screen.name,
        "screenID" to screen.id,
        "screenTags" to screen.tags
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenDismissed.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags,
        "screenName" to screen.name,
        "screenID" to screen.id,
        "screenTags" to screen.tags
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.ScreenViewed.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags,
        "screenName" to screen.name,
        "screenID" to screen.id,
        "screenTags" to screen.tags,
        "duration" to duration
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}

private fun RoverEvent.BlockTapped.toFlat(): Attributes {
    return hashMapOf(
        "experienceID" to experience.id,
        "experienceName" to experience.name,
        "experienceTags" to experience.tags,
        "screenName" to screen.name,
        "screenID" to screen.id,
        "screenTags" to screen.tags,
        "blockID" to block.id,
        "blockName" to block.name,
        "blockTags" to block.tags
    ) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()
}
