package io.rover.sdk.ui.layout.screen

import android.content.Context
import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import io.rover.sdk.Rover
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.distinctUntilChanged
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.PrefetchAfterMeasure
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.ui.pxAsDp
import io.rover.sdk.ui.toMeasuredSize
import org.reactivestreams.Publisher
import java.lang.RuntimeException

internal class ScreenView : RecyclerView, MeasuredBindableView<ScreenViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)

    private val viewModelSubject =
        PublishSubject<MeasuredBindableView.Binding<ScreenViewModelInterface>>()
    private val vtoMeasuredSizeSubject = PublishSubject<MeasuredSize>()

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        viewTreeObserver.addOnGlobalLayoutListener {
            vtoMeasuredSizeSubject.onNext(
                MeasuredSize(
                    width.pxAsDp(this@ScreenView.context.resources.displayMetrics),
                    height.pxAsDp(this@ScreenView.context.resources.displayMetrics),
                    resources.displayMetrics.density
                )
            )
        }

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

                val blockAndRowAdapter = Rover.shared?.views?.blockAndRowRecyclerAdapter(
                    layout,
                    resources.displayMetrics
                )
                    ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")

                // set up the Experience layout manager for the RecyclerView.  Unlike a typical
                // RecyclerView layout manager, in our system our layout is indeed data, so the
                // layout manager needs the Screen view model.
                layoutManager =
                    Rover.shared?.views?.blockAndRowLayoutManager(layout, resources.displayMetrics)
                        ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")

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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewModelBinding = null
        adapter = null
    }
}
