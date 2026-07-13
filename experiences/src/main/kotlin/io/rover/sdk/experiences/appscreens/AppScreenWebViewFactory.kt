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

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.rover.sdk.core.logging.log

/**
 * Builds the [WebView] used to render an App Screen document.
 *
 * The WebView is created against a [MutableContextWrapper] wrapping a
 * [ContextThemeWrapper] over the application context using
 * [android.R.style.Theme_DeviceDefault_DayNight]. This is deliberate:
 *
 * 1. The DayNight theme lets the WebView resolve `prefers-color-scheme` from this context's
 *    appearance (its theme's `android:isLightTheme`, falling back to the context's
 *    `Configuration.uiMode`) without leaking an [android.app.Activity] into a long-lived view.
 * 2. The [MutableContextWrapper] base means M4 can swap in the hosting Activity's context at
 *    attach time (for correct window/IME behaviour) without recreating the WebView, then swap it
 *    back out on detach to avoid an Activity leak.
 *
 * How `prefers-color-scheme` resolves: Chromium reads it from the WebView's CURRENT context — the
 * [MutableContextWrapper]'s base at evaluation time, re-evaluated at least on window attach — never
 * from the surrounding Compose theme. (Device-proven: a construction context forced dark did NOT
 * survive an attach-time swap to an unthemed base; see `withForcedNightModeTheme`.) The context
 * built here is what the WebView resolves through while DETACHED — prewarm, the warm pool, and the
 * initial load all happen detached — so the caller passes [forcedDark] (the HOST-provided per-surface
 * override, Hub-only policy; not read from remote config by App Screens): the application context is
 * first wrapped by [withForcedNightMode] so its
 * `Configuration.uiMode` night bits carry the forced scheme BEFORE the DayNight [ContextThemeWrapper]
 * resolves `isLightTheme` over it, and the page boots in the right scheme rather than flipping at
 * first attach. The attach-time swap must then keep the scheme AND a DayNight theme
 * (`AppScreenHost` uses `withForcedNightModeTheme`). [forcedDark] == `null` (AUTO / no override) is
 * a pass-through — the application context flows unchanged, so the query resolves from the device
 * appearance exactly as before this parameter existed.
 *
 * For M2/M3 the WebView lives inside a single composition, so this factory is invoked once per
 * screen load.
 */
internal object AppScreenWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(appContext: Context, forcedDark: Boolean?): WebView {
        val themed = ContextThemeWrapper(
            // Force the night-mode bits onto the construction context BEFORE the DayNight theme
            // wrapper, so `prefers-color-scheme` resolves to the config override (pass-through for
            // AUTO/null — byte-identical to the pre-override behaviour).
            appContext.applicationContext.withForcedNightMode(forcedDark),
            android.R.style.Theme_DeviceDefault_DayNight
        )
        val webView = WebView(MutableContextWrapper(themed))
        log.d(
            "App Screen WebView created (forcedDark=$forcedDark, " +
                "uiMode=0x${Integer.toHexString(themed.resources.configuration.uiMode)})"
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            // No local file/content access: the document is remote HTML loaded over a base URL.
            allowFileAccess = false
            allowContentAccess = false

            mediaPlaybackRequiresUserGesture = false

            // Zoom is entirely disabled; App Screens are native-feeling surfaces, not documents.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Install the renderer-death client (M6). It MUST be present on every App Screen WebView:
        // its onRenderProcessGone returns true, without which a renderer crash kills the app
        // process. The navigator wires its handler once the owning session exists. A log-only
        // render-process (un)responsiveness client is added where the platform supports it.
        webView.webViewClient = AppScreenWebViewClient()
        AppScreenRenderProcessClient.installIfSupported(webView)

        // Transparent so the composable host's scheme-aware background shows through until reveal,
        // preventing a white flash in dark mode.
        webView.setBackgroundColor(Color.TRANSPARENT)

        // targetSdk 36 leaves algorithmic darkening off by default; the runtime handles its own
        // dark styling via prefers-color-scheme, so explicitly keep it off where the API exists.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }

        if (isDebuggable(appContext)) {
            WebView.setWebContentsDebuggingEnabled(true)
            log.d("App Screen WebView contents debugging enabled (debuggable build)")
        }

        return webView
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
