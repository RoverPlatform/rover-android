package io.rover.sdk.ui.blocks.concerns.background

import io.rover.sdk.assets.AssetService
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.share
import io.rover.sdk.streams.shareHotAndReplay
import io.rover.sdk.streams.subscribe
import io.rover.sdk.streams.timeout
import io.rover.sdk.ui.PixelSize
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.data.domain.Background
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.dpAsPx
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
