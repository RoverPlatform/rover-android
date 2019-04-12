package io.rover.core.ui.blocks.concerns.background

import io.rover.core.assets.AssetService
import io.rover.core.assets.ImageOptimizationService
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.share
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribe
import io.rover.core.streams.timeout
import io.rover.core.ui.PixelSize
import io.rover.core.ui.concerns.MeasuredSize
import io.rover.core.data.domain.Background
import io.rover.core.ui.asAndroidColor
import io.rover.core.ui.dpAsPx
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

class BackgroundViewModel(
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
