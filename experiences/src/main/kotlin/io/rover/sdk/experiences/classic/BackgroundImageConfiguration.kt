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

import android.graphics.Bitmap
import android.graphics.Shader

/**
 * Specifies how a view should properly display the given background image.
 *
 * The method these are specified with is a bit idiosyncratic on account of Android implementation
 * details and the combination of Drawables the view uses to achieve the effect.
 */
internal class BackgroundImageConfiguration(
    /**
     * Bounds in pixels, in *relative insets from their respective edges*.
     *
     * TODO: consider changing to not use Rect to better indicate that it is not a rectangle but an inset for each edge
     *
     * Our drawable is always set to FILL_XY, which means by specifying these insets you get
     * complete control over the aspect ratio, sizing, and positioning.  Note that this parameter
     * cannot be used to specify any sort of scaling for tiling, since the bottom/right bounds are
     * effectively undefined as the pattern repeats forever.  In that case, consider using using
     * [imageNativeDensity] to achieve a scale effect (although note that it is in terms of the
     * display DPI).
     *
     * (Note: we use this approach rather than just having a configurable gravity on the drawable
     * because that would not allow for aspect correct fit scaling.)
     */
    val insets: Rect,

    /**
     * An Android tiling mode.  For no tiling, set as null.
     */
    val tileMode: Shader.TileMode?,

    /**
     * This density value should be set on the bitmap with [Bitmap.setDensity] before drawing it
     * on an Android canvas.
     *
     * (Note: While the [BackgroundViewModel] itself could have applied this to the Bitmap, by
     * definition the view models are to avoid touching Android display API; the only reason that
     * the view model is dealing with the [Bitmap] type at all is that it's otherwise a pretty
     * suitable type shuttling the pixel data around, and also ensuring that it is warmed over into
     * GPU vram).
     */
    val imageNativeDensity: Int
)
