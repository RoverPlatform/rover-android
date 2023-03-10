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

package io.rover.sdk.experiences.classic.blocks.concerns.background

import android.graphics.Bitmap
import io.rover.sdk.core.data.domain.Background
import io.rover.sdk.experiences.classic.BackgroundImageConfiguration
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.PrefetchAfterMeasure
import org.reactivestreams.Publisher

/**
 * Binds [BackgroundViewModelInterface] properties to that of a view.
 *
 * Backgrounds can specify a background colour or image.
 */
internal interface ViewBackgroundInterface : MeasuredBindableView<BackgroundViewModelInterface>

/**
 * This interface is exposed by View Models that have support for a background.  Equivalent to
 * the [Background] domain model interface.
 */
internal interface BackgroundViewModelInterface : BindableViewModel, PrefetchAfterMeasure {
    val backgroundColor: Int

    /**
     * Subscribe to be informed of the image becoming ready.
     */
    val backgroundUpdates: Publisher<BackgroundUpdate>

    data class BackgroundUpdate(
        val bitmap: Bitmap,
        val fadeIn: Boolean,
        val backgroundImageConfiguration: BackgroundImageConfiguration
    )

    /**
     * Inform the view model of the display geometry of the image view, so that it may
     * make an attempt to retrieve the image for display.
     *
     * Be sure to subscribe to [imageUpdates] first.
     */
    fun informDimensions(
        measuredSize: MeasuredSize
    )
}
