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

package io.rover.sdk.experiences.appscreens.ui

import android.content.Context
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Harness B (design case (b)): proves a live font-scale change reaches an
 * already-attached WebView through LocalConfiguration invalidation ->
 * AndroidView.update -> textZoom assignment, in place — same instance, no
 * load. No navigator, no network, no document: textZoom is a WebSettings
 * property independent of content.
 */
class AppScreenWebViewHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Records load calls so the no-reload assertion is observable. */
    private class SpyWebView(context: Context) : WebView(context) {
        var loadCalls = 0

        override fun loadUrl(url: String) {
            loadCalls++
        }

        override fun loadUrl(url: String, additionalHttpHeaders: MutableMap<String, String>) {
            loadCalls++
        }

        override fun loadData(data: String, mimeType: String?, encoding: String?) {
            loadCalls++
        }

        override fun loadDataWithBaseURL(
            baseUrl: String?,
            data: String,
            mimeType: String?,
            encoding: String?,
            historyUrl: String?
        ) {
            loadCalls++
        }

        override fun reload() {
            loadCalls++
        }

        override fun postUrl(url: String, postData: ByteArray) {
            loadCalls++
        }
    }

    @Test
    fun liveFontScaleChangeUpdatesTextZoomInPlace() {
        lateinit var spy: SpyWebView
        lateinit var baseConfiguration: Configuration
        lateinit var neutralContext: Context
        composeRule.activityRule.scenario.onActivity { activity ->
            spy = SpyWebView(MutableContextWrapper(activity))
            // A distinct neutral context, mirroring the session contract
            // (AppScreenSession.appThemedContext is separate from the
            // wrapper): spy.context IS the MutableContextWrapper, and using
            // it as neutralContext would make onRelease set the wrapper's
            // base context to itself.
            neutralContext = activity.applicationContext
            baseConfiguration = Configuration(activity.resources.configuration)
        }

        // Fresh Configuration copy per value: Configuration is mutable, and
        // mutate-and-reassign of the same instance may not invalidate
        // composition.
        val configState = mutableStateOf(
            Configuration(baseConfiguration).apply { fontScale = 1.0f }
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configState.value) {
                AppScreenWebViewHost(
                    webView = spy,
                    attachContext = null,
                    neutralContext = neutralContext
                )
            }
        }
        composeRule.waitForIdle()

        lateinit var attachedParent: ViewParent
        var detachCount = 0
        composeRule.runOnUiThread {
            assertEquals(100, spy.settings.textZoom)
            attachedParent = spy.parent
            // Registered only after the initial attach has settled, so it
            // exclusively counts detaches provoked by the upcoming
            // font-scale change (not the initial composition's attach).
            assertTrue(spy.isAttachedToWindow)
            spy.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    detachCount++
                }
            })
        }

        configState.value = Configuration(baseConfiguration).apply { fontScale = 2.0f }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            // (a) textZoom followed the configuration change...
            assertEquals(200, spy.settings.textZoom)
            // (b) ...on the SAME attached instance: the view was never
            // detached from the window during the update (ruling out
            // removeView/addView churn that would destroy render state and
            // scroll position), and it remains attached to the same parent
            // at the same position...
            assertEquals(0, detachCount)
            assertSame(attachedParent, spy.parent)
            assertSame(spy, (spy.parent as ViewGroup).getChildAt(0))
            // (c) ...and nothing ever triggered a load.
            assertEquals(0, spy.loadCalls)
        }
    }
}
