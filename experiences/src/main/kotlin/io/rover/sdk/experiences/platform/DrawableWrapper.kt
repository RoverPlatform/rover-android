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

package io.rover.sdk.experiences.platform

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.drawable.DrawableCompat

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Drawable which delegates all calls to its wrapped [Drawable].
 *
 * The wrapped [Drawable] *must* be fully released from any [View]
 * before wrapping, otherwise internal [Drawable.Callback] may be dropped.
 *
 * Originally copied from Android Appcompat Support Library version 26.1.0.
 *
 * @hide
 */
internal open class DrawableWrapper(drawable: Drawable) : Drawable(), Drawable.Callback {

    private var wrappedDrawable: Drawable? = null
        set(drawable) {
            if (wrappedDrawable != null) {
                wrappedDrawable!!.callback = null
            }

            field = drawable

            if (drawable != null) {
                drawable.callback = this
            }
        }

    init {
        wrappedDrawable = drawable
    }

    override fun draw(canvas: Canvas) {
        wrappedDrawable!!.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        wrappedDrawable!!.bounds = bounds
    }

    override fun setChangingConfigurations(configs: Int) {
        wrappedDrawable!!.changingConfigurations = configs
    }

    override fun getChangingConfigurations(): Int {
        return wrappedDrawable!!.changingConfigurations
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setDither(dither: Boolean) {
        @Suppress("DEPRECATION")
        wrappedDrawable!!.setDither(dither)
    }

    override fun setFilterBitmap(filter: Boolean) {
        wrappedDrawable!!.isFilterBitmap = filter
    }

    override fun setAlpha(alpha: Int) {
        wrappedDrawable!!.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        wrappedDrawable!!.colorFilter = cf
    }

    override fun isStateful(): Boolean {
        return wrappedDrawable!!.isStateful
    }

    override fun setState(stateSet: IntArray): Boolean {
        return wrappedDrawable!!.setState(stateSet)
    }

    override fun getState(): IntArray {
        return wrappedDrawable!!.state
    }

    override fun jumpToCurrentState() {
        wrappedDrawable!!.jumpToCurrentState()
    }

    override fun getCurrent(): Drawable {
        return wrappedDrawable!!.current
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart) || wrappedDrawable!!.setVisible(visible, restart)
    }

    override fun getOpacity(): Int {
        return wrappedDrawable!!.opacity
    }

    override fun getTransparentRegion(): Region? {
        return wrappedDrawable!!.transparentRegion
    }

    override fun getIntrinsicWidth(): Int {
        return wrappedDrawable!!.intrinsicWidth
    }

    override fun getIntrinsicHeight(): Int {
        return wrappedDrawable!!.intrinsicHeight
    }

    override fun getMinimumWidth(): Int {
        return wrappedDrawable!!.minimumWidth
    }

    override fun getMinimumHeight(): Int {
        return wrappedDrawable!!.minimumHeight
    }

    override fun getPadding(padding: Rect): Boolean {
        return wrappedDrawable!!.getPadding(padding)
    }

    /**
     * {@inheritDoc}
     */
    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    /**
     * {@inheritDoc}
     */
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    /**
     * {@inheritDoc}
     */
    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    override fun onLevelChange(level: Int): Boolean {
        return wrappedDrawable!!.setLevel(level)
    }

    override fun setAutoMirrored(mirrored: Boolean) {
        DrawableCompat.setAutoMirrored(wrappedDrawable!!, mirrored)
    }

    override fun isAutoMirrored(): Boolean {
        return DrawableCompat.isAutoMirrored(wrappedDrawable!!)
    }

    override fun setTint(tint: Int) {
        DrawableCompat.setTint(wrappedDrawable!!, tint)
    }

    override fun setTintList(tint: ColorStateList?) {
        DrawableCompat.setTintList(wrappedDrawable!!, tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (tintMode != null) {
            DrawableCompat.setTintMode(wrappedDrawable!!, tintMode)
        }
    }

    override fun setHotspot(x: Float, y: Float) {
        DrawableCompat.setHotspot(wrappedDrawable!!, x, y)
    }

    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
        DrawableCompat.setHotspotBounds(wrappedDrawable!!, left, top, right, bottom)
    }
}
