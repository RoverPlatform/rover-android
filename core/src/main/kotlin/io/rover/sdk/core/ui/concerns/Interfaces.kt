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

package io.rover.sdk.core.ui.concerns

import android.view.View

/**
 * A ViewModel that an appropriate Rover view can be bound to.
 */
interface BindableViewModel

/**
 * View models that wish to be informed early (when they are present on the screen but out of view)
 * of what their measured size is, so they can perhaps do some sort of asynchronous background
 * update behaviour.
 */
interface PrefetchAfterMeasure {
    fun measuredSizeReadyForPrefetch(
        measuredSize: MeasuredSize
    )
}

/**
 * Wraps a Rover Android [View] that can be bound to a [BindableViewModel].
 *
 * This is usually implemented by the views themselves, and [view] just returns `this`.  This is an
 * interface rather than an abstract [View] subclass in order to allow implementers to inherit from
 * various different [View] subclasses.
 */
interface BindableView<VM : BindableViewModel> {
    var viewModel: VM?

    val view: View
        get() = this as View
}

/**
 * Wraps a Rover Android [View] that can be bound to a [BindableViewModel].
 *
 * This is usually implemented by the views themselves, and [view] just returns `this`.  This is an
 * interface rather than an abstract [View] subclass in order to allow implementers to inherit from
 * various different [View] subclasses.
 *
 * This is a version of [BindableView] that is meant for use with Rover's internal layout system
 * that is used independently of the Android layout system.
 */
interface MeasuredBindableView<VM : BindableViewModel> {
    var viewModelBinding: Binding<VM>?

    val view: View
        get() = this as View

    data class Binding<out VM : BindableViewModel>(
        /**
         * The view model which should be bound to the view, and its contents/behaviour thus
         * displayed.
         */
        val viewModel: VM,

        /**
         * If relevant for this type of view model, the measured size for the content performed
         * during the layout pass.
         */
        val measuredSize: MeasuredSize? = null
    )
}

data class MeasuredSize(
    /**
     * Width, in dps.
     */
    val width: Float,

    /**
     * Height, in dps.
     */
    val height: Float,

    /**
     * The conversion factor between the dps and device-dependent pixels.
     */
    val density: Float
)
