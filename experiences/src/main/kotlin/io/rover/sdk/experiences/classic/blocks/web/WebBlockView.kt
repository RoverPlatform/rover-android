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

package io.rover.sdk.experiences.classic.blocks.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.annotation.RequiresApi
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.classic.blocks.concerns.ViewComposition
import io.rover.sdk.experiences.classic.blocks.concerns.background.ViewBackground
import io.rover.sdk.experiences.classic.blocks.concerns.border.ViewBorder
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableView
import io.rover.sdk.experiences.classic.blocks.concerns.layout.ViewBlock
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding

internal class WebBlockView : WebView, LayoutableView<WebViewBlockViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    // mixins (TODO: injections)
    private val viewComposition = ViewComposition()

    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)
    private val viewWeb = ViewWeb(this)

    override var viewModelBinding: MeasuredBindableView.Binding<WebViewBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding
        viewWeb.viewModelBinding = binding
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

        requestDisallowInterceptTouchEvent((viewModelBinding?.viewModel?.scrollingEnabled) ?: true)
        return super.onTouchEvent(event)
    }
}
