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

package io.rover.sdk.experiences.classic.blocks.concerns.border

import io.rover.sdk.core.data.domain.Border
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView

/**
 * Binds [BorderViewModelInterface] properties to that of a view.
 *
 * Borders can specify a border of arbitrary width, with optional rounded corners.
 */
internal interface ViewBorderInterface : MeasuredBindableView<BorderViewModelInterface>

/**
 * This interface is exposed by View Models that have support for a border (of arbitrary width and
 * possibly rounded with a radius).  Equivalent to the [Border] domain model interface.
 */
internal interface BorderViewModelInterface : BindableViewModel {
    /**
     * An Android color ARGB int of the border color.
     */
    val borderColor: Int

    /**
     * The rounded corner radius of the border in Dp.
     */
    val borderRadius: Int

    /**
     * The border width in Dp.
     */
    val borderWidth: Int

    companion object
}
