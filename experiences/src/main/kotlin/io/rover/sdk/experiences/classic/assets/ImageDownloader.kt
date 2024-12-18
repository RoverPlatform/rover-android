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

package io.rover.sdk.experiences.classic.assets

import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.data.http.HttpRequest
import io.rover.sdk.core.data.http.HttpVerb
import io.rover.sdk.core.data.http.NetworkClient
import org.reactivestreams.Publisher
import java.net.URL

/**
 * A simple HTTP downloader.
 */
internal class ImageDownloader(
    private val networkClient: NetworkClient
) {
    /**
     * Download the given URL as a stream.  Supports HTTP and HTTPS, but only GET is supported.
     *
     * @return a async Publisher that yields a single [HttpClientResponse].  That response itself
     * will contain a stream that can be read from until the connection completes.
     */
    fun downloadStreamFromUrl(url: URL): Publisher<HttpClientResponse> {
        val request = HttpRequest(
            url, hashMapOf(), HttpVerb.GET
        )

        return networkClient.request(request, bodyData = null)
    }
}
