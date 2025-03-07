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

package io.rover.sdk.core.data.sync

import android.net.Uri
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.data.graphql.operations.data.encodeJson
import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.data.http.HttpRequest
import io.rover.sdk.core.data.http.HttpVerb
import io.rover.sdk.core.data.http.NetworkClient
import io.rover.sdk.core.platform.DateFormattingInterface
import org.json.JSONArray
import org.reactivestreams.Publisher
import java.net.URL
import java.util.Locale

class SyncClient(
    private val endpoint: URL,
    private val authenticationContext: AuthenticationContextInterface,
    private val dateFormatting: DateFormattingInterface,
    private val networkClient: NetworkClient
) : SyncClientInterface {
    override fun executeSyncRequests(requests: List<SyncRequest>): Publisher<HttpClientResponse> {
        return networkClient.request(
            queryItems(requests),
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
                if (authenticationContext.sdkToken != null) {
                    this["x-rover-account-token"] = authenticationContext.sdkToken!!
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
