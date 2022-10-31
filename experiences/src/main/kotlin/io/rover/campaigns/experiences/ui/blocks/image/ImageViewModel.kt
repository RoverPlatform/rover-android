package io.rover.campaigns.experiences.ui.blocks.image

import io.rover.campaigns.experiences.assets.AssetService
import io.rover.campaigns.experiences.assets.ImageOptimizationService
import io.rover.campaigns.experiences.logging.log
import io.rover.campaigns.experiences.streams.PublishSubject
import io.rover.campaigns.experiences.streams.Publishers
import io.rover.campaigns.experiences.streams.Scheduler
import io.rover.campaigns.experiences.streams.filterNulls
import io.rover.campaigns.experiences.streams.flatMap
import io.rover.campaigns.experiences.streams.map
import io.rover.campaigns.experiences.streams.observeOn
import io.rover.campaigns.experiences.streams.onErrorReturn
import io.rover.campaigns.experiences.streams.share
import io.rover.campaigns.experiences.streams.shareHotAndReplay
import io.rover.campaigns.experiences.streams.subscribe
import io.rover.campaigns.experiences.streams.timeout
import io.rover.campaigns.experiences.ui.PixelSize
import io.rover.campaigns.experiences.ui.concerns.MeasuredSize
import io.rover.campaigns.experiences.data.domain.Block
import io.rover.campaigns.experiences.data.domain.Image
import io.rover.campaigns.experiences.ui.RectF
import io.rover.campaigns.experiences.ui.dpAsPx
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
