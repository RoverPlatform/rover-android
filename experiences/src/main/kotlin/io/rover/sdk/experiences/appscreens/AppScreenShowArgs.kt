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

package io.rover.sdk.experiences.appscreens

import org.json.JSONObject

/**
 * Builds the JSON `args` object for a runtime `show` down-call.
 *
 * [href] is JSON-quoted; [optimisticDataJson] and [responseJson] (when present) are ALREADY-VALID JSON text
 * produced by the runtime / server and are spliced in verbatim as values — never re-parsed or
 * re-serialized — so opaque payloads cross the bridge byte-for-byte. The result composes with
 * [AppScreenBridge.call], which itself splices `args` verbatim into the call envelope.
 */
internal object AppScreenShowArgs {

    /**
     * The wire key for the optimistic-data payload. It is round-tripped: the runtime emits it on a
     * `navigate` message (read by [AppScreenBridge]) and this builder splices it back into the `show`
     * args under the same key. This is the single source of that literal; changing it requires the
     * server runtime to match.
     */
    const val OPTIMISTIC_DATA_KEY: String = "optimisticData"

    fun build(href: String, optimisticDataJson: String? = null, responseJson: String? = null): String {
        val builder = StringBuilder()
        builder.append("{\"href\":")
        builder.append(JSONObject.quote(href))
        if (optimisticDataJson != null) {
            builder.append(",").append(JSONObject.quote(OPTIMISTIC_DATA_KEY)).append(":")
            builder.append(optimisticDataJson)
        }
        if (responseJson != null) {
            builder.append(",\"response\":")
            builder.append(responseJson)
        }
        builder.append("}")
        return builder.toString()
    }
}
