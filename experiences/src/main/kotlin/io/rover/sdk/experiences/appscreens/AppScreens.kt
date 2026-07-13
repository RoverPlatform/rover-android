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

import android.net.Uri
import androidx.annotation.RestrictTo

/**
 * Cross-module hooks for App Screens (Experiences V3), exposed to sibling Rover SDK modules only.
 *
 * This is NOT part of the supported public SDK API surface: it is annotated
 * [RestrictTo][androidx.annotation.RestrictTo] with [LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]
 * so that only other Rover modules in this library group (e.g. `notifications`, which embeds an App
 * Screens home inside the Communication Hub) may call it. App integrators should continue to render
 * experiences through the ordinary experiences entry points.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object AppScreens {
    /**
     * Returns true iff [url] is an App Screen (Experiences V3) URL — its first path segment is
     * exactly `"a"` and there is at least one further non-empty segment (e.g. `/a/home`, `/a/x/y`).
     *
     * Delegates to the same [AppScreensDecisions.isAppScreenUrl] classifier that the experiences
     * renderer uses internally, so sibling modules classify a home/experience URL identically to
     * the pipeline that will ultimately render it.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun isAppScreenUrl(url: Uri): Boolean = AppScreensDecisions.isAppScreenUrl(url)
}
