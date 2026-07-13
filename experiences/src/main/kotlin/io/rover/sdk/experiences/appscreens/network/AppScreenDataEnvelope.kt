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
 * The result of fetching an App Screen data document (the scope-dependent `.json` channel).
 *
 * The [rawJson] body is kept verbatim as a String because it crosses the JS bridge byte-for-byte
 * as the `response` argument to the runtime's `show` call. Only [templateHash] (for the hash
 * handshake) and [responseScope] (for the one-shot retry decision) are peeked out of it.
 *
 * @property rawJson The unmodified `{data, user, images, params, templateHash}` JSON body.
 * @property templateHash The value of the top-level `templateHash` key, or null when absent/blank.
 * @property responseScope The parsed `x-rover-app-screen-data-scope` response header, or null.
 */
internal data class AppScreenDataEnvelope(
    val rawJson: String,
    val templateHash: String?,
    val responseScope: AppScreenDataScope?
)
