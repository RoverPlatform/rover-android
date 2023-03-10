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

package io.rover.sdk.experiences.classic.navigation

import android.content.Context
import android.util.AttributeSet
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.classic.layout.screen.ScreenView
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModelInterface
import io.rover.sdk.experiences.platform.whenNotNull

/**
 * Navigation behaviour between screens of an Experience.
 */
internal class NavigationView : FrameLayout, MeasuredBindableView<NavigationViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    /**
     * The current view, stored so we can yield an android state parcelable and thus enable
     * state restore.
     */
    private var activeView: ScreenView? = null

    private val viewCache: LruCache<ScreenViewModelInterface, ScreenView> = object : LruCache<ScreenViewModelInterface, ScreenView>(3) {
        override fun entryRemoved(evicted: Boolean, key: ScreenViewModelInterface?, oldValue: ScreenView?, newValue: ScreenView?) {
            removeView(oldValue)
        }
    }

    private fun getViewForScreenViewModel(screenViewModel: ScreenViewModelInterface): ScreenView {
        return viewCache[screenViewModel] ?: ScreenView(
            context
        ).apply {
            this@NavigationView.addView(this)
            this.visibility = View.GONE
            viewCache.put(screenViewModel, this)
            this.viewModelBinding = MeasuredBindableView.Binding(screenViewModel)
        }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<NavigationViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        val viewModel = binding?.viewModel
        viewModel?.screen?.androidLifecycleDispose(this)?.subscribe({ screenUpdate ->
            if (!screenUpdate.animate) {
                val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                activeView?.visibility = GONE
                newView.visibility = VISIBLE
                activeView = newView
            } else {
                if (screenUpdate.backwards) {
                    val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                    newView.bringToFront()
                    newView.visibility = GONE

                    val set = TransitionSet().apply {
                        addTransition(
                            Slide(
                                Gravity.START
                            ).addTarget(newView)
                        )
                        activeView.whenNotNull { activeView ->
                            addTransition(
                                Slide(
                                    Gravity.END
                                ).addTarget(activeView)
                            )
                        }
                    }

                    TransitionManager.beginDelayedTransition(this, set)
                    activeView.whenNotNull {
                        it.visibility = GONE
                    }
                    newView.visibility = VISIBLE

                    activeView = newView
                } else {
                    // forwards
                    val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                    newView.bringToFront()
                    newView.visibility = GONE

                    val set = TransitionSet().apply {
                        activeView.whenNotNull { activeView ->
                            addTransition(
                                Slide(
                                    Gravity.START
                                ).addTarget(activeView)
                            )
                        }
                        addTransition(
                            Slide(
                                Gravity.END
                            ).addTarget(newView)
                        )
                    }

                    TransitionManager.beginDelayedTransition(this, set)
                    newView.visibility = VISIBLE
                    activeView.whenNotNull { it.visibility = GONE }

                    activeView = newView
                }
            }
        }, { error -> throw error }, { subscription -> subscriptionCallback(subscription) })

        viewModel?.start()
    }
}
