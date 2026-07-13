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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure CSS custom-property script builder that publishes safe-area insets to an App
 * Screen document. No Android dependency; the values are whole CSS pixels.
 */
class AppScreenInsetsTest {

    @Test
    fun `builds one setProperty per edge with px suffix`() {
        val script = AppScreenInsetsScript.build(AppScreenInsets(top = 47, right = 0, bottom = 24, left = 0))

        assertEquals(
            "document.documentElement.style.setProperty('--rover-safe-area-inset-top','47px');" +
                "document.documentElement.style.setProperty('--rover-safe-area-inset-right','0px');" +
                "document.documentElement.style.setProperty('--rover-safe-area-inset-bottom','24px');" +
                "document.documentElement.style.setProperty('--rover-safe-area-inset-left','0px');",
            script
        )
    }

    @Test
    fun `zero insets still emit all four properties as 0px`() {
        val script = AppScreenInsetsScript.build(AppScreenInsets.ZERO)

        listOf("top", "right", "bottom", "left").forEach { edge ->
            assertTrue(
                "expected --rover-safe-area-inset-$edge set to 0px",
                script.contains("'--rover-safe-area-inset-$edge','0px'")
            )
        }
    }

    @Test
    fun `landscape cutout insets carry left and right`() {
        val script = AppScreenInsetsScript.build(AppScreenInsets(top = 0, right = 44, bottom = 0, left = 44))

        assertTrue(script.contains("'--rover-safe-area-inset-left','44px'"))
        assertTrue(script.contains("'--rover-safe-area-inset-right','44px'"))
    }

    @Test
    fun `ZERO is the additive identity value`() {
        assertEquals(AppScreenInsets(0, 0, 0, 0), AppScreenInsets.ZERO)
    }

    @Test
    fun `fromDevicePx with zero additional top converts each edge device px to css px`() {
        val insets = AppScreenInsets.fromDevicePx(
            topPx = 94, rightPx = 88, bottomPx = 48, leftPx = 0,
            density = 2f, additionalTopDp = 0f
        )

        assertEquals(AppScreenInsets(top = 47, right = 44, bottom = 24, left = 0), insets)
    }

    @Test
    fun `fromDevicePx adds additional top to the top edge only`() {
        val insets = AppScreenInsets.fromDevicePx(
            topPx = 94, rightPx = 88, bottomPx = 48, leftPx = 0,
            density = 2f, additionalTopDp = 60f
        )

        // top: 94/2 = 47, plus a 60dp additional band = 107; other edges unchanged.
        assertEquals(AppScreenInsets(top = 107, right = 44, bottom = 24, left = 0), insets)
    }

    @Test
    fun `fromDevicePx rounds each edge at a non-integer density`() {
        val insets = AppScreenInsets.fromDevicePx(
            topPx = 110, rightPx = 115, bottomPx = 63, leftPx = 0,
            density = 2.625f, additionalTopDp = 60f
        )

        // 110/2.625 = 41.90.. + 60 = 101.90 → 102; 115/2.625 = 43.81 → 44; 63/2.625 = 24.0 → 24.
        assertEquals(AppScreenInsets(top = 102, right = 44, bottom = 24, left = 0), insets)
    }

    @Test
    fun `script from fromDevicePx reflects the summed top inset`() {
        val insets = AppScreenInsets.fromDevicePx(
            topPx = 94, rightPx = 0, bottomPx = 48, leftPx = 0,
            density = 2f, additionalTopDp = 60f
        )

        val script = AppScreenInsetsScript.build(insets)

        assertTrue(script.contains("'--rover-safe-area-inset-top','107px'"))
        assertTrue(script.contains("'--rover-safe-area-inset-bottom','24px'"))
    }
}
