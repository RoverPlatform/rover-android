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

package io.rover.sdk.experiences

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.util.DisplayMetrics
import androidx.lifecycle.Lifecycle
import io.rover.sdk.core.data.domain.*
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.forAndroidMainThread
import io.rover.sdk.core.streams.forExecutor
import io.rover.sdk.experiences.classic.assets.AndroidAssetService
import io.rover.sdk.experiences.classic.assets.ImageDownloader
import io.rover.sdk.experiences.classic.assets.ImageOptimizationService
import io.rover.sdk.experiences.classic.blocks.barcode.BarcodeBlockView
import io.rover.sdk.experiences.classic.blocks.barcode.BarcodeBlockViewModel
import io.rover.sdk.experiences.classic.blocks.barcode.BarcodeViewModel
import io.rover.sdk.experiences.classic.blocks.button.ButtonBlockView
import io.rover.sdk.experiences.classic.blocks.button.ButtonBlockViewModel
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModel
import io.rover.sdk.experiences.classic.blocks.concerns.border.BorderViewModel
import io.rover.sdk.experiences.classic.blocks.concerns.layout.*
import io.rover.sdk.experiences.classic.blocks.concerns.text.AndroidRichTextToSpannedTransformer
import io.rover.sdk.experiences.classic.blocks.concerns.text.TextViewModel
import io.rover.sdk.experiences.classic.blocks.image.ImageBlockView
import io.rover.sdk.experiences.classic.blocks.image.ImageBlockViewModel
import io.rover.sdk.experiences.classic.blocks.image.ImageViewModel
import io.rover.sdk.experiences.classic.blocks.poll.VotingInteractor
import io.rover.sdk.experiences.classic.blocks.poll.VotingService
import io.rover.sdk.experiences.classic.blocks.poll.VotingStorage
import io.rover.sdk.experiences.classic.blocks.poll.image.ImagePollBlockView
import io.rover.sdk.experiences.classic.blocks.poll.image.ImagePollBlockViewModel
import io.rover.sdk.experiences.classic.blocks.poll.image.ImagePollViewModel
import io.rover.sdk.experiences.classic.blocks.poll.text.TextPollBlockView
import io.rover.sdk.experiences.classic.blocks.poll.text.TextPollBlockViewModel
import io.rover.sdk.experiences.classic.blocks.poll.text.TextPollViewModel
import io.rover.sdk.experiences.classic.blocks.rectangle.RectangleBlockView
import io.rover.sdk.experiences.classic.blocks.rectangle.RectangleBlockViewModel
import io.rover.sdk.experiences.classic.blocks.text.TextBlockView
import io.rover.sdk.experiences.classic.blocks.text.TextBlockViewModel
import io.rover.sdk.experiences.classic.blocks.web.WebBlockView
import io.rover.sdk.experiences.classic.blocks.web.WebViewBlockViewModel
import io.rover.sdk.experiences.classic.blocks.web.WebViewModel
import io.rover.sdk.experiences.classic.layout.BlockAndRowLayoutManager
import io.rover.sdk.experiences.classic.layout.BlockAndRowRecyclerAdapter
import io.rover.sdk.experiences.classic.layout.Layout
import io.rover.sdk.experiences.classic.layout.ViewType
import io.rover.sdk.experiences.classic.layout.row.RowView
import io.rover.sdk.experiences.classic.layout.row.RowViewModel
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModel
import io.rover.sdk.experiences.classic.navigation.NavigationViewModel
import io.rover.sdk.experiences.data.events.AppOpenedTracker
import io.rover.sdk.experiences.data.events.MiniAnalyticsService
import io.rover.sdk.experiences.platform.IoMultiplexingExecutor
import io.rover.sdk.experiences.platform.LocalStorage
import io.rover.sdk.experiences.services.*
import java.util.concurrent.Executor

/**
 * Holds the various pieces belonging to the Classic Experiences renderer.
 */
