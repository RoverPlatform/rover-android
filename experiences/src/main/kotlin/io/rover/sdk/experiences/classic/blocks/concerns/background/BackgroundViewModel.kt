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

package io.rover.sdk.experiences.classic.blocks.concerns.background

import io.rover.sdk.core.data.domain.Background
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.*
import io.rover.sdk.experiences.classic.PixelSize
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.classic.assets.AssetService
import io.rover.sdk.experiences.classic.assets.ImageOptimizationService
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.dpAsPx
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

internal class BackgroundViewModel(
    private val background: Background,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    mainScheduler: Scheduler
) : BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.color.asAndroidColor()

    override fun informDimensions(measuredSize: MeasuredSize) {
        measurementsSubject.onNext(measuredSize)
    }

    override fun measuredSizeReadyForPrefetch(measuredSize: MeasuredSize) {
        prefetchMeasurementsSubject.onNext(measuredSize)
    }

    private val prefetchMeasurementsSubject = PublishSubject<MeasuredSize>()
    private val measurementsSubject = PublishSubject<MeasuredSize>()
    private var fadeInNeeded = false

    override val backgroundUpdates: Publisher<BackgroundViewModelInterface.BackgroundUpdate> = Publishers.merge(
        prefetchMeasurementsSubject.imageFetchTransform(),
        measurementsSubject
            .flatMap {
                Publishers.just(it)
                    .imageFetchTransform()
                    .share().apply {
                        // as a side-effect, register a subscriber right away that will monitor for timeouts
                        // (but only on asset requests being emitted for view dimensions not for prefetch
                        // dimensions)
                        if (background.image != null) {
                            timeout(50, TimeUnit.MILLISECONDS)
                                .subscribe(
                                    {},
                                    { error ->
                                        log.v("Fade in needed, because $error")
                                        fadeInNeeded = true
                                    }
                                )
                        }
                    }
            }
    ).shareHotAndReplay(0).observeOn(mainScheduler) // shareHot because this chain is responsible for side-effect of pre-warming cache, even before subscribed.

    private fun Publisher<MeasuredSize>.imageFetchTransform(): Publisher<BackgroundViewModelInterface.BackgroundUpdate> {
        return flatMap { measuredSize ->
            val optimizedImage = imageOptimizationService.optimizeImageBackground(
                background,
                PixelSize(
                    measuredSize.width.dpAsPx(measuredSize.density),
                    measuredSize.height.dpAsPx(measuredSize.density)
                ),
                measuredSize.density
            ) ?: return@flatMap Publishers.empty<BackgroundViewModelInterface.BackgroundUpdate>()

            assetService.imageByUrl(optimizedImage.uri.toURL()).map { bitmap ->
                BackgroundViewModelInterface.BackgroundUpdate(
                    bitmap,
                    fadeInNeeded,
                    optimizedImage.imageConfiguration
                )
            }
        }
    }
}
