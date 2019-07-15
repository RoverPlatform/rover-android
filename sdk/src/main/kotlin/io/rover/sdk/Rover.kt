package io.rover.sdk

import android.app.Application
import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.support.annotation.ColorInt
import android.util.DisplayMetrics
import io.rover.sdk.assets.AndroidAssetService
import io.rover.sdk.assets.ImageDownloader
import io.rover.sdk.assets.ImageOptimizationService
import io.rover.sdk.data.domain.Background
import io.rover.sdk.data.domain.Barcode
import io.rover.sdk.data.domain.BarcodeBlock
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Border
import io.rover.sdk.data.domain.ButtonBlock
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Image
import io.rover.sdk.data.domain.ImageBlock
import io.rover.sdk.data.domain.ImagePollBlock
import io.rover.sdk.data.domain.RectangleBlock
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.domain.Text
import io.rover.sdk.data.domain.TextBlock
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.WebView
import io.rover.sdk.data.domain.WebViewBlock
import io.rover.sdk.data.events.AnalyticsService
import io.rover.sdk.data.graphql.GraphQlApiService
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.logging.log
import io.rover.sdk.platform.IoMultiplexingExecutor
import io.rover.sdk.platform.LocalStorage
import io.rover.sdk.services.BarcodeRenderingService
import io.rover.sdk.services.EmbeddedWebBrowserDisplay
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.services.SessionStore
import io.rover.sdk.services.SessionTracker
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.forAndroidMainThread
import io.rover.sdk.streams.forExecutor
import io.rover.sdk.ui.RoverViewModel
import io.rover.sdk.ui.blocks.barcode.BarcodeBlockView
import io.rover.sdk.ui.blocks.barcode.BarcodeBlockViewModel
import io.rover.sdk.ui.blocks.barcode.BarcodeViewModel
import io.rover.sdk.ui.blocks.button.ButtonBlockView
import io.rover.sdk.ui.blocks.button.ButtonBlockViewModel
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModel
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModel
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModel
import io.rover.sdk.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.LayoutPaddingDeflection
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.concerns.text.AndroidRichTextToSpannedTransformer
import io.rover.sdk.ui.blocks.concerns.text.TextViewModel
import io.rover.sdk.ui.blocks.image.ImageBlockView
import io.rover.sdk.ui.blocks.image.ImageBlockViewModel
import io.rover.sdk.ui.blocks.image.ImageViewModel
import io.rover.sdk.ui.blocks.poll.VotingRepository
import io.rover.sdk.ui.blocks.poll.VotingService
import io.rover.sdk.ui.blocks.poll.VotingStorage
import io.rover.sdk.ui.blocks.poll.image.ImagePollBlockView
import io.rover.sdk.ui.blocks.poll.image.ImagePollBlockViewModel
import io.rover.sdk.ui.blocks.poll.image.ImagePollViewModel
import io.rover.sdk.ui.blocks.poll.text.TextPollBlockView
import io.rover.sdk.ui.blocks.poll.text.TextPollBlockViewModel
import io.rover.sdk.ui.blocks.poll.text.TextPollViewModel
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockView
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockViewModel
import io.rover.sdk.ui.blocks.text.TextBlockView
import io.rover.sdk.ui.blocks.text.TextBlockViewModel
import io.rover.sdk.ui.blocks.web.WebBlockView
import io.rover.sdk.ui.blocks.web.WebViewBlockViewModel
import io.rover.sdk.ui.blocks.web.WebViewModel
import io.rover.sdk.ui.layout.BlockAndRowLayoutManager
import io.rover.sdk.ui.layout.BlockAndRowRecyclerAdapter
import io.rover.sdk.ui.layout.Layout
import io.rover.sdk.ui.layout.ViewType
import io.rover.sdk.ui.layout.row.RowView
import io.rover.sdk.ui.layout.row.RowViewModel
import io.rover.sdk.ui.layout.screen.ScreenViewModel
import io.rover.sdk.ui.navigation.NavigationViewModel
import io.rover.sdk.ui.toolbar.ExperienceToolbarViewModel
import io.rover.sdk.ui.toolbar.ToolbarConfiguration
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * Entry point for the Rover SDK.
 */

