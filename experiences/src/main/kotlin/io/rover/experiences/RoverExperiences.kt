package io.rover.experiences

import android.app.Application
import androidx.lifecycle.Lifecycle
import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import androidx.annotation.ColorInt
import android.util.DisplayMetrics
import io.rover.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.core.streams.Scheduler
import io.rover.experiences.assets.AndroidAssetService
import io.rover.experiences.assets.ImageDownloader
import io.rover.experiences.assets.ImageOptimizationService
import io.rover.experiences.data.domain.Background
import io.rover.experiences.data.domain.Barcode
import io.rover.experiences.data.domain.BarcodeBlock
import io.rover.experiences.data.domain.Block
import io.rover.experiences.data.domain.Border
import io.rover.experiences.data.domain.ButtonBlock
import io.rover.experiences.data.domain.Experience
import io.rover.experiences.data.domain.Image
import io.rover.experiences.data.domain.ImageBlock
import io.rover.experiences.data.domain.ImagePoll
import io.rover.experiences.data.domain.ImagePollBlock
import io.rover.experiences.data.domain.RectangleBlock
import io.rover.experiences.data.domain.Row
import io.rover.experiences.data.domain.Screen
import io.rover.experiences.data.domain.Text
import io.rover.experiences.data.domain.TextBlock
import io.rover.experiences.data.domain.TextPoll
import io.rover.experiences.data.domain.TextPollBlock
import io.rover.experiences.data.domain.WebView
import io.rover.experiences.data.domain.WebViewBlock
import io.rover.experiences.data.events.AnalyticsService
import io.rover.experiences.data.events.AppOpenedTracker
import io.rover.experiences.data.graphql.GraphQlApiService
import io.rover.experiences.logging.log
import io.rover.experiences.platform.IoMultiplexingExecutor
import io.rover.experiences.platform.LocalStorage
import io.rover.experiences.services.BarcodeRenderingService
import io.rover.experiences.services.EmbeddedWebBrowserDisplay
import io.rover.experiences.services.EventEmitter
import io.rover.experiences.services.MeasurementService
import io.rover.experiences.services.SessionStore
import io.rover.experiences.services.SessionTracker
import io.rover.core.streams.forAndroidMainThread
import io.rover.core.streams.forExecutor
import io.rover.experiences.ui.RoverViewModel
import io.rover.experiences.ui.blocks.barcode.BarcodeBlockView
import io.rover.experiences.ui.blocks.barcode.BarcodeBlockViewModel
import io.rover.experiences.ui.blocks.barcode.BarcodeViewModel
import io.rover.experiences.ui.blocks.button.ButtonBlockView
import io.rover.experiences.ui.blocks.button.ButtonBlockViewModel
import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModel
import io.rover.experiences.ui.blocks.concerns.border.BorderViewModel
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModel
import io.rover.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.LayoutPaddingDeflection
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.experiences.ui.blocks.concerns.text.AndroidRichTextToSpannedTransformer
import io.rover.experiences.ui.blocks.concerns.text.TextViewModel
import io.rover.experiences.ui.blocks.image.ImageBlockView
import io.rover.experiences.ui.blocks.image.ImageBlockViewModel
import io.rover.experiences.ui.blocks.image.ImageViewModel
import io.rover.experiences.ui.blocks.poll.VotingInteractor
import io.rover.experiences.ui.blocks.poll.VotingService
import io.rover.experiences.ui.blocks.poll.VotingStorage
import io.rover.experiences.ui.blocks.poll.image.ImagePollBlockView
import io.rover.experiences.ui.blocks.poll.image.ImagePollBlockViewModel
import io.rover.experiences.ui.blocks.poll.image.ImagePollViewModel
import io.rover.experiences.ui.blocks.poll.text.TextPollBlockView
import io.rover.experiences.ui.blocks.poll.text.TextPollBlockViewModel
import io.rover.experiences.ui.blocks.poll.text.TextPollViewModel
import io.rover.experiences.ui.blocks.rectangle.RectangleBlockView
import io.rover.experiences.ui.blocks.rectangle.RectangleBlockViewModel
import io.rover.experiences.ui.blocks.text.TextBlockView
import io.rover.experiences.ui.blocks.text.TextBlockViewModel
import io.rover.experiences.ui.blocks.web.WebBlockView
import io.rover.experiences.ui.blocks.web.WebViewBlockViewModel
import io.rover.experiences.ui.blocks.web.WebViewModel
import io.rover.experiences.ui.layout.BlockAndRowLayoutManager
import io.rover.experiences.ui.layout.BlockAndRowRecyclerAdapter
import io.rover.experiences.ui.layout.Layout
import io.rover.experiences.ui.layout.ViewType
import io.rover.experiences.ui.layout.row.RowView
import io.rover.experiences.ui.layout.row.RowViewModel
import io.rover.experiences.ui.layout.screen.ScreenViewModel
import io.rover.experiences.ui.navigation.NavigationViewModel
import io.rover.experiences.ui.toolbar.ExperienceToolbarViewModel
import io.rover.experiences.ui.toolbar.ToolbarConfiguration
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * Entry point for the Rover SDK.
 */

