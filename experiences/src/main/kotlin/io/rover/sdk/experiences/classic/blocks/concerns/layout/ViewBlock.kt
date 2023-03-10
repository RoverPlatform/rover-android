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

package io.rover.sdk.experiences.classic.blocks.concerns.layout

import android.view.MotionEvent
import android.view.View
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.classic.dpAsPx

internal class ViewBlock(
    override val view: View
) : ViewBlockInterface {
    override var viewModelBinding: MeasuredBindableView.Binding<BlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        val viewModel = binding?.viewModel

        val displayMetrics = view.resources.displayMetrics

        view.setOnClickListener { viewModel?.click() }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> viewModel?.touched()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel?.released()
            }

            false
        }

        if (viewModel != null) {
            view.setPaddingRelative(
                (viewModel.padding.left).dpAsPx(displayMetrics),
                (viewModel.padding.top).dpAsPx(displayMetrics),
                (viewModel.padding.right).dpAsPx(displayMetrics),
                (viewModel.padding.bottom).dpAsPx(displayMetrics)
            )

            view.alpha = viewModel.opacity

            view.isClickable = viewModel.isClickable

            // TODO: figure out how to set a ripple drawable for clickable blocks in a way that
            // works across different view types?
        } else {
            view.setPaddingRelative(0, 0, 0, 0)
        }
    }
}
