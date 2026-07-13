/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.experiences.rich.compose.ui.utils

import android.app.Activity
import android.os.Looper
import android.view.Window
import androidx.core.view.WindowCompat
import io.rover.sdk.experiences.rich.compose.model.values.Appearance
import io.rover.sdk.experiences.rich.compose.model.values.StatusBarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemBarControllerTest {

    private lateinit var window: Window
    private lateinit var controller: SystemBarController

    @Before
    fun setUp() {
        // A real (Robolectric) Activity window so the controller drives the actual
        // WindowInsetsController and Window.statusBarColor, and posts to the main looper.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        window = activity.window
        controller = SystemBarController(window)
    }

    // The reference-counting and deferred-restore state machine is shared between the icon tint and
    // the legacy status-bar background colour, so most of these tests exercise it via the colour path
    // and assert on Window.statusBarColor, which is the most faithfully-shadowed observable.

    private fun setBaselineColor(color: Int) {
        @Suppress("DEPRECATION")
        window.statusBarColor = color
    }

    private fun statusBarColor(): Int {
        @Suppress("DEPRECATION")
        return window.statusBarColor
    }

    private fun appearanceLightStatusBars(): Boolean =
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars

    /** Advance past the controller's deferred-restore delay so any pending restore runs. */
    private fun elapseRestoreDelay() {
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
    }

    @Test
    fun applySetsStatusBarBackgroundColor() {
        setBaselineColor(HOST_COLOR)

        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)

        assertEquals(EXPERIENCE_COLOR, statusBarColor())
    }

    @Test
    fun applySetsDarkIconTintForDarkStyle() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT)

        // DARK style => dark icons => appearanceLightStatusBars == true.
        assertTrue(appearanceLightStatusBars())
    }

    @Test
    fun applySetsLightIconTintForLightStyle() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        controller.apply(SCREEN_A, StatusBarStyle.LIGHT, Appearance.LIGHT)

        // LIGHT style => light icons => appearanceLightStatusBars == false.
        assertFalse(appearanceLightStatusBars())
    }

    @Test
    fun releaseRestoresBaselineAfterDelay() {
        setBaselineColor(HOST_COLOR)
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)

        controller.release(SCREEN_A)

        // Restore is deferred, not synchronous.
        assertEquals(EXPERIENCE_COLOR, statusBarColor())

        elapseRestoreDelay()

        assertEquals(HOST_COLOR, statusBarColor())
    }

    @Test
    fun handoffDoesNotFlashBaseline() {
        // Reproduces the navigation flicker scenario: the outgoing screen releases (which schedules a
        // restore) before the incoming screen applies. The incoming apply must cancel the pending
        // restore so the host baseline is never shown.
        setBaselineColor(HOST_COLOR)
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)

        controller.release(SCREEN_A)
        controller.apply(SCREEN_B, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = OTHER_COLOR)

        elapseRestoreDelay()

        // Never reverted to HOST_COLOR; the incoming screen's colour stands.
        assertEquals(OTHER_COLOR, statusBarColor())
    }

    @Test
    fun baselineHeldWhileAnotherScreenStillActive() {
        setBaselineColor(HOST_COLOR)
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)
        controller.apply(SCREEN_B, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)

        controller.release(SCREEN_A)
        elapseRestoreDelay()

        // B is still active, so nothing is restored.
        assertEquals(EXPERIENCE_COLOR, statusBarColor())

        controller.release(SCREEN_B)
        elapseRestoreDelay()

        assertEquals(HOST_COLOR, statusBarColor())
    }

    @Test
    fun releasingNewestScreenReappliesPreviousScreenState() {
        setBaselineColor(HOST_COLOR)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        controller.apply(SCREEN_A, StatusBarStyle.LIGHT, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)
        controller.apply(SCREEN_B, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = OTHER_COLOR)

        assertEquals(OTHER_COLOR, statusBarColor())
        assertTrue(appearanceLightStatusBars())

        controller.release(SCREEN_B)
        elapseRestoreDelay()

        assertEquals(EXPERIENCE_COLOR, statusBarColor())
        assertFalse(appearanceLightStatusBars())

        controller.release(SCREEN_A)
        elapseRestoreDelay()

        assertEquals(HOST_COLOR, statusBarColor())
    }

    @Test
    fun releaseOfUnknownScreenIsNoOp() {
        setBaselineColor(HOST_COLOR)
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)

        controller.release("never-applied")
        elapseRestoreDelay()

        // The active screen A is untouched; no restore happened.
        assertEquals(EXPERIENCE_COLOR, statusBarColor())
    }

    @Test
    fun baselineCapturedOnceAcrossMultipleApplies() {
        setBaselineColor(HOST_COLOR)

        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)
        // Re-applying (e.g. style/colour change) must not capture the experience colour as the new
        // baseline to restore to.
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = OTHER_COLOR)

        controller.release(SCREEN_A)
        elapseRestoreDelay()

        assertEquals(HOST_COLOR, statusBarColor())
    }

    @Test
    fun tintRestoredToBaselineAfterRelease() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        controller.apply(SCREEN_A, StatusBarStyle.LIGHT, Appearance.LIGHT)
        // LIGHT style leaves appearanceLightStatusBars == false which equals the baseline, so flip to a
        // style that changes it to be observable.
        controller.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT)
        assertTrue(appearanceLightStatusBars())

        controller.release(SCREEN_A)
        elapseRestoreDelay()

        assertFalse(appearanceLightStatusBars())
    }

    @Test
    fun nullWindowIsNoOp() {
        val nullController = SystemBarController(null)

        // Should not throw.
        nullController.apply(SCREEN_A, StatusBarStyle.DARK, Appearance.LIGHT, legacyBackgroundColor = EXPERIENCE_COLOR)
        nullController.release(SCREEN_A)
    }

    private companion object {
        const val SCREEN_A = "screen-a"
        const val SCREEN_B = "screen-b"

        const val HOST_COLOR = 0xFF101010.toInt()
        const val EXPERIENCE_COLOR = 0xFF204060.toInt()
        const val OTHER_COLOR = 0xFF603020.toInt()
    }
}
