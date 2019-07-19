package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Bitmap
import io.rover.sdk.assets.AssetService
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.data.domain.Image
import io.rover.sdk.data.domain.ImagePoll
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.Timestamped
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.timestamp
import io.rover.sdk.ui.PixelSize
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.poll.text.VotingState
import io.rover.sdk.ui.concerns.BindableViewModel
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.dpAsPx
import org.reactivestreams.Publisher

internal class ImagePollViewModel(
    override val imagePoll: ImagePoll,
    private val measurementService: MeasurementService,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    mainScheduler: Scheduler
) : ImagePollViewModelInterface {

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40
        private const val IMAGE_FADE_IN_MINIMUM_TIME = 50L
    }

    override fun intrinsicHeight(bounds: io.rover.sdk.ui.RectF): Float {
        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            imagePoll.question.rawValue,
            imagePoll.question.font.getFontAppearance(imagePoll.question.color, imagePoll.question.alignment),
            bounds.width()
        )

        val horizontalSpacing =
            measurementService.snapToPixValue(imagePoll.options[1].leftMargin)
        val optionTextHeight = measurementService.snapToPixValue(OPTION_TEXT_HEIGHT)
        val verticalSpacing =
            measurementService.snapToPixValue(imagePoll.options.first().topMargin)

        val optionImageHeight = (bounds.width() - horizontalSpacing) / 2

        return when (imagePoll.options.size) {
            2 -> verticalSpacing + optionTextHeight + optionImageHeight + questionHeight
            else -> 2 * (verticalSpacing + optionTextHeight + optionImageHeight) + questionHeight
        }
    }

    override fun informImagePollOptionDimensions(measuredSize: MeasuredSize) {
        measurementsSubject.onNext(measuredSize)
    }

    private val measurementsSubject = PublishSubject<MeasuredSize>()

    private val imagesList: List<Image> = imagePoll.options.mapNotNull { it.image }

    override fun castVote(selectedOption: Int) {
        // TODO: Add voting logic
        setResultsState(selectedOption, listOf(20, 42, 15, 18))
    }

    override val votingState = PublishSubject<VotingState>()

    private fun setResultsState(selectedOption: Int, votingShare: List<Int>) {
        votingState.onNext(VotingState.Results(selectedOption, votingShare))
    }

    override val multiImageUpdates: Publisher<List<ImagePollViewModelInterface.ImageUpdate>> =
        measurementsSubject
            .timestamp()
            .imagesFetchTransform()
            .observeOn(mainScheduler)

    private fun Publisher<Timestamped<MeasuredSize>>.imagesFetchTransform(): Publisher<List<ImagePollViewModelInterface.ImageUpdate>> {
        return flatMap { (timestampMillis, measuredSize) ->
            val optimizedImages = imagesList.map {
                val pixelSize = PixelSize(measuredSize.width.dpAsPx(measuredSize.density), measuredSize.height.dpAsPx(measuredSize.density))
                val uriWithParameters = imageOptimizationService.optimizeImageBlockForFill(it, pixelSize)

                return@map assetService.imageByUrl(uriWithParameters.toURL()).map { bitmap ->
                    val timeElapsed = System.currentTimeMillis() - timestampMillis
                    val shouldFade = timeElapsed > IMAGE_FADE_IN_MINIMUM_TIME
                    ImagePollViewModelInterface.ImageUpdate(bitmap, shouldFade) }
            }
            Publishers.combineLatest(optimizedImages) { it }
        }
    }
}

internal interface ImagePollViewModelInterface : BindableViewModel, Measurable {
    val imagePoll: ImagePoll

    /**
     * Subscribe to be informed of the images becoming ready.
     */
    val multiImageUpdates: Publisher<List<ImageUpdate>>

    data class ImageUpdate(val bitmap: Bitmap, val shouldFade: Boolean)

    /**
     * Inform the view model of the display geometry of the image view, so that it may
     * make an attempt to retrieve the images for display.
     *
     * Be sure to subscribe to [multiImageUpdates] first.
     */
    fun informImagePollOptionDimensions(
        measuredSize: MeasuredSize
    )

    fun castVote(selectedOption: Int)
    val votingState: PublishSubject<VotingState>
}