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

package io.rover.sdk.experiences.classic

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.classic.navigation.NavigationView
import io.rover.sdk.experiences.classic.navigation.NavigationViewModelInterface

/**
 * Embed this view to include a Rover Experience in a layout.
 *
 * In order to display an Experience, instantiate [NavigationViewModelInterface] and set it to
 * [viewModelBinding].
 */
internal class RenderClassicExperienceView : CoordinatorLayout, MeasuredBindableView<NavigationViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override var viewModelBinding: MeasuredBindableView.Binding<NavigationViewModelInterface>? by ViewModelBinding(rebindingAllowed = false) { binding, subscriptionCallback ->
        val viewModel = binding?.viewModel

        navigationView.viewModelBinding = null

        if (viewModel != null) {
            navigationView.viewModelBinding = MeasuredBindableView.Binding(
                viewModel
            )
        }
    }

    private val navigationView: NavigationView = NavigationView(context)

    init {
        addView(
            navigationView
        )

        (navigationView.layoutParams as LayoutParams).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }
    }
}
