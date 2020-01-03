package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Bitmap
import io.rover.sdk.assets.AssetService
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Image
import io.rover.sdk.data.domain.ImagePoll
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.events.Option
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.Timestamped
import io.rover.sdk.streams.distinctUntilChanged
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.subscribe
import io.rover.sdk.streams.timestamp
import io.rover.sdk.ui.PixelSize
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.poll.VotingInteractor
import io.rover.sdk.ui.blocks.poll.VotingState
import io.rover.sdk.ui.concerns.BindableViewModel
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.dpAsPx
import org.reactivestreams.Publisher

internal class ImagePollViewModel(
    override val id: String,
    override val imagePoll: ImagePoll,
    private val measurementService: MeasurementService,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    private val mainScheduler: Scheduler,
    private val pollVotingInteractor: VotingInteractor,
    private val eventEmitter: EventEmitter,
    private val block: Block,
    private val screen: Screen,
    private val experience: Experience,
    private val campaignId: String?
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
        multiImageUpdate()
        measurementsSubject.onNext(measuredSize)
    }

    private val measurementsSubject = PublishSubject<MeasuredSize>()

    private val images: Map<String, Image> = imagePoll.options.filter { it.image != null }.associate { it.id to it.image!! }

    override val multiImageUpdates = PublishSubject<Map<String, ImagePollViewModelInterface.ImageUpdate>>()

    private fun multiImageUpdate() {
        measurementsSubject.distinctUntilChanged()
            .timestamp()
            .imagesFetchTransform()
            .observeOn(mainScheduler)
            .subscribe { multiImageUpdates.onNext(it) }
    }

    private fun Publisher<Timestamped<MeasuredSize>>.imagesFetchTransform(): Publisher<Map<String, ImagePollViewModelInterface.ImageUpdate>> {
        return flatMap { (timestampMillis, measuredSize) ->
            val optimizedImages = images.map {
                val pixelSize = PixelSize(measuredSize.width.dpAsPx(measuredSize.density), measuredSize.height.dpAsPx(measuredSize.density))
                val uriWithParameters = imageOptimizationService.optimizeImageForFill(it.value, pixelSize)

                return@map assetService.imageByUrl(uriWithParameters.toURL()).distinctUntilChanged().map { bitmap ->
                    val timeElapsed = System.currentTimeMillis() - timestampMillis
                    val shouldFade = timeElapsed > IMAGE_FADE_IN_MINIMUM_TIME
                    it.key to ImagePollViewModelInterface.ImageUpdate(bitmap, shouldFade) }
            }
            Publishers.combineLatest(optimizedImages) { it.associate { it } }
        }
    }

    override fun castVote(selectedOption: String, optionIds: List<String>) {
        pollVotingInteractor.castVotes(selectedOption)
        imagePoll.options.find { it.id == selectedOption }?.let { option ->
            eventEmitter.trackEvent(RoverEvent.PollAnswered(experience, screen, block, Option(option.id, option.text.rawValue, option.image?.url?.toString()), campaignId))
        }
    }

    override fun bindInteractor(id: String, optionIds: List<String>) {
        pollVotingInteractor.initialize(id, optionIds)
    }

    override fun cancel() {
        pollVotingInteractor.cancel()
    }

    override val votingState = pollVotingInteractor.votingState
}

internal interface ImagePollViewModelInterface : BindableViewModel, Measurable {
    val imagePoll: ImagePoll
    val id: String

    /**
     * Subscribe to be informed of the images becoming ready.
     */
    val multiImageUpdates: Publisher<Map<String, ImagePollViewModelInterface.ImageUpdate>>

    fun cancel()

    data class ImageUpdate(val bitmap: Bitmap, val shouldFade: Boolean)

    /**
     * Inform the view model of the display geometry of the image view, so that it may
     * make an attempt to retrieve the images for display.
     *
     * Be sure to subscribe to [multiImageUpdates] first.
     */
    fun informImagePollOptionDimensions(measuredSize: MeasuredSize)
    
    fun castVote(selectedOption: String, optionIds: List<String>)
    fun bindInteractor(id: String, optionIds: List<String>)
    val votingState: PublishSubject<VotingState>
}