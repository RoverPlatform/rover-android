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

package io.rover.sdk.experiences.classic.layout.screen

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.*
import io.rover.sdk.experiences.RoverExperiencesClassic
import io.rover.sdk.experiences.classic.blocks.concerns.ViewComposition
import io.rover.sdk.experiences.classic.blocks.concerns.background.ViewBackground
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.PrefetchAfterMeasure
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.classic.pxAsDp
import io.rover.sdk.experiences.classic.toMeasuredSize
import org.reactivestreams.Publisher

internal class ScreenView : RecyclerView, MeasuredBindableView<ScreenViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)

    private val viewModelSubject =
        PublishSubject<MeasuredBindableView.Binding<ScreenViewModelInterface>>()
    private val vtoMeasuredSizeSubject = PublishSubject<MeasuredSize>()

    init {
        val rover = Rover.shared ?: throw RuntimeException("Rover Experience view layer not usable until Rover.initialize has been called (with ExperiencesAssembler included).")

        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        val viewModelBindingAndSize: Publisher<Pair<MeasuredBindableView.Binding<ScreenViewModelInterface>, MeasuredSize>> =
            Publishers.combineLatest(
                viewModelSubject,
                vtoMeasuredSizeSubject.distinctUntilChanged()
            ) { viewModelBinding: MeasuredBindableView.Binding<ScreenViewModelInterface>, measured: MeasuredSize ->
                Pair(viewModelBinding, measured)
            }

        viewModelBindingAndSize
            .androidLifecycleDispose(this)
            .subscribe { (viewModelBinding: MeasuredBindableView.Binding<ScreenViewModelInterface>, measuredSize: MeasuredSize) ->
                viewBackground.viewModelBinding = MeasuredBindableView.Binding(
                    viewModelBinding.viewModel,
                    measuredSize
                )
            }

        viewModelBindingAndSize
            .distinctUntilChanged { it.second.width }
            .androidLifecycleDispose(this)
            .subscribe { (viewModelBinding: MeasuredBindableView.Binding<ScreenViewModelInterface>, measuredSize: MeasuredSize) ->
                log.v("View model and view measurements now both ready: $viewModelBinding and $measuredSize")

                val layout = viewModelBinding.viewModel.render(measuredSize.width)

                val blockAndRowAdapter = rover.resolve(RoverExperiencesClassic::class.java)?.views?.blockAndRowRecyclerAdapter(
                    layout,
                    resources.displayMetrics
                ) ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")

                // set up the Experience layout manager for the RecyclerView.  Unlike a typical
                // RecyclerView layout manager, in our system our layout is indeed data, so the
                // layout manager needs the Screen view model.

                layoutManager = rover.resolve(RoverExperiencesClassic::class.java)?.views?.blockAndRowLayoutManager(
                    layout,
                    resources.displayMetrics
                ) ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")

                // and then setup the adapter itself.
                adapter = blockAndRowAdapter

                // and then iterate through all of the viewmodels that respond to PrefetchAfterMeasure
                // and induce them to greedily start fetching their needed assets.
                layout
                    .coordinatesAndViewModels
                    .filter { it.viewModel is PrefetchAfterMeasure }
                    .forEach { displayItem ->
                        (displayItem.viewModel as PrefetchAfterMeasure)
                            .measuredSizeReadyForPrefetch(
                                displayItem.position.toMeasuredSize(
                                    resources.displayMetrics.density
                                )
                            )
                    }
            }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ScreenViewModelInterface>? by ViewModelBinding { binding, _ ->
        // The binding lacks a measured size because ScreenView is embedded in a standard Android
        // layout (and not a Rover layout), and so we establish our own VTO above to discover it.
        if (binding != null) {
            viewModelSubject.onNext(binding)
        }
    }

    override fun draw(canvas: Canvas) {
        viewComposition.beforeDraw(canvas)
        super.draw(canvas)
        viewComposition.afterDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewComposition.onSizeChanged(w, h, oldw, oldh)

        // Workaround: The VTO is not always reliable (perhaps due to being embedded Compose
        // AndroidView), so also emit the size from here, too.  .distinctUntilChanged() above
        // ensures the potentially duplicate size calls are ignored.
        vtoMeasuredSizeSubject.onNext(
            MeasuredSize(w.pxAsDp(resources.displayMetrics), h.pxAsDp(resources.displayMetrics), resources.displayMetrics.density)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewModelBinding = null
        adapter = null
    }
}
