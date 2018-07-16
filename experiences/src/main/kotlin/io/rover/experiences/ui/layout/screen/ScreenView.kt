package io.rover.experiences.ui.layout.screen

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import io.rover.experiences.ui.layout.BlockAndRowLayoutManager
import io.rover.experiences.ui.layout.BlockAndRowRecyclerAdapter
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.distinctUntilChanged
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.MeasuredSize
import io.rover.core.ui.concerns.PrefetchAfterMeasure
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.ui.concerns.toMeasuredSize
import io.rover.core.ui.pxAsDp
import org.reactivestreams.Publisher

class ScreenView : RecyclerView, BindableView<ScreenViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)

    private val viewModelSubject = PublishSubject<BindableView.Binding<ScreenViewModelInterface>>()
    private val vtoMeasuredSizeSubject = PublishSubject<MeasuredSize>()

    init {
        viewTreeObserver.addOnGlobalLayoutListener {
            vtoMeasuredSizeSubject.onNext(
                MeasuredSize(
                    width.pxAsDp(this@ScreenView.context.resources.displayMetrics),
                    height.pxAsDp(this@ScreenView.context.resources.displayMetrics),
                    resources.displayMetrics.density
                )
            )
        }

        val combined: Publisher<Pair<BindableView.Binding<ScreenViewModelInterface>, MeasuredSize>> = Publishers.combineLatest(
            viewModelSubject,
            vtoMeasuredSizeSubject.distinctUntilChanged()
        ) { viewModelBinding: BindableView.Binding<ScreenViewModelInterface>, measured: MeasuredSize ->
            Pair(viewModelBinding, measured)
        }

        combined.subscribe { (viewModelBinding: BindableView.Binding<ScreenViewModelInterface>, measuredSize: MeasuredSize) ->
            log.v("View model and view measurements now both ready: $viewModelBinding and $measuredSize")
            viewBackground.viewModel = BindableView.Binding(
                viewModelBinding.viewModel,
                measuredSize
            )
            val layout = viewModelBinding.viewModel.render(measuredSize.width)

            val blockAndRowAdapter = Rover.sharedInstance.resolve(
                BlockAndRowRecyclerAdapter::class.java, null, layout, resources.displayMetrics
            )!!

            // set up the Experience layout manager for the RecyclerView.  Unlike a typical
            // RecyclerView layout manager, in our system our layout is indeed data, so the
            // layout manager needs the Screen view model.
            layoutManager = Rover.sharedInstance.resolve(
                BlockAndRowLayoutManager::class.java,
                null,
                layout,
                resources.displayMetrics
            )!!

            // and then setup the adapter itself.
            adapter = blockAndRowAdapter

            // and then iterate through all of the viewmodels that respond to PrefetchAfterMeasure
            // and induce them to greedily start fetching their needed assets.
            layout
                .coordinatesAndViewModels
                .filter { it.viewModel is PrefetchAfterMeasure }
                .forEach { displayItem ->
                    (displayItem.viewModel as PrefetchAfterMeasure)
                        .measuredSizeReadyForPrefetch(displayItem.position.toMeasuredSize(
                            resources.displayMetrics.density
                        ))
            }
        }
    }

    override var viewModel: BindableView.Binding<ScreenViewModelInterface>? by ViewModelBinding { binding, _ ->
        // The binding lacks a measured size because ScreenView is embedded in a standard Android
        // layout (and not a Rover layout), and so we establish our own VTO above to discover it.
        if(binding != null) {
            viewModelSubject.onNext(binding)
        }
    }

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}