internal class RoverExperiencesClassic(
    /**
     * When initializing Rover you must give it a reference
     */
    private val application: Application,

    /**
     * Set your Rover Account Token (API Key) here.
     */
    private var accountToken: String,

    /**
     * Set the background colour for the Custom Chrome tabs that are used for presenting web content
     * in a web browser.
     */
    private val chromeTabBackgroundColor: Int,

    /**
     * Network service for making Rover API requests via GraphQL.
     */
    private val apiService: GraphQlApiServiceInterface,

    internal val appThemeDescription: AppThemeDescription
) {
    var classicExperienceTransformer: ((ClassicExperienceModel) -> ClassicExperienceModel)? = null
        private set

    /**
     * Sets an experience transformer on the [RoverExperiencesClassic] object enabling retrieved experiences to be altered
     * in the desired way. The transformer is called on the UI thread so the transformer shouldn't block
     * the thread.
     */
    fun setExperienceTransformer(classicExperienceTransformer: (ClassicExperienceModel) -> ClassicExperienceModel) {
        this.classicExperienceTransformer = classicExperienceTransformer
    }

    fun removeExperienceTransformer() {
        classicExperienceTransformer = null
    }

    internal val classicEventEmitter: ClassicEventEmitter = ClassicEventEmitter()

    private val endpoint: String = "https://api.rover.io/graphql"

    internal val mainScheduler: Scheduler = Scheduler.forAndroidMainThread()

    internal val ioExecutor: Executor = IoMultiplexingExecutor.build("io")

    private val ioScheduler: Scheduler = Scheduler.forExecutor(
        ioExecutor
    )

    private val imageDownloader: ImageDownloader = ImageDownloader(ioExecutor)

    private val assetService: AndroidAssetService =
        AndroidAssetService(imageDownloader, ioScheduler, mainScheduler)

    private val imageOptimizationService: ImageOptimizationService = ImageOptimizationService()

    private val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)

    private val httpClient: AndroidHttpsUrlConnectionNetworkClient = AndroidHttpsUrlConnectionNetworkClient(ioScheduler, packageInfo)

    internal val webBrowserDisplay: EmbeddedWebBrowserDisplay =
        EmbeddedWebBrowserDisplay(chromeTabBackgroundColor)

    private val localStorage: LocalStorage = LocalStorage(application)

    private val sessionStore: SessionStore = SessionStore(localStorage)

    private val miniAnalyticsService: MiniAnalyticsService = MiniAnalyticsService(
        application,
        packageInfo,
        accountToken,
        classicEventEmitter
    )

    private val appOpenedTracker: AppOpenedTracker = AppOpenedTracker(classicEventEmitter)

    private val pollsEndpoint = "https://polls.rover.io/v1/polls"

    private val pollVotingService: VotingService = VotingService(pollsEndpoint, httpClient)

    private val pollVotingStorage: VotingStorage = VotingStorage(localStorage.getKeyValueStorageFor("voting"))

    private val sessionTracker: SessionTracker = SessionTracker(classicEventEmitter, sessionStore, 10)

    private val textFormatter: AndroidRichTextToSpannedTransformer =
        AndroidRichTextToSpannedTransformer()

    internal val barcodeRenderingService: BarcodeRenderingService = BarcodeRenderingService()

    private val measurementService: MeasurementService = MeasurementService(
        displayMetrics = application.resources.displayMetrics,
        richTextToSpannedTransformer = textFormatter,
        barcodeRenderingService = barcodeRenderingService
    )

    internal val views: Views = Views()

    internal val viewModels: ViewModels by lazy {
        ViewModels(
            apiService,
            mainScheduler,
            classicEventEmitter,
            sessionTracker,
            imageOptimizationService,
            assetService,
            measurementService,
            pollVotingService,
            pollVotingStorage
        )
    }
}

