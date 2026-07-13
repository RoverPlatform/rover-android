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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the pure top-inset fold that keeps an App Screen document's layout stable across push/pop:
 * an entry folds in the overlay chrome band whenever it carries a capsule, keyed on the chrome being
 * PROVIDED (role + presence) rather than on any button's momentary visibility. No Android dependency;
 * the value is [Dp] arithmetic.
 */
class AppScreenEntryTopInsetTest {

    @Test
    fun `pushed entry always folds the overlay band for its back button`() {
        // A pushed entry carries the back button regardless of host chrome or dismissability.
        assertEquals(
            OverlayAffordanceTopBand,
            appScreenEntryTopInset(isRootEntry = false, hasHostAffordance = false, isDismissable = false)
        )
    }

    @Test
    fun `bare root entry folds nothing`() {
        // Root with neither a host affordance nor a dismiss handler shows no chrome, so no band.
        assertEquals(
            0.dp,
            appScreenEntryTopInset(isRootEntry = true, hasHostAffordance = false, isDismissable = false)
        )
    }

    @Test
    fun `root entry with a host affordance folds the band`() {
        assertEquals(
            OverlayAffordanceTopBand,
            appScreenEntryTopInset(isRootEntry = true, hasHostAffordance = true, isDismissable = false)
        )
    }

    @Test
    fun `dismissable root entry folds the band even without a host affordance`() {
        // The full-screen close (✕) button occupies the same band as the host affordance.
        assertEquals(
            OverlayAffordanceTopBand,
            appScreenEntryTopInset(isRootEntry = true, hasHostAffordance = false, isDismissable = true)
        )
    }
}