open class RoverExperiences(
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
    private val chromeTabBackgroundColor: Int
) {
    var experienceTransformer: ((Experience) -> Experience)? = null
        private set

    /**
     * Sets an experience transformer on the [RoverExperiences] object enabling retrieved experiences to be altered
     * in the desired way. The transformer is called on the UI thread so the transformer shouldn't block
     * the thread.
     */
    fun setExperienceTransformer(experienceTransformer: (Experience) -> Experience) {
        this.experienceTransformer = experienceTransformer
    }

    fun removeExperienceTransformer() {
        experienceTransformer = null
    }

    /**
     * Public in order to be accessible for capturing events for analytics and automation.
     */
    val eventEmitter: EventEmitter = EventEmitter()

    private val endpoint: String = "https://api.rover.io/graphql"

    private val mainScheduler: Scheduler = Scheduler.forAndroidMainThread()

    private val ioExecutor: Executor = IoMultiplexingExecutor.build("io")

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

    private val analyticsService: AnalyticsService = AnalyticsService(
        application,
        packageInfo,
        accountToken,
        eventEmitter
    )

    private val appOpenedTracker: AppOpenedTracker = AppOpenedTracker(eventEmitter)

    private val pollsEndpoint = "https://polls.rover.io/v1/polls"

    private val pollVotingService: VotingService = VotingService(pollsEndpoint, httpClient)

    private val pollVotingStorage: VotingStorage = VotingStorage(localStorage.getKeyValueStorageFor("voting"))

    private val apiService: GraphQlApiService =
        GraphQlApiService(URL(endpoint), accountToken, httpClient)

    private val sessionTracker: SessionTracker = SessionTracker(eventEmitter, sessionStore, 10)

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
            eventEmitter,
            sessionTracker,
            imageOptimizationService,
            assetService,
            measurementService,
            pollVotingService,
            pollVotingStorage
        )
    }

    companion object {
        /**
         * Be sure to always call this before [RoverExperiences.initialize] in your Application's onCreate()!
         *
         * Rover internally uses the standard HTTP client included with Android, but to work
         * effectively it needs HTTP caching enabled.  Unfortunately, this can only be done at the
         * global level, so we ask that you call this method -- [installSaneGlobalHttpCache] -- at
         * application start time (unless you have already added your own cache to Android's
         * [HttpURLConnection].
         */
        @JvmStatic
        fun installSaneGlobalHttpCache(applicationContext: Context) {
            AndroidHttpsUrlConnectionNetworkClient.installSaneGlobalHttpCache(applicationContext)
        }

        @JvmStatic
        @JvmOverloads
        fun initialize(
            application: Application,
            accountToken: String,
            @ColorInt chromeTabColor: Int = Color.BLACK
        ) {
            shared = RoverExperiences(
                application = application,
                accountToken = accountToken,
                chromeTabBackgroundColor = chromeTabColor
            )
        }

        /**
         * The global Rover context. A Rover instance must be set here in order to use any of Rover.
         * You can use one of the [initialize] methods to do so.
         */
        @JvmStatic
        var shared: RoverExperiences? = null
    }

    init {
        log.i("Started Rover Android SDK v${BuildConfig.VERSION_NAME}.")
    }
}

