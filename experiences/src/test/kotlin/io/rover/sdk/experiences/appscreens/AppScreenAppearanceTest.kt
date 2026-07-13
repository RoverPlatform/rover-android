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
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.rover.sdk.core.data.config.CommHubColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the pure config → forced-scheme mapping. The tri-state is load-bearing: `null` means "no
 * override / follow the device", NOT "light", so AUTO and an absent value must both resolve to null.
 * No Android dependency.
 */
class ForcedDarkMappingTest {

    @Test
    fun `DARK forces dark`() {
        assertEquals(true, forcedDark(CommHubColorScheme.DARK))
    }

    @Test
    fun `LIGHT forces light`() {
        assertEquals(false, forcedDark(CommHubColorScheme.LIGHT))
    }

    @Test
    fun `AUTO follows the device (null)`() {
        assertNull(forcedDark(CommHubColorScheme.AUTO))
    }

    @Test
    fun `absent scheme follows the device (null)`() {
        assertNull(forcedDark(null))
    }
}

/**
 * Verifies [withForcedNightMode]'s uiMode bit surgery against a real [Configuration]. Runs under
 * Robolectric (already a testImplementation dependency of this module) for a live Android Context /
 * resource configuration; no UI is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WithForcedNightModeTest {

    private val baseContext: Context get() = ApplicationProvider.getApplicationContext()

    /** Build a context whose configuration carries a known uiMode (night bits + a type bit). */
    private fun contextWithUiMode(uiMode: Int): Context {
        val configuration = Configuration(baseContext.resources.configuration).apply {
            this.uiMode = uiMode
        }
        return baseContext.createConfigurationContext(configuration)
    }

    private fun Context.nightBits(): Int =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    @Test
    fun `null override is a pass-through returning the same context`() {
        val context = baseContext
        assertSame(context, context.withForcedNightMode(null))
    }

    @Test
    fun `forcedDark true sets the night-yes bits`() {
        val started = contextWithUiMode(
            Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
        )
        val forced = started.withForcedNightMode(true)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, forced.nightBits())
    }

    @Test
    fun `forcedDark false sets the night-no bits even when starting dark`() {
        val started = contextWithUiMode(
            Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
        )
        val forced = started.withForcedNightMode(false)
        assertEquals(Configuration.UI_MODE_NIGHT_NO, forced.nightBits())
    }

    @Test
    fun `forcing the night bits preserves the non-night uiMode bits`() {
        // A distinctive non-night uiMode type (television) must survive the mask-and-set.
        val started = contextWithUiMode(
            Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_NO
        )
        val forced = started.withForcedNightMode(true)
        val uiMode = forced.resources.configuration.uiMode
        assertEquals(Configuration.UI_MODE_NIGHT_YES, uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(
            Configuration.UI_MODE_TYPE_TELEVISION,
            uiMode and Configuration.UI_MODE_TYPE_MASK
        )
    }

    @Test
    fun `a forced context is a distinct instance from its source`() {
        val started = contextWithUiMode(Configuration.UI_MODE_NIGHT_NO)
        assertTrue(started !== started.withForcedNightMode(true))
        assertFalse(started === started.withForcedNightMode(false))
    }
}

/**
 * Verifies [withForcedNightModeTheme], the attach-time variant: unlike [withForcedNightMode] it
 * must keep a DayNight THEME layered over the forced configuration, because Chromium resolves
 * `prefers-color-scheme` through the WebView's current context and a bare configuration context's
 * default theme resolves light, beating the forced uiMode. Runs under Robolectric; no UI is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WithForcedNightModeThemeTest {

    private val baseContext: Context get() = ApplicationProvider.getApplicationContext()

    private fun Context.nightBits(): Int =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    @Test
    fun `null override is a pass-through returning the same context`() {
        assertSame(baseContext, baseContext.withForcedNightModeTheme(null))
    }

    @Test
    fun `forced context carries the forced night bits`() {
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            baseContext.withForcedNightModeTheme(true).nightBits()
        )
        assertEquals(
            Configuration.UI_MODE_NIGHT_NO,
            baseContext.withForcedNightModeTheme(false).nightBits()
        )
    }

    @Test
    fun `forced context is a theme wrapper over the source, preserving it as base`() {
        val themed = baseContext.withForcedNightModeTheme(true)
        assertTrue(themed is android.view.ContextThemeWrapper)
        assertSame(baseContext, (themed as android.view.ContextThemeWrapper).baseContext)
    }
}
