package io.rover.experiences.ui.blocks.poll.image

import android.graphics.Bitmap
import io.rover.experiences.assets.AssetService
import io.rover.experiences.assets.ImageOptimizationService
import io.rover.core.data.domain.Block
import io.rover.core.data.domain.Experience
import io.rover.core.data.domain.Image
import io.rover.core.data.domain.ImagePoll
import io.rover.core.data.domain.Screen
import io.rover.experiences.data.events.Option
import io.rover.experiences.data.events.Poll
import io.rover.experiences.data.events.RoverEvent
import io.rover.experiences.data.getFontAppearance
import io.rover.experiences.services.EventEmitter
import io.rover.experiences.services.MeasurementService
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.Timestamped
import io.rover.core.streams.distinctUntilChanged
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.subscribe
import io.rover.core.streams.timestamp
import io.rover.experiences.ui.PixelSize
import io.rover.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.experiences.ui.blocks.poll.VotingInteractor
import io.rover.experiences.ui.blocks.poll.VotingState
import io.rover.experiences.ui.concerns.BindableViewModel
import io.rover.experiences.ui.concerns.MeasuredSize
import io.rover.experiences.ui.dpAsPx
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

    override fun intrinsicHeight(bounds: io.rover.experiences.ui.RectF): Float {
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
        trackPollAnswered(selectedOption)
    }

    private fun trackPollAnswered(selectedOption: String) {
        val selectedPollOption = imagePoll.options.find { it.id == selectedOption }

        selectedPollOption?.let { selectedPollOption ->
            val option = Option(selectedPollOption.id, selectedPollOption.text.rawValue, selectedPollOption.image?.url?.toString())
            val poll = Poll(id, imagePoll.question.rawValue)
            val pollAnsweredEvent = RoverEvent.PollAnswered(experience, screen, block, option, poll, campaignId)

            eventEmitter.trackEvent(pollAnsweredEvent)
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