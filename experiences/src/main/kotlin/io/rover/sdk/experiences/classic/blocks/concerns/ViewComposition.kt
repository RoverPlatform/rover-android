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

package io.rover.sdk.experiences.classic.blocks.concerns

import android.graphics.Canvas
import android.view.View

/**
 * This allows the View* mixins to receive notification of certain important events and state
 * changes that are only exposed by Android with a template pattern and not a callback registration
 * pattern.
 */
internal class ViewComposition : ViewCompositionInterface {
    private val beforeDraws: MutableList<(Canvas) -> Unit> = mutableListOf()
    private val afterDraws: MutableList<(Canvas) -> Unit> = mutableListOf()
    private val onSizeChangedCallbacks: MutableList<(width: Int, height: Int, oldWidth: Int, oldHeight: Int) -> Unit> = mutableListOf()

    /**
     * Execute the given callback against the [Canvas] just before the view's main [View.draw]
     * pass would occur.
     */
    override fun registerBeforeDraw(stage: (Canvas) -> Unit) {
        beforeDraws.add(stage)
    }

    /**
     * Execute the given callback against the [Canvas] just after the view's main [View.draw]
     * pass has occurred.
     */
    override fun registerAfterDraw(stage: (Canvas) -> Unit) {
        afterDraws.add(stage)
    }

    override fun beforeDraw(canvas: Canvas) {
        // allow to inject behaviour before main view draw (here)
        beforeDraws.forEach { it(canvas) }
    }

    override fun afterDraw(canvas: Canvas) {
        // capture the background draw after the beforeDraws() are called (rather than before
        // like it is here)
        afterDraws.forEach { it(canvas) }
        // and allow to inject behaviour after main view draw (here)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        onSizeChangedCallbacks.forEach { it(w, h, oldw, oldh) }
    }
}

interface ViewCompositionInterface {
    /**
     * Execute the given callback against the [Canvas] just before the view's main [View.draw]
     * pass would occur.
     */
    fun registerBeforeDraw(stage: (Canvas) -> Unit)

    /**
     * Execute the given callback against the [Canvas] just after the view's main [View.draw]
     * pass has occurred.
     */
    fun registerAfterDraw(stage: (Canvas) -> Unit)

    // The following methods MUST be wired up!  If they are not, functionality in certain mixins
    // will fail to work properly.

    /**
     * In the view containing this mixin, implement the [View.onDraw] template method, and call this
     * method before calling super.
     */
    fun beforeDraw(canvas: Canvas)

    /**
     * In the view containing this mixin, implement the [View.onDraw] template method, and call this
     * method after calling super.
     */
    fun afterDraw(canvas: Canvas)

    /**
     * In the view containing this mixin, implement the [View.onSizeChanged] template method, and
     * call this method after calling super.
     */
    fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)
}
