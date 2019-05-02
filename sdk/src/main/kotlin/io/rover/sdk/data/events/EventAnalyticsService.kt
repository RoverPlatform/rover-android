package io.rover.sdk.data.events

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import io.rover.sdk.data.graphql.ApiError
import io.rover.sdk.data.graphql.encodeJson
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.subscribe
import io.rover.sdk.streams.subscribeOn
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Responsible for dispatching Analytics events.
 */

open class EventAnalyticsService(
    context: Context,
    private val accountToken: String?,
    eventEmitter: EventEmitter
) {

    private val prefs: SharedPreferences? = context.getSharedPreferences(BASE_CONTEXT_NAME, Context.MODE_PRIVATE)

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
        return UUID.randomUUID().toString().apply { prefs?.edit()?.putString(STORAGE_IDENTIFIER, this)?.apply() }
    }

    private fun buildRequest(endpoint: URL, accountToken: String?): HttpRequest {
        val headersMap = hashMapOf("Content-Type" to "application/json").apply {
            if (accountToken != null) put("x-rover-account-token", accountToken)
        }

        return HttpRequest(endpoint, headersMap, HttpVerb.POST)
    }

    private fun encodeBody(eventInformation: EventEmitter.Event): String {
        return JSONObject().apply {
            put("anonymousID", installationIdentifier)
            put("event", eventInformation.name)
            put("timestamp", dateAsIso8601(Date()))
            put("properties", eventInformation.attributes.encodeJson())
        }.toString()
    }

    private fun sendRequest(eventInformation: EventEmitter.Event) {
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

    open fun request(
        request: HttpRequest,
        bodyData: String
    ) {
        AsyncTask.execute {
            val connection = request.url.openConnection() as HttpURLConnection
            val requestBody = bodyData.toByteArray(Charsets.UTF_8)

            connection.apply {
                    setFixedLengthStreamingMode(requestBody.size)
                    request.headers.onEach { (field, value) -> setRequestProperty(field, value) }

                    doOutput = true
                    requestMethod = request.verb.wireFormat
                }

            try {
                connection.outputStream.use { stream ->
                    DataOutputStream(stream).use { dataOutputStream ->
                        dataOutputStream.write(requestBody)
                    }
                }
            } catch (e: IOException) {
                return@execute
            }
        }
    }
}