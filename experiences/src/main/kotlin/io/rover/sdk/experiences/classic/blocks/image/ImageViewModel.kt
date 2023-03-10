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

package io.rover.sdk.experiences.classic.blocks.image

import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.Image
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.*
import io.rover.sdk.experiences.classic.PixelSize
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.assets.AssetService
import io.rover.sdk.experiences.classic.assets.ImageOptimizationService
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.dpAsPx
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

internal class ImageViewModel(
    private val image: Image,
    private val block: Block,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    mainScheduler: Scheduler
) : ImageViewModelInterface {

    override fun informDimensions(measuredSize: MeasuredSize) {
        measurementsSubject.onNext(measuredSize)
    }

    override fun measuredSizeReadyForPrefetch(measuredSize: MeasuredSize) {
        prefetchMeasurementsSubject.onNext(measuredSize)
    }

    private val prefetchMeasurementsSubject = PublishSubject<MeasuredSize>()
    private val measurementsSubject = PublishSubject<MeasuredSize>()

    private var fadeInNeeded = false

    override val imageUpdates: Publisher<ImageViewModelInterface.ImageUpdate> = Publishers.merge(
        prefetchMeasurementsSubject.imageFetchTransform(),
        measurementsSubject
            .flatMap {
                Publishers.just(it)
                    .imageFetchTransform()
                    .share()
                    .apply {
                        timeout(50, TimeUnit.MILLISECONDS)
                            .subscribe(
                                { },
                                { error ->
                                    log.v("Fade in needed, because $error")
                                    fadeInNeeded = true
                                }
                            )
                    }
            }
    ).shareHotAndReplay(0).observeOn(mainScheduler) // shareHot because this chain is responsible for side-effect of pre-warming cache, even before subscribed.

    override val isDecorative: Boolean = image.isDecorative

    override val accessibilityLabel: String? = image.accessibilityLabel

    private fun Publisher<MeasuredSize>.imageFetchTransform(): Publisher<ImageViewModelInterface.ImageUpdate> {
        return flatMap { measuredSize ->
            val uriWithParameters = imageOptimizationService.optimizeImageBlock(
                image,
                block.border.width,
                PixelSize(
                    measuredSize.width.dpAsPx(measuredSize.density),
                    measuredSize.height.dpAsPx(measuredSize.density)
                ),
                measuredSize.density
            )

            // so if item does not appear within a threshold of time then turn on a fade-in bit?
            assetService.imageByUrl(uriWithParameters.toURL())
                .map { bitmap ->
                    ImageViewModelInterface.ImageUpdate(
                        bitmap,
                        fadeInNeeded
                    )
                }.onErrorReturn { error ->
                    log.w("Problem fetching image: $error, ignoring.")
                    null
                }.filterNulls()
        }
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        // get aspect ratio of image and use it to calculate the height needed to accommodate
        // the image at its correct aspect ratio given the width
        val heightToWidthRatio = image.height.toFloat() / image.width.toFloat()
        return bounds.width() * heightToWidthRatio
    }
}