// TODO: consider moving entire class into appropriate sub-package
internal class ViewModels(
    private val apiService: GraphQlApiService,
    private val mainScheduler: Scheduler,
    private val eventEmitter: EventEmitter,
    private val sessionTracker: SessionTracker,
    private val imageOptimizationService: ImageOptimizationService,
    private val assetService: AndroidAssetService,
    private val measurementService: MeasurementService,
    private val pollVotingService: VotingService,
    private val pollVotingStorage: VotingStorage
) {
    fun experienceViewModel(
        experienceRequest: RoverViewModel.ExperienceRequest,
        campaignId: String?,
        initialScreenId: String?,
        activityLifecycle: Lifecycle,
        experienceTransformer: ((Experience) -> Experience)? = RoverExperiences.shared?.experienceTransformer
    ): RoverViewModel {
        return RoverViewModel(
            experienceRequest = experienceRequest,
            graphQlApiService = apiService,
            mainThreadScheduler = mainScheduler,
            resolveNavigationViewModel = { experience, icicle ->
                experienceNavigationViewModel(experience, campaignId, initialScreenId, activityLifecycle, icicle)
            },
            experienceTransformer = experienceTransformer
        )
    }

    private fun experienceToolbarViewModel(toolbarConfiguration: ToolbarConfiguration): ExperienceToolbarViewModel {
        return ExperienceToolbarViewModel(toolbarConfiguration)
    }

    private fun experienceNavigationViewModel(
        experience: Experience,
        campaignId: String?,
        initialScreenId: String?,
        activityLifecycle: Lifecycle,
        icicle: Parcelable? = null
    ): NavigationViewModel {
        return NavigationViewModel(
            experience,
            eventEmitter = eventEmitter,
            campaignId = campaignId,
            sessionTracker = sessionTracker,
            resolveScreenViewModel = { screen -> screenViewModel(screen, experience, campaignId) },
            resolveToolbarViewModel = { configuration -> experienceToolbarViewModel(configuration) },
            initialScreenId = initialScreenId,
            activityLifecycle = activityLifecycle,
            icicle = icicle
        )
    }

    fun screenViewModel(
        screen: Screen,
        experience: Experience,
        campaignId: String?
    ): ScreenViewModel {
        return ScreenViewModel(
            screen,
            backgroundViewModel(screen.background),
            resolveRowViewModel = { row -> rowViewModel(row, screen, experience, campaignId) }
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
        experience: Experience,
        campaignId: String?
    ): RowViewModel {
        return RowViewModel(
            row = row,
            blockViewModelResolver = { block ->
                blockContentsViewModel(block, screen, experience, campaignId)
            },
            backgroundViewModel = this.backgroundViewModel(row.background)
        )
    }

    private fun blockContentsViewModel(
        block: Block,
        screen: Screen,
        experience: Experience,
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
                val textPollViewModel = textPollViewModel(block.textPoll, block, screen, experience, "${experience.id}:${block.id}", campaignId)
                return TextPollBlockViewModel(
                    textPollViewModel = textPollViewModel,
                    blockViewModel = blockViewModel(block, setOf(), textPollViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ImagePollBlock -> {
                val imagePollViewModel = imagePollViewModel(block.imagePoll, block, screen, experience, "${experience.id}:${block.id}", campaignId)
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

    private fun imagePollViewModel(imagePoll: ImagePoll, block: Block, screen: Screen, experience: Experience, id: String, campaignId: String?): ImagePollViewModel {
        return ImagePollViewModel(
            id = id,
            imagePoll = imagePoll,
            measurementService = measurementService,
            imageOptimizationService = imageOptimizationService,
            assetService = assetService,
            mainScheduler = mainScheduler,
            pollVotingInteractor = VotingInteractor(pollVotingService, pollVotingStorage, mainScheduler),
            eventEmitter = eventEmitter,
            block = block,
            screen = screen,
            experience = experience,
            campaignId = campaignId
        )
    }

    private fun textPollViewModel(
        textPoll: TextPoll,
        block: Block,
        screen: Screen,
        experience: Experience,
        id: String,
        campaignId: String?
    ): TextPollViewModel {
        return TextPollViewModel(
            id,
            textPoll,
            measurementService,
            backgroundViewModel(textPoll.options.first().background),
            VotingInteractor(pollVotingService, pollVotingStorage, mainScheduler),
            eventEmitter,
            block,
            screen,
            experience,
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