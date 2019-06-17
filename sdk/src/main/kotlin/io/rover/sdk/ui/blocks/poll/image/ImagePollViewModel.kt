package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Bitmap
import io.rover.sdk.assets.AssetService
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.data.domain.Image
import io.rover.sdk.data.domain.ImagePollBlock
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.logging.log
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.filterNulls
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.onErrorReturn
import io.rover.sdk.ui.PixelSize
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.concerns.BindableViewModel
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.dpAsPx
import org.reactivestreams.Publisher

internal class ImagePollViewModel(
    override val imagePollBlock: ImagePollBlock,
    private val measurementService: MeasurementService,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    mainScheduler: Scheduler
) : ImagePollViewModelInterface {

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40
    }

    override fun intrinsicHeight(bounds: io.rover.sdk.ui.RectF): Float {
        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            imagePollBlock.question,
            imagePollBlock.questionStyle.font.getFontAppearance(imagePollBlock.questionStyle.color, imagePollBlock.questionStyle.textAlignment),
            bounds.width()
        )

        val horizontalSpacing =
            measurementService.snapToPixValue(imagePollBlock.optionStyle.horizontalSpacing)
        val optionTextHeight = measurementService.snapToPixValue(OPTION_TEXT_HEIGHT)
        val verticalSpacing =
            measurementService.snapToPixValue(imagePollBlock.optionStyle.verticalSpacing)

        val optionImageHeight = (bounds.width() - horizontalSpacing) / 2

        return when (imagePollBlock.options.size) {
            2 -> verticalSpacing + optionTextHeight + optionImageHeight + questionHeight
            else -> 2 * (verticalSpacing + optionTextHeight + optionImageHeight) + questionHeight
        }
    }

    override fun informImagePollOptionDimensions(measuredSize: MeasuredSize) {
        measurementsSubject.onNext(measuredSize)
    }

    private val measurementsSubject = PublishSubject<MeasuredSize>()

    private val imagesList: List<Image> = imagePollBlock.options.map { it.image }

    override val multiImageUpdates: Publisher<List<ImagePollViewModelInterface.ImageUpdate>> =
        measurementsSubject
            .flatMap { measuredSize -> Publishers.just(measuredSize) }
            .imagesFetchTransform()
            .observeOn(mainScheduler)

    private fun Publisher<MeasuredSize>.imagesFetchTransform(): Publisher<List<ImagePollViewModelInterface.ImageUpdate>> {
        return flatMap { measuredSize ->
            val optimizedImages = imagesList.map {
                val uriWithParameters = imageOptimizationService.optimizeImageBlock(
                    it,
                    imagePollBlock.optionStyle.border.width,
                    PixelSize(
                        measuredSize.width.dpAsPx(measuredSize.density),
                        measuredSize.height.dpAsPx(measuredSize.density)
                    ),
                    measuredSize.density
                )

                return@map assetService.imageByUrl(uriWithParameters.toURL())
                    .map { bitmap ->
                        ImagePollViewModelInterface.ImageUpdate(
                            bitmap
                        )
                    }
            }

            Publishers.combineLatest(optimizedImages) {
                it
            }
        }
    }
}

internal interface ImagePollViewModelInterface : BindableViewModel, Measurable {
    val imagePollBlock: ImagePollBlock

    /**
     * Subscribe to be informed of the images becoming ready.
     */
    val multiImageUpdates: Publisher<List<ImageUpdate>>

    data class ImageUpdate(val bitmap: Bitmap)

    /**
     * Inform the view model of the display geometry of the image view, so that it may
     * make an attempt to retrieve the images for display.
     *
     * Be sure to subscribe to [multiImageUpdates] first.
     */
    fun informImagePollOptionDimensions(
        measuredSize: MeasuredSize
    )
}