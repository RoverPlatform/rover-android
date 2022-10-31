package io.rover.campaigns.core.data.sync

import android.net.Uri
import io.rover.campaigns.core.data.AuthenticationContext
import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.graphql.operations.data.encodeJson
import io.rover.campaigns.core.data.http.HttpClientResponse
import io.rover.campaigns.core.data.http.HttpRequest
import io.rover.campaigns.core.data.http.HttpVerb
import io.rover.campaigns.core.data.http.NetworkClient
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.DateFormattingInterface
import org.json.JSONArray
import org.reactivestreams.Publisher
import java.net.URL
import java.util.Locale

class SyncClient(
    private val endpoint: URL,
    private val authenticationContext: AuthenticationContext,
    private val dateFormatting: DateFormattingInterface,
    private val networkClient: NetworkClient
) : SyncClientInterface {
    override fun executeSyncRequests(requests: List<SyncRequest>): Publisher<HttpClientResponse> {
        return networkClient.request(
            queryItems(requests).apply {
                log.v("SYNC HTTP REQUEST: $this")
            },
            null
        )
    }

    private fun queryItems(requests: List<SyncRequest>): HttpRequest {
        val signatures = requests.mapNotNull { it.query.signature }

        val expression: String = if (signatures.isEmpty()) {
            ""
        } else {
            signatures.joinToString(", ")
        }.let { "($it)" }

        val body: String = requests.joinToString("\n") { it.query.definition }

        val query = """
            query Sync$expression {
                $body
            }
        """.trimIndent()

        val fragments: List<String>? = requests.flatMap { it.query.fragments }

        val initial = hashMapOf<String, Any>()

        val variables: Attributes = requests.fold(initial) { result, request ->
            request.variables.entries.fold(result) { nextResult, element ->
                nextResult["${request.query.name}${element.key.capitalize()}"] = element.value
                nextResult
            }
        }

        // remove any graphql whitespace.
        val condensedQuery = query.split("\n", " ", "\t").filter { it.isNotEmpty() }.joinToString(" ")

        log.v("Condensed sync query: $condensedQuery")

        val uri = Uri.parse(endpoint.toString())
        val builder = uri.buildUpon()

        builder.appendQueryParameter("query", condensedQuery)

        val variablesJson = variables.encodeJson()

        builder.appendQueryParameter(
            "variables",
            variablesJson.toString()
        )

        if (fragments != null) {
            builder.appendQueryParameter("fragments", JSONArray(fragments).toString())
        }

        return HttpRequest(
            URL(builder.build().toString()),
            hashMapOf<String, String>().apply {
                when {
                    authenticationContext.sdkToken != null -> this["x-rover-account-token"] = authenticationContext.sdkToken!!
                    authenticationContext.bearerToken != null -> this["authorization"] = "Bearer ${authenticationContext.bearerToken}"
                }
            },
            HttpVerb.GET
        )
    }
}

val SyncQuery.signature: String?
    get() {
        if (arguments.isEmpty()) {
            return null
        }

        return arguments.joinToString(", ") {
            "\$$name${it.name.capitalize(Locale.ROOT)}:${it.type}"
        }
    }

val SyncQuery.definition: String
    get() {

        if (arguments.isEmpty()) {
            return ""
        }

        val signature = arguments.joinToString(", ") {
            "${it.name}:\$$name${it.name.capitalize()}"
        }

        val expression = "($signature)"

        return """
        $name$expression {
            $body
        }
        """.trimIndent()
    }