// TODO: consider moving entire class into appropriate sub-package
internal class ViewModels(
    private val apiService: GraphQlApiServiceInterface,
    private val mainScheduler: Scheduler,
    private val classicEventEmitter: ClassicEventEmitter,
    private val sessionTracker: SessionTracker,
    private val imageOptimizationService: ImageOptimizationService,
    private val assetService: AndroidAssetService,
    private val measurementService: MeasurementService,
    private val pollVotingService: VotingService,
    private val pollVotingStorage: VotingStorage
) {
    // TODO: rename to experienceViewModel()
    fun experienceNavigationViewModel(
        classicExperience: ClassicExperienceModel,
        experienceUrl: Uri?,
        campaignId: String?,
        initialScreenId: String?,
        activityLifecycle: Lifecycle,
        icicle: Parcelable? = null
    ): NavigationViewModel {
        return NavigationViewModel(
            classicExperience,
            experienceUrl = experienceUrl,
            classicEventEmitter = classicEventEmitter,
            campaignId = campaignId,
            sessionTracker = sessionTracker,
            resolveScreenViewModel = { screen -> screenViewModel(screen, classicExperience, experienceUrl, campaignId) },
            initialScreenId = initialScreenId,
            activityLifecycle = activityLifecycle,
            icicle = icicle
        )
    }

    private fun screenViewModel(
        screen: Screen,
        classicExperience: ClassicExperienceModel,
        experienceUrl: Uri?,
        campaignId: String?
    ): ScreenViewModel {
        return ScreenViewModel(
            screen,
            backgroundViewModel(screen.background),
            resolveRowViewModel = { row -> rowViewModel(row, screen, classicExperience, experienceUrl, campaignId) }
        )
    }

    private fun backgroundViewModel(
        background: Background
    ): BackgroundViewModel {
        return BackgroundViewModel(
            background = background,
            assetService = assetService,
            imageOptimizationService = imageOptimizationService,
            mainScheduler = mainScheduler
        )
    }

    fun rowViewModel(
        row: Row,
        screen: Screen,
        classicExperience: ClassicExperienceModel,
        experienceUrl: Uri?,
        campaignId: String?
    ): RowViewModel {
        return RowViewModel(
            row = row,
            blockViewModelResolver = { block ->
                blockContentsViewModel(block, screen, classicExperience, experienceUrl, campaignId)
            },
            backgroundViewModel = this.backgroundViewModel(row.background)
        )
    }

    private fun blockContentsViewModel(
        block: Block,
        screen: Screen,
        classicExperience: ClassicExperienceModel,
        experienceUrl: Uri?,
        campaignId: String?
    ): CompositeBlockViewModelInterface {
        when (block) {
            is RectangleBlock -> {
                return RectangleBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), null),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is TextBlock -> {
                val textViewModel = textViewModel(block.text, singleLine = false)
                return TextBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), textViewModel),
                    textViewModel = textViewModel,
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ImageBlock -> {
                val imageViewModel = imageViewModel(block.image, block)
                return ImageBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), imageViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    imageViewModel = imageViewModel,
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ButtonBlock -> {
                return ButtonBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), null),
                    borderViewModel = borderViewModel(block.border),
                    backgroundViewModel = backgroundViewModel(block.background),
                    textViewModel = textViewModel(
                        block.text,
                        singleLine = true,
                        centerVertically = true
                    )
                )
            }
            is WebViewBlock -> {
                return WebViewBlockViewModel(
                    blockViewModel = blockViewModel(block, setOf(), null),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border),
                    webViewModel = webViewModel(block.webView)
                )
            }
            is BarcodeBlock -> {
                val barcodeViewModel = barcodeViewModel(block.barcode)
                return BarcodeBlockViewModel(
                    blockViewModel = blockViewModel(block, emptySet(), barcodeViewModel),
                    barcodeViewModel = barcodeViewModel
                )
            }
            is TextPollBlock -> {
                val textPollViewModel = textPollViewModel(block.textPoll, block, screen, classicExperience, experienceUrl, "${classicExperience.id}:${block.id}", campaignId)
                return TextPollBlockViewModel(
                    textPollViewModel = textPollViewModel,
                    blockViewModel = blockViewModel(block, setOf(), textPollViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ImagePollBlock -> {
                val imagePollViewModel = imagePollViewModel(block.imagePoll, block, screen, classicExperience, "${classicExperience.id}:${block.id}", experienceUrl, campaignId)
                return ImagePollBlockViewModel(
                    imagePollViewModel = imagePollViewModel,
                    blockViewModel = blockViewModel(block, setOf(), imagePollViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            else -> throw Exception(
                "This Rover UI block type is not supported by this version of the SDK: ${block.javaClass.simpleName}."
            )
        }
    }

    private fun imagePollViewModel(imagePoll: ImagePoll, block: Block, screen: Screen, classicExperience: ClassicExperienceModel, id: String, experienceUrl: Uri?, campaignId: String?): ImagePollViewModel {
        return ImagePollViewModel(
            id = id,
            imagePoll = imagePoll,
            measurementService = measurementService,
            imageOptimizationService = imageOptimizationService,
            assetService = assetService,
            mainScheduler = mainScheduler,
            pollVotingInteractor = VotingInteractor(pollVotingService, pollVotingStorage, mainScheduler),
            classicEventEmitter = classicEventEmitter,
            block = block,
            screen = screen,
            classicExperience = classicExperience,
            experienceUrl = experienceUrl,
            campaignId = campaignId
        )
    }

    private fun textPollViewModel(
        textPoll: TextPoll,
        block: Block,
        screen: Screen,
        classicExperience: ClassicExperienceModel,
        experienceUrl: Uri?,
        id: String,
        campaignId: String?
    ): TextPollViewModel {
        return TextPollViewModel(
            id,
            textPoll,
            measurementService,
            backgroundViewModel(textPoll.options.first().background),
            VotingInteractor(pollVotingService, pollVotingStorage, mainScheduler),
            classicEventEmitter,
            block,
            screen,
            classicExperience,
            experienceUrl,
            campaignId
        )
    }

    private fun blockViewModel(
        block: Block,
        paddingDeflections: Set<LayoutPaddingDeflection>,
        measurable: Measurable?
    ): BlockViewModel {
        return BlockViewModel(
            block = block,
            paddingDeflections = paddingDeflections,
            measurable = measurable
        )
    }

    private fun borderViewModel(
        border: Border
    ): BorderViewModel {
        return BorderViewModel(border)
    }

    private fun textViewModel(
        text: Text,
        singleLine: Boolean,
        centerVertically: Boolean = false
    ): TextViewModel {
        return TextViewModel(
            styledText = text,
            measurementService = measurementService,
            singleLine = singleLine,
            centerVertically = centerVertically
        )
    }

    private fun imageViewModel(
        image: Image,
        containingBlock: Block
    ): ImageViewModel {
        return ImageViewModel(
            image = image,
            block = containingBlock,
            imageOptimizationService = imageOptimizationService,
            assetService = assetService,
            mainScheduler = mainScheduler
        )
    }

    private fun webViewModel(
        webView: WebView
    ): WebViewModel {
        return WebViewModel(webView)
    }

    private fun barcodeViewModel(
        barcode: Barcode
    ): BarcodeViewModel {
        return BarcodeViewModel(
            barcode,
            measurementService
        )
    }
}

internal class Views {
    // TODO: consider moving into RoverView
    fun blockAndRowLayoutManager(
        layout: Layout,
        displayMetrics: DisplayMetrics
    ): BlockAndRowLayoutManager {
        return BlockAndRowLayoutManager(
            layout,
            displayMetrics
        )
    }

    private fun blockView(
        viewType: ViewType,
        context: Context
    ): LayoutableView<out LayoutableViewModel> {
        return when (viewType) {
            ViewType.Row -> RowView(context)
            ViewType.Rectangle -> RectangleBlockView(context)
            ViewType.Text -> TextBlockView(context)
            ViewType.Button -> ButtonBlockView(context)
            ViewType.Image -> ImageBlockView(context)
            ViewType.WebView -> WebBlockView(context)
            ViewType.Barcode -> BarcodeBlockView(context)
            ViewType.TextPoll -> TextPollBlockView(context)
            ViewType.ImagePoll -> ImagePollBlockView(context)
        }
    }

    // TODO: consider moving into RoverView
    fun blockAndRowRecyclerAdapter(
        layout: Layout,
        displayMetrics: DisplayMetrics
    ): BlockAndRowRecyclerAdapter {
        return BlockAndRowRecyclerAdapter(
            layout = layout,
            displayMetrics = displayMetrics,
            blockViewFactory = { viewType, context ->
                blockView(viewType, context)
            }
        )
    }
}
