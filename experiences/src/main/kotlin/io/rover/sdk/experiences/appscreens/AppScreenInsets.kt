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

import kotlin.math.roundToInt

/**
 * The safe-area insets, in CSS pixels, to publish to an App Screen document as the
 * `--rover-safe-area-inset-*` custom properties.
 *
 * The Android WebView renders edge-to-edge, so `env(safe-area-inset-*)` reports 0 and a page's top
 * padding collapses under the status bar. App Screen pages therefore pad with
 * `max(env(safe-area-inset-*), var(--rover-safe-area-inset-*, 0px))`; the native side computes the
 * real per-host insets and injects them here (see [AppScreenInsetsScript]).
 *
 * Values are whole CSS pixels (device pixels ÷ display density), which matches what the runtime's
 * CSS consumes. This type is a plain value with no Android dependency so the CSS-string builder can
 * be unit-tested off-device.
 *
 * `additionalTopDp` on [fromDevicePx] folds a caller-supplied band (in dp, which equals CSS px) into
 * the top edge only: it carries the floating overlay-capsule band (root affordance, back, or sheet
 * chrome) so pages lay out clear of the overlaid button, the Android counterpart of iOS's nav bar
 * contributing to the safe area (see `AppScreensRoot.OverlayAffordanceTopBand`).
 */
internal data class AppScreenInsets(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int
) {
    companion object {
        val ZERO = AppScreenInsets(0, 0, 0, 0)

        /**
         * Build an [AppScreenInsets] from raw device-pixel edges by converting to whole CSS pixels
         * (device px ÷ [density]), then adding [additionalTopDp] to the top edge only. Since CSS px
         * ≡ dp here, the dp band is added directly. Each edge is rounded to the nearest whole pixel.
         *
         * Kept free of Android/Compose types (plain Ints/Floats) so it is unit-testable off-device.
         */
        fun fromDevicePx(
            topPx: Int,
            rightPx: Int,
            bottomPx: Int,
            leftPx: Int,
            density: Float,
            additionalTopDp: Float = 0f
        ): AppScreenInsets = AppScreenInsets(
            top = (topPx / density + additionalTopDp).roundToInt(),
            right = (rightPx / density).roundToInt(),
            bottom = (bottomPx / density).roundToInt(),
            left = (leftPx / density).roundToInt()
        )
    }
}

/**
 * Builds the JavaScript that publishes an [AppScreenInsets] onto `document.documentElement` as the
 * four `--rover-safe-area-inset-*` custom properties.
 *
 * The emitted values are pure integers suffixed with `px`, so there is no string-escaping hazard and
 * no untrusted input crosses into the script — this is why the injection is delivered with a
 * fire-and-forget `evaluateJavascript`, distinct from the Promise-awaiting bridge down-calls. A
 * reload replaces the document and wipes these inline properties, so the script is re-run on every
 * (re)load and on every (re)attach.
 */
internal object AppScreenInsetsScript {

    private val EDGES = listOf("top", "right", "bottom", "left")

    /** The CSS custom-property script for [insets]. Idempotent; safe to run repeatedly. */
    fun build(insets: AppScreenInsets): String {
        val values = listOf(insets.top, insets.right, insets.bottom, insets.left)
        return EDGES.indices.joinToString(separator = "") { i ->
            "document.documentElement.style.setProperty(" +
                "'--rover-safe-area-inset-${EDGES[i]}','${values[i]}px');"
        }
    }
}
