/*
 * Copyright (c) 2025, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.experiences.rich.compose.ui

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.sameInstance
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In pluggable navigation mode, child screens are registered as destinations in the *host's*
 * NavHost, so their content composes outside the experience's provider subtree and the
 * experience-scoped composition locals do not reach them implicitly. [capturedExperienceLocals]
 * captures those locals where they are visible so registered destinations can re-provide them.
 *
 * These tests compose the re-provided block as a *sibling* of the provider subtree — the same
 * relationship a host NavHost destination has to the experience content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PluggableEnvironmentCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capturedLocalsReachAComposableOutsideTheProviderSubtree() {
        val mapping = FontLoader.TypeFaceMapping(mapOf("Druk-Bold" to Typeface.DEFAULT_BOLD))
        val assetContext = RemoteAssetContext(Uri.parse("https://example.com/experience"))
        val userInfo = { mapOf<String, Any>("name" to "Taylor") }
        val urlParams = mapOf("screenID" to "abc")
        val experienceUrl = Uri.parse("https://example.com/experience")

        var observedMapping: FontLoader.TypeFaceMapping? = null
        var observedAssetContext: AssetContext? = null
        var observedUserInfo: Map<String, Any>? = null
        var observedUrlParams: Map<String, String>? = null
        var observedExperienceUrl: Uri? = null

        composeRule.setContent {
            // A plain local rather than MutableState: children compose inline in composition
            // order, so the capture made in the provider subtree is visible to the sibling
            // below within the same pass, and no snapshot state is written during composition.
            var captured: Array<ProvidedValue<*>>? = null

            CompositionLocalProvider(
                Environment.LocalTypefaceMapping provides mapping,
                Environment.LocalAssetContext provides assetContext,
                Environment.LocalUserInfo provides userInfo,
                Environment.LocalUrlParameters provides urlParams,
                Environment.LocalExperienceUrl provides experienceUrl
            ) {
                captured = capturedExperienceLocals()
            }

            CompositionLocalProvider(*captured!!) {
                observedMapping = Environment.LocalTypefaceMapping.current
                observedAssetContext = Environment.LocalAssetContext.current
                observedUserInfo = Environment.LocalUserInfo.current?.invoke()
                observedUrlParams = Environment.LocalUrlParameters.current
                observedExperienceUrl = Environment.LocalExperienceUrl.current
            }
        }
        composeRule.waitForIdle()

        assertThat(observedMapping, sameInstance<FontLoader.TypeFaceMapping?>(mapping))
        assertThat(observedAssetContext, sameInstance<AssetContext?>(assetContext))
        assertThat(observedUserInfo, equalTo<Map<String, Any>?>(mapOf("name" to "Taylor")))
        assertThat(observedUrlParams, equalTo<Map<String, String>?>(urlParams))
        assertThat(observedExperienceUrl, equalTo<Uri?>(experienceUrl))
    }
}
