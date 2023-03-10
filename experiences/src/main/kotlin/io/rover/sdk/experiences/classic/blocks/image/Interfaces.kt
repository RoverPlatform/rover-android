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

package io.rover.sdk.experiences.classic.blocks.image

import android.graphics.Bitmap
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.classic.blocks.concerns.layout.Measurable
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.PrefetchAfterMeasure
import org.reactivestreams.Publisher

// ViewImage mixin is binding against ImageBlockViewModelInterface instead of
// ImageViewModelInterface in order to discover the block's opacity for use in an animation.  This
// is a hack and should be solved properly.
internal interface ViewImageInterface : MeasuredBindableView<ImageBlockViewModelInterface>

internal interface ImageViewModelInterface : Measurable, BindableViewModel, PrefetchAfterMeasure {
    /**
     * Subscribe to be informed of the image becoming ready.
     */
    val imageUpdates: Publisher<ImageUpdate>

    val isDecorative: Boolean

    val accessibilityLabel: String?

    data class ImageUpdate(
        val bitmap: Bitmap,
        val fadeIn: Boolean
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

internal interface ImageBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    ImageViewModelInterface
