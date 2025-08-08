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

package io.rover.sdk.experiences.rich.ui.events

import android.content.Intent
import io.rover.sdk.experiences.rich.api.models.Experience
import java.net.URI
import java.util.*

internal data class ExperienceRequested(
    private val intent: Intent? = null,
    val experienceURL: String? = intent?.data?.toString() ?: intent?.getStringExtra(EXPERIENCE_URL)
) {
    val screenId: String? by lazy {
        val id = intent?.getStringExtra(SCREEN_ID)

        if (id != null) return@lazy id

        val url = experienceURL
        if (url?.contains(SCREEN_ID) == true) {
            val uuidRegex = Regex("[^&;]*")
            val sub = url.substringAfter("?$SCREEN_ID=")
            uuidRegex.find(sub)?.value
        } else {
            null
        }
    }

    /**
     * Either the [experienceURL] minus the [screenId] respectively.
     *
     * When launching a [Experience] from a URL this is the property that should be used.
     */
    val experienceURLForRequest: String? by lazy {
        experienceURL?.let { uriString ->
            val uri = URI.create(uriString)
            if (arrayOf("http", "https").contains(uri.scheme.lowercase(Locale.ROOT))) {
                // An Experience link was opened directly, just exclude any query parameters (eg screenID):
                URI(uri.scheme, uri.authority, uri.path, null, uri.fragment).toString()
            } else {
                // An Experience link (wrapped as a deep link with a custom scheme) was opened directly.
                // Replace the scheme with "https", and exclude the query parameters.
                URI("https", uri.authority, uri.path, null, uri.fragment).toString()
            }
        }
    }

    companion object {
        private const val SCREEN_ID: String = "screenID"
        private const val EXPERIENCE_URL: String = "experience-url"
    }
}
