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

/**
 * The data scope of an App Screen document, as advertised by the server via the
 * [HEADER_NAME] response header.
 *
 * [PUBLIC] documents contain no personalized data and may be cached/shared. [PERSONALIZED]
 * documents are specific to the current identity and must never be treated as public. When the
 * scope is missing or unknown it is treated as [PERSONALIZED] (fail safe); see
 * [AppScreensDecisions.effectiveScope].
 */
internal enum class AppScreenDataScope {
    PUBLIC,
    PERSONALIZED;

    companion object {
        /**
         * The response header the server uses to advertise the data scope of an App Screen
         * document.
         */
        const val HEADER_NAME = "x-rover-app-screen-data-scope"

        /**
         * Parse an [AppScreenDataScope] from a raw header value.
         *
         * Trims surrounding whitespace and lowercases before matching. `"public"` maps to
         * [PUBLIC] and `"personalized"` maps to [PERSONALIZED]. Anything else — including null,
         * blank, or an unrecognized value — returns null so the caller can apply its fail-safe
         * default.
         */
        fun fromHeader(value: String?): AppScreenDataScope? {
            return when (value?.trim()?.lowercase()) {
                "public" -> PUBLIC
                "personalized" -> PERSONALIZED
                else -> null
            }
        }
    }
}
