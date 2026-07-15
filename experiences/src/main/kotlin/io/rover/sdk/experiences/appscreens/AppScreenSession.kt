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

import android.content.Context
import android.content.MutableContextWrapper
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The mutable state of one App Screen render surface: its [WebView], the [AppScreenBridge] installed
 * on it, the phase surfaced to the host composable, and the identity captured off the document/data
 * channels.
 *
 * A session is the unit the navigator ([AppScreenNavigator]) owns and pools. A warm session (one per
 * [templateKey]) has a loaded runtime and can be re-shown with a new href in place; ephemeral
 * sessions are one-offs destroyed on pop. The [WebView] is created once (over a
 * [android.content.MutableContextWrapper], see [AppScreenWebViewFactory]) and re-parented as it
 * moves between navigation slots; [appThemedContext] is the neutral (Activity-free) context its
 * wrapper is swapped back to on detach to avoid leaking a host Activity into the warm pool.
 */
internal class AppScreenSession(
    /**
     * The origin-qualified template identity this session serves (see
     * [AppScreensDecisions.templateKey]): `scheme://host[:port]/path`. The pool keys warm sessions
     * by it, and the session's bridge origin is derived from the origin embedded in it — so a
     * session built for one associated domain never carries a bridge trusting a different one.
     */
    val templateKey: String,
    initialWebView: WebView,
    initialBridge: AppScreenBridge,
    initialAppThemedContext: Context,
    initialForcedDark: Boolean?
) {
    /**
     * The neutral (Activity-free) themed context the WebView's [android.content.MutableContextWrapper]
     * is swapped back to on detach, so a warm pooled WebView never retains a host Activity. Carries
     * the same forced night-mode bits as the WebView's construction context; replaced together with
     * the WebView in [replaceWebView] so a config-flip rebuild can't re-base the new WebView onto the
     * old scheme's context at detach. Main thread only.
     */
    var appThemedContext: Context = initialAppThemedContext
        private set

    /**
     * The forced dark scheme this session's WebView was built for — the [forcedDark] mapping of the
     * HOST-PROVIDED per-surface colour-scheme override that was active when the session was created
     * (Hub-only policy; standalone surfaces provide none and follow the device). NOT read from the
     * remote config by App Screens; the navigator threads the active surface's override in. `true`
     * forces dark, `false` forces light, `null` follows the device. The host reads it at attach time
     * to build the swapped-in Activity context with the same forced scheme (see `AppScreenHost`) —
     * that attach context is what an ATTACHED WebView actually resolves `prefers-color-scheme` through
     * (Chromium re-evaluates on attach), while the construction context here covers the detached
     * phases (prewarm, warm pool, initial load). Both must agree or the page flips scheme at attach.
     *
     * Mutable (not a constructor `val`) because a live host appearance change (a Hub config flip while
     * the surface stays composed) rebuilds a composed session's WebView IN PLACE through the existing
     * renderer-recovery machinery — the same session object is kept, so the navigator updates this to
     * the new scheme before the rebuild reconstructs the WebView over it. The same update happens when
     * the surface-crossing claim guard re-themes a reused master. Main thread only.
     */
    var forcedDark: Boolean? = initialForcedDark
        private set

    /** Update the recorded forced scheme ahead of a config-change WebView rebuild. Main thread only. */
    fun updateForcedDark(value: Boolean?) {
        forcedDark = value
    }

    /** Coarse lifecycle of the session, for host phase logic and diagnostics. */
    enum class State {
        LoadingDocument,
        AwaitingRuntime,
        Ready,
        Failed
    }

    /**
     * The live [WebView] rendering this session. Backed by Compose state so the host re-attaches
     * automatically when recovery swaps in a fresh WebView after a renderer death (the old
     * instance is dead and cannot be reloaded — unlike iOS). Main thread only.
     */
    var webView: WebView by mutableStateOf(initialWebView)
        private set

    /** The bridge installed on the current [webView]; replaced together with it on recovery. */
    var bridge: AppScreenBridge = initialBridge
        private set

    /**
     * Swap in a freshly-built [WebView] and [AppScreenBridge] after the previous renderer died
     * or a config colorScheme flip forced a rebuild. The caller is responsible for detaching +
     * destroying the old WebView and re-wiring the new bridge's handlers. The detach-time
     * [appThemedContext] follows the new WebView's own wrapper base (the factory layering is a
     * documented invariant), so it always matches the new construction context's forced scheme.
     * Main thread only.
     */
    fun replaceWebView(newWebView: WebView, newBridge: AppScreenBridge) {
        webView = newWebView
        bridge = newBridge
        (newWebView.context as? MutableContextWrapper)?.let { appThemedContext = it.baseContext }
    }

    /**
     * The reveal/skeleton/error phase observed by the host composable. Mutated only on the main
     * thread (the navigator's pipelines run on the main dispatcher).
     */
    val phase = mutableStateOf<AppScreenPhase>(AppScreenPhase.Loading)

    @Volatile
    var state: State = State.LoadingDocument

    /** The href this session is currently showing (updated on each navigation/reuse). */
    @Volatile
    var currentHref: String = ""

    /** The raw (un-normalized) document ETag last loaded into the WebView. */
    @Volatile
    var documentETag: String? = null

    /** The effective data scope observed for this session's document. */
    @Volatile
    var dataScope: AppScreenDataScope? = null

    /** Whether the runtime has booted at least once (so warm reuse can skip the document load). */
    @Volatile
    var runtimeLoaded: Boolean = false

    /**
     * The args of the LAST successfully-delivered `show` (hydrate or morph). Renderer-death recovery replays
     * this after rebuilding the WebView to repaint the full pre-crash content in one call. Updated
     * on every successful show; a morph's payload (which carries the response) supersedes its
     * hydrate. Null until the first show resolves. Main thread only.
     */
    @Volatile
    var lastShowPayload: ShowPayload? = null

    /**
     * The one-attempt guard for renderer-death recovery. Set when a recovery attempt begins;
     * cleared only when a pipeline runs clean through reveal. A second liveness failure while this
     * is set lands the (visible) session in the error state, so a crash-loop settles after exactly
     * one retry. The error state's Retry clears it. Main thread only.
     */
    @Volatile
    var didAttemptRecovery: Boolean = false

    /**
     * Set once the session's WebView has been detached and destroyed for good (warm-idle discard on
     * a renderer death). Guards the renderer-gone handler against acting twice on a torn-down
     * session. Main thread only.
     */
    @Volatile
    var isDestroyed: Boolean = false

    /**
     * Observed by the host: true when the renderer died while this session was on a stack but not
     * visible. The host, on becoming visible again (a pop-to / sheet dismissal re-composes it),
     * triggers lazy recovery. Compose state so that reappearance drives the recovery.
     */
    val dead = mutableStateOf(false)

    /**
     * The safe-area insets last published to this session's document, or null if none have been
     * injected yet. Read/written only on the main thread; kept so the host can skip redundant
     * injections and the navigator can cheaply re-inject after a reload wipes the inline styles.
     */
    var lastInjectedInsets: AppScreenInsets? = null

    /**
     * Publish [insets] to the document as the `--rover-safe-area-inset-*` custom properties and
     * remember them. Fire-and-forget; must be called on the main thread. No-op when the values are
     * unchanged from the last injection.
     */
    fun injectInsets(insets: AppScreenInsets) {
        if (insets == lastInjectedInsets) return
        lastInjectedInsets = insets
        webView.evaluateJavascript(AppScreenInsetsScript.build(insets), null)
    }

    /**
     * Re-inject the last-known insets (e.g. after a reload replaced the document, wiping the inline
     * custom properties). No-op if nothing has been injected yet. Main thread only.
     */
    fun reinjectInsets() {
        val insets = lastInjectedInsets ?: return
        webView.evaluateJavascript(AppScreenInsetsScript.build(insets), null)
    }
}

/**
 * A snapshot of the arguments of a successfully-delivered `show`, retained per session so
 * renderer-death recovery can replay it against a freshly-built WebView (see [AppScreenSession.lastShowPayload]).
 *
 * [href], [optimisticDataJson], and [responseJson] are the exact inputs to [AppScreenShowArgs.build] — the
 * raw JSON crosses the bridge byte-perfect on replay. [templateHash] is the hash the response was
 * baked against (null for a hydrate-only payload); recovery uses it to detect a rebake since the
 * crash and, if so, run the normal document-refetch handshake before replaying.
 */
internal data class ShowPayload(
    val href: String,
    val optimisticDataJson: String?,
    val responseJson: String?,
    val templateHash: String?
) {
    /** Rebuild the `show` args JSON for replay. */
    fun toArgs(): String = AppScreenShowArgs.build(href, optimisticDataJson, responseJson)
}
