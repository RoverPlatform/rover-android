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

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import io.rover.sdk.core.logging.log

/**
 * The [WebViewClient] installed on every App Screen WebView (M6). Its sole job is to intercept
 * renderer death via [onRenderProcessGone] and route it to the navigator.
 *
 * Returning `true` from [onRenderProcessGone] is mandatory: the default returns `false`, which makes
 * the framework kill the whole app process when the renderer dies. All WebViews in a process usually
 * share ONE renderer, so a single crash fires this on every live WebView nearly simultaneously; the
 * navigator's handler is written to be idempotent and to classify each session independently.
 *
 * The [onRenderProcessGone] handler is set by the navigator once the owning [AppScreenSession] is
 * known (mirroring the bridge's late-bound `onNavigate`/`onLinks`), so the same client type can be
 * factory-installed before the session exists and re-wired to a fresh session on recovery.
 */
internal class AppScreenWebViewClient : WebViewClient() {

    /**
     * Invoked on the main thread when this WebView's renderer dies. Receives the dead [WebView] (so
     * the navigator can guard against stale callbacks for an already-replaced instance) and whether
     * the renderer crashed (`true`) versus was reclaimed by the OS under memory pressure (`false`).
     * Null until the navigator wires it.
     */
    @Volatile
    var onRenderProcessGone: ((view: WebView, didCrash: Boolean) -> Unit)? = null

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        val didCrash = detail.didCrash()
        val handler = onRenderProcessGone
        if (handler == null) {
            // No session wired yet (e.g. a bare factory WebView). Still MUST return true or the OS
            // kills the app process; drop the dead WebView on the caller's floor.
            log.w("App Screen renderer gone (didCrash=$didCrash) with no handler installed; dropping")
        } else {
            handler(view, didCrash)
        }
        // Never let the system default (false) run: that tears down the whole app process.
        return true
    }
}

/**
 * A log-only [WebViewRenderProcessClient] (androidx.webkit) installed where the platform supports
 * it. It reports renderer un/responsiveness; App Screens does not act on these — the bounded awaits
 * in the load pipelines already cover user-facing hangs — but the signal is logged for diagnostics.
 */
internal class AppScreenRenderProcessClient : WebViewRenderProcessClient() {
    override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
        log.w("App Screen WebView renderer unresponsive (bounded awaits will surface any hang)")
    }

    override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
        log.i("App Screen WebView renderer responsive again")
    }

    companion object {
        /** Install the log-only render-process client on [webView] where the feature exists. */
        fun installIfSupported(webView: WebView) {
            if (WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE
                )
            ) {
                WebViewCompat.setWebViewRenderProcessClient(webView, AppScreenRenderProcessClient())
            }
        }
    }
}
