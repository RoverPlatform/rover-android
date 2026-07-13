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

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, process-lifetime record of the last-observed [AppScreenDataScope] for each App Screen
 * template path (see [AppScreensDecisions.templatePath]).
 *
 * Recording a scope lets subsequent loads of the same template skip the cold sequential fetch and
 * load concurrently (see [AppScreensDecisions.loadOrdering]). This is a cache, not a source of
 * truth: it is never persisted and is discarded when the process ends. Backed by a
 * [ConcurrentHashMap] so it is safe to read and write from multiple threads.
 */
internal class AppScreenScopeRegistry {
    private val scopes = ConcurrentHashMap<String, AppScreenDataScope>()

    /** The last-recorded scope for [templatePath], or null if none has been observed. */
    fun scopeFor(templatePath: String): AppScreenDataScope? {
        return scopes[templatePath]
    }

    /** Records the observed [scope] for [templatePath], replacing any previous value. */
    fun record(templatePath: String, scope: AppScreenDataScope) {
        scopes[templatePath] = scope
    }
}
