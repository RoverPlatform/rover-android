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

package io.rover.sdk.experiences.classic.blocks.poll.image

import android.graphics.Bitmap
import android.net.Uri
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Image
import io.rover.sdk.core.data.domain.ImagePoll
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.Timestamped
import io.rover.sdk.core.streams.distinctUntilChanged
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.streams.timestamp
import io.rover.sdk.experiences.classic.PixelSize
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.assets.AssetService
import io.rover.sdk.experiences.classic.assets.ImageOptimizationService
import io.rover.sdk.experiences.classic.blocks.concerns.layout.Measurable
import io.rover.sdk.experiences.classic.blocks.poll.VotingInteractor
import io.rover.sdk.experiences.classic.blocks.poll.VotingState
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.dpAsPx
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.data.events.Option
import io.rover.sdk.experiences.data.events.Poll
import io.rover.sdk.experiences.data.getFontAppearance
import io.rover.sdk.experiences.services.ClassicEventEmitter
import io.rover.sdk.experiences.services.MeasurementService
import org.reactivestreams.Publisher

internal class ImagePollViewModel(
    override val id: String,
    override val imagePoll: ImagePoll,
    private val measurementService: MeasurementService,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationService,
    private val mainScheduler: Scheduler,
    private val pollVotingInteractor: VotingInteractor,
    private val classicEventEmitter: ClassicEventEmitter,
    private val block: Block,
    private val screen: Screen,
    private val classicExperience: ClassicExperienceModel,
    private val experienceUrl: Uri?,
    private val campaignId: String?
) : ImagePollViewModelInterface {

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40
        private const val IMAGE_FADE_IN_MINIMUM_TIME = 50L
    }

    override fun intrinsicHeight(bounds: RectF): Float {
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
                    it.key to ImagePollViewModelInterface.ImageUpdate(bitmap, shouldFade)
                }
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
            val pollAnsweredEvent = MiniAnalyticsEvent.PollAnswered(classicExperience, experienceUrl, screen, block, option, poll, campaignId)

            classicEventEmitter.trackEvent(pollAnsweredEvent)
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
    val multiImageUpdates: Publisher<Map<String, ImageUpdate>>

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
