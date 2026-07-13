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

package io.rover.sdk.experiences.appscreens.network

import io.rover.sdk.experiences.appscreens.AppScreenDataScope

/**
 * The result of fetching an App Screen HTML document (the always-anonymous document channel).
 *
 * @property html The unified HTML document body, loaded into the WebView via
 * `loadDataWithBaseURL`.
 * @property etag The raw `ETag` response header (un-normalized; callers normalize via
 * [io.rover.sdk.experiences.appscreens.AppScreensDecisions.normalizeETag] before comparing to a
 * template hash), or null when the server did not send one.
 * @property dataScope The parsed `x-rover-app-screen-data-scope` header, or null when absent or
 * unrecognized (callers fail safe to PERSONALIZED via
 * [io.rover.sdk.experiences.appscreens.AppScreensDecisions.effectiveScope]).
 */
internal data class AppScreenDocument(
    val html: String,
    val etag: String?,
    val dataScope: AppScreenDataScope?
)
