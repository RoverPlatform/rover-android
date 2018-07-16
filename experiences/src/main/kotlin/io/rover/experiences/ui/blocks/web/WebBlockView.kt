package io.rover.experiences.ui.blocks.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.AttributeSet
import android.webkit.WebView
import io.rover.core.logging.log
import android.view.MotionEvent
import io.rover.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

class WebBlockView : WebView, LayoutableView<WebViewBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    // mixins (TODO: injections)
    private val viewComposition = ViewComposition()

    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)
    private val viewWeb = ViewWeb(this)

    override var viewModel: BindableView.Binding<WebViewBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModel = binding
        viewBlock.viewModel = binding
        viewBackground.viewModel = binding
        viewWeb.viewModel = binding
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

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        // log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
    }

    /**
     * Overrides [onTouchEvent] in order to (optionally) prevent touch & drag scrolling of the
     * web view.  We suppress the ClickableViewAccessibility warning because that warning
     * is intended for usage of onTouchEvent to detect clicks.  There is no click equivalent of
     * touch & drag for scrolling.  We also disable/enable the scroll bars as part of the same
     * policy separately in [ViewWeb].
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: sadly this cannot be delegated readily to ViewWeb because it requires using this
        // override, so we'll ask the view model from here.  While I could teach ViewComposition
        // about TouchEvent, because handlers can consume events it is unclear

        requestDisallowInterceptTouchEvent((viewModel?.viewModel?.scrollingEnabled) ?: true)
        return super.onTouchEvent(event)
    }
}
