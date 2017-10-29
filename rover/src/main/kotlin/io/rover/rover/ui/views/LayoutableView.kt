package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.viewmodels.LayoutableViewModel

/**
 * An Android view, as thin as possible,
 *
 * TODO: factor the "composability" concerns out into a base class potench.
 */
abstract class LayoutableView<VM: LayoutableViewModel> : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    abstract var viewModel: VM?

    private val beforeDraws: MutableList<(Canvas) -> Unit> = mutableListOf()
    private val afterDraws: MutableList<(Canvas) -> Unit> = mutableListOf()
    private val onSizeChangedCallbacks: MutableList<(width: Int, height: Int, oldWidth: Int, oldHeight: Int) -> Unit> = mutableListOf()

    // TODO: port this to the new observer stuff in the Android support lib. if it fits

    /**
     * Execute the given callback against the [Canvas] just before the view's main [draw]
     * pass would occur.
     */
    fun registerBeforeDraw(stage: (Canvas) -> Unit) {
        beforeDraws.add(stage)
    }

    /**
     * Execute the given callback against the [Canvas] just after the view's main [draw]
     * pass has occurred.
     */
    fun registerAfterDraw(stage: (Canvas) -> Unit) {
        afterDraws.add(stage)
    }

    fun registerOnSizeChangedCallback(callback: (width: Int, height: Int, oldWidth: Int, oldHeight: Int) -> Unit ) {
        onSizeChangedCallbacks.add(callback)
    }

    override fun onDraw(canvas: Canvas) {
        // allow to inject behaviour before main view draw (here)
        beforeDraws.forEach { it(canvas) }
        // TODO: I may change this back to override draw() instead if it turns out we need to
        // capture the background draw after the beforeDraws() are called (rather than before
        // like it is here)
        super.onDraw(canvas)
        afterDraws.forEach { it(canvas) }
        // and allow to inject behaviour after main view draw (here)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChangedCallbacks.forEach { it(w, h, oldw, oldh) }
    }
}