open class Rover(
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
     * Sets an experience transformer on the [Rover] object enabling retrieved experiences to be altered
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

    private val httpClient: HttpClient = HttpClient(ioScheduler, packageInfo)

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

    private val pollsEndpoint = "https://polls.rover.io/v1/polls"

    private val pollVotingService: VotingService = VotingService(pollsEndpoint, httpClient)

    private val pollVotingStorage: VotingStorage = VotingStorage(localStorage.getKeyValueStorageFor("voting"))

    private val pollVotingRepository = VotingRepository(pollVotingService, pollVotingStorage)

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
            measurementService
        )
    }

    companion object {
        /**
         * Be sure to always call this before [Rover.initialize] in your Application's onCreate()!
         *
         * Rover internally uses the standard HTTP client included with Android, but to work
         * effectively it needs HTTP caching enabled.  Unfortunately, this can only be done at the
         * global level, so we ask that you call this method -- [installSaneGlobalHttpCache] -- at
         * application start time (unless you have already added your own cache to Android's
         * [HttpURLConnection].
         */
        @JvmStatic
        fun installSaneGlobalHttpCache(applicationContext: Context) {
            HttpClient.installSaneGlobalHttpCache(applicationContext)
        }

        @JvmStatic
        @JvmOverloads
        fun initialize(
            application: Application,
            accountToken: String,
            @ColorInt chromeTabColor: Int = Color.BLACK
        ) {
            shared = Rover(
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
        var shared: Rover? = null
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
    private val measurementService: MeasurementService
) {
    fun experienceViewModel(
        experienceRequest: RoverViewModel.ExperienceRequest,
        campaignId: String?,
        activityLifecycle: Lifecycle,
        experienceTransformer: ((Experience) -> Experience)? = Rover.shared?.experienceTransformer
    ): RoverViewModel {
        return RoverViewModel(
            experienceRequest = experienceRequest,
            graphQlApiService = apiService,
            mainThreadScheduler = mainScheduler,
            resolveNavigationViewModel = { experience, icicle ->
                experienceNavigationViewModel(experience, campaignId, activityLifecycle, icicle)
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
        activityLifecycle: Lifecycle,
        icicle: Parcelable? = null
    ): NavigationViewModel {
        return NavigationViewModel(
            experience,
            eventEmitter = eventEmitter,
            campaignId = campaignId,
            sessionTracker = sessionTracker,
            resolveScreenViewModel = { screen -> screenViewModel(screen) },
            resolveToolbarViewModel = { configuration -> experienceToolbarViewModel(configuration) },
            activityLifecycle = activityLifecycle,
            icicle = icicle
        )
    }

    fun screenViewModel(
        screen: Screen
    ): ScreenViewModel {
        return ScreenViewModel(
            screen,
            backgroundViewModel(screen.background),
            resolveRowViewModel = { row -> rowViewModel(row) }
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
        row: Row
    ): RowViewModel {
        return RowViewModel(
            row = row,
            blockViewModelResolver = { block ->
                blockContentsViewModel(block)
            },
            backgroundViewModel = this.backgroundViewModel(row.background)
        )
    }

    private fun blockContentsViewModel(
        block: Block
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
                val textPollViewModel = textPollViewModel(block)
                return TextPollBlockViewModel(
                    textPollViewModel = textPollViewModel,
                    blockViewModel = blockViewModel(block, setOf(), textPollViewModel),
                    backgroundViewModel = backgroundViewModel(block.background),
                    borderViewModel = borderViewModel(block.border)
                )
            }
            is ImagePollBlock -> {
                val imagePollViewModel = imagePollViewModel(block)
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

    private fun imagePollViewModel(imagePollBlock: ImagePollBlock): ImagePollViewModel {
        return ImagePollViewModel(
            imagePollBlock = imagePollBlock,
            measurementService = measurementService,
            imageOptimizationService = imageOptimizationService,
            assetService = assetService,
            mainScheduler = mainScheduler
        )
    }

    private fun textPollViewModel(
        textPollBlock: TextPollBlock
    ): TextPollViewModel {
        return TextPollViewModel(
            textPollBlock,
            measurementService,
            backgroundViewModel(textPollBlock.optionStyle.background)
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
        image: Image?,
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