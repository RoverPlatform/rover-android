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

package io.rover.sdk.notifications.communicationhub.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.Window
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.spatial.RelativeLayoutBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.hubHomeExperienceURL
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.refreshHubExperienceURL
import io.rover.sdk.core.refreshRoverConfig
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.core.roverConfig
import io.rover.sdk.experiences.ExperienceCache
import io.rover.sdk.experiences.ExperienceComposable
import io.rover.sdk.experiences.LocalExperienceCache
import io.rover.sdk.experiences.NavigationMode
import io.rover.sdk.experiences.appscreens.AppScreenRootAffordance
import io.rover.sdk.experiences.appscreens.AppScreens
import io.rover.sdk.experiences.appscreens.LocalAppScreenColorSchemeOverride
import io.rover.sdk.experiences.appscreens.LocalAppScreenOwnsStatusBar
import io.rover.sdk.experiences.appscreens.LocalAppScreenRootAffordance
import io.rover.sdk.experiences.rich.compose.ui.AppBarConfig
import io.rover.sdk.experiences.rich.compose.ui.AppBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigSink
import io.rover.sdk.experiences.rich.compose.ui.LocalExternalNavController
import io.rover.sdk.experiences.rich.compose.ui.LocalNavDestinationRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalStatusBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalStatusBarConfigSink
import io.rover.sdk.experiences.rich.compose.ui.NavDestinationRegistry
import io.rover.sdk.experiences.rich.compose.ui.StatusBarConfig
import io.rover.sdk.experiences.rich.compose.ui.StatusBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.utils.overlapsStatusBar
import io.rover.sdk.notifications.communicationhub.automaticMaterialScheme
import io.rover.sdk.notifications.communicationhub.rememberCommHubDarkTheme
import io.rover.sdk.notifications.communicationhub.conversations.ConversationDetailRoute
import io.rover.sdk.notifications.communicationhub.conversations.ConversationDetailViewModel
import io.rover.sdk.notifications.communicationhub.messages.Messages
import io.rover.sdk.notifications.communicationhub.messages.MessagesListViewModel
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigationState
import io.rover.sdk.notifications.communicationhub.posts.PostDetailRoute
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import io.rover.sdk.notifications.conversationPushNotificationPresenter
import io.rover.sdk.notifications.conversationsRepository
import io.rover.sdk.notifications.conversationsSync
import io.rover.sdk.notifications.hubCoordinator
import io.rover.sdk.notifications.inboxSeenWatermark
import io.rover.sdk.notifications.postsRepository
import io.rover.sdk.notifications.roverBadge
import kotlinx.coroutines.flow.first

/**
 * Embed this view within a tab to integrate the Rover Hub.
 *
 * @param modifier Modifier
 * @param contentPadding External padding to apply (e.g., from parent Scaffold's bottom bar)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hub(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val rover = Rover.shared

    // Collect state from managers via extension properties
    val config by rover.roverConfig.collectAsState()
    val experienceURL by rover.hubHomeExperienceURL.collectAsState()
    val badgeText by rover.roverBadge.newBadge.collectAsState()

    val postsRepository = rover.postsRepository
    val conversationsRepository = rover.conversationsRepository
    val linkOpen = rover.resolve(LinkOpenInterface::class.java)

    // Derive feature flags from config
    val isHomeViewEnabled = config.hub.isHomeEnabled
    val isInboxEnabled = config.hub.isInboxEnabled

    // App Screens (Experiences V3) home: when the home experience URL classifies as an App Screen
    // (/a/…), the embedded page owns ALL of its own top chrome — it injects its safe-area padding
    // and self-styles via prefers-color-scheme. To honour the one-chrome rule the Hub must NOT draw
    // its TopAppBar over this destination, and its status-bar machinery must stand down for it. This
    // flag is false for every V1/V2 home, so that path is byte-identical to before.
    val isV3Home = experienceURL?.let { AppScreens.isAppScreenUrl(Uri.parse(it)) } == true
    // Whether home actually renders that App Screen: the URL persists after the home view is turned
    // off, and a disabled home shows the inbox fallback. Only the route padding keys on this.
    val rendersV3Home = isHomeViewEnabled && isV3Home

    val context = LocalContext.current

    // Create navigation components.
    // Plain remember (not rememberSaveable) intentionally: the Hub always resets to its home
    // screen on configuration changes. Using rememberSaveable here would restore the
    // NavController's saved back stack, which may include experience sub-destinations that
    // are not yet registered in the NavHost on the first key(0) composition, causing a crash.
    val navController = remember {
        NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
        }
    }
    val navRegistry = remember { NavDestinationRegistry() }
    val appBarRegistry = remember { AppBarConfigRegistry() }

    // A route's app-bar config, ignoring a previous V2 home's stale entry when the home is a V3 App
    // Screen (which never registers its own), so the Hub bar is never drawn over the V3 page's chrome.
    fun appBarConfigFor(route: String?): AppBarConfig? = route
        ?.takeUnless { it == "home" && isV3Home }
        ?.let { appBarRegistry.get(it) }

    // Single rule for which routes show the Hub app bar, shared by the bar and by each route's top
    // padding. The destination's hasAppBar makes it right on a pluggable screen's first frame.
    fun routeShowsAppBar(route: String?): Boolean =
        (route == "home" && isInboxEnabled && isHomeViewEnabled && !isV3Home) ||
            appBarConfigFor(route) != null ||
            navRegistry.destinations.any { it.route == route && it.hasAppBar } ||
            route == "inbox" ||
            route?.startsWith("postDetail/") == true ||
            route?.startsWith("conversationDetail/") == true
    val statusBarRegistry = remember { StatusBarConfigRegistry() }

    // The home URL is seeded from the cached /home response and replaced by the fetch, so a V2 home
    // can compose, register its config, and then be replaced by an App Screens one. Drop what it left
    // behind; App Screens registers nothing of its own, and a V2 home re-registers when it composes.
    LaunchedEffect(isV3Home) {
        if (isV3Home) {
            appBarRegistry.register("home", null)
            statusBarRegistry.register("home", null)
        }
    }

    val experienceCache = remember { ExperienceCache() }

    // Resolve HubCoordinator and observe pending navigation
    val hubCoordinator = remember { rover.hubCoordinator }
    val pendingNavigation by hubCoordinator.pendingNavigation.collectAsState()
    
    // Handle pending navigation from coordinator
    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { nav ->
            executePendingHubNavigation(
                navController = navController,
                nav = nav,
                isHomeViewEnabled = isHomeViewEnabled,
                isInboxEnabled = isInboxEnabled,
            )
            // Conditional clear: a newer navigation queued while this one awaited the graph stays pending.
            hubCoordinator.clearPendingNavigation(nav)
        }
    }

    // Has the home URL question been *answered* yet? This is deliberately distinct from
    // `experienceURL != null`: a null URL means either "not fetched yet" or "fetched, and there is
    // no home", and those two must not render the same thing. Treating the in-flight null as "no
    // home" transiently composes the inbox fallback inside HomeRoute, whose reveal hook advances the
    // inbox seen watermark past every stored post and silently zeroes the badge — without the user
    // ever having seen the inbox. Keyed on isHomeViewEnabled alongside the fetch effect below, so
    // the two always agree: false (home off) means resolved immediately, since the inbox fallback is
    // then the real, permanent destination.
    var homeURLResolved by remember(isHomeViewEnabled) { mutableStateOf(!isHomeViewEnabled) }

    // Fetch home view URL when enabled
    LaunchedEffect(isHomeViewEnabled) {
        if (isHomeViewEnabled) {
            log.d("refreshHubExperienceURL start")
            try {
                rover.refreshHubExperienceURL()
            } finally {
                // Resolved on success *and* failure: a failed or empty fetch legitimately falls back
                // to the inbox exactly as before, and revealing it there is honest — that is what the
                // user actually ends up looking at.
                homeURLResolved = true
            }
            log.d("refreshHubExperienceURL complete")
        } else {
            homeURLResolved = true
        }
    }

    // Re-fetch the remote config each time the Hub appears, matching iOS (HubContentView.onAppear
    // runs a targeted config sync): the app-foreground sync alone would leave a dashboard change
    // (e.g. the colorScheme override) unapplied for as long as the app stays foregrounded.
    // Entering composition is this codebase's "revealed" signal (see MessagesComponent's
    // onMessagesListRevealed); on success the config StateFlow restyles the Hub theme and any
    // embedded App Screens reactively, so no restart or re-entry is needed for the new value.
    LaunchedEffect(Unit) {
        rover.refreshRoverConfig()
    }

    // Determine color scheme from config and generate a Material 3 theme from it.
    val isDark = rememberCommHubDarkTheme()
    val accentColor = config.accentColor?.toColorInt()?.let { Color(it) } ?: Color.Blue
    val colorScheme =
        automaticMaterialScheme(sourceColor = accentColor, isDark = isDark, dynamicColor = false)

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalExperienceCache provides experienceCache,
            LocalExternalNavController provides navController,
            LocalNavDestinationRegistry provides navRegistry,
            LocalAppBarConfigRegistry provides appBarRegistry,
            LocalStatusBarConfigRegistry provides statusBarRegistry,
            // Declare the Hub's colour-scheme policy to any embedded App Screen: the LIVE tri-state
            // config colorScheme (AUTO/LIGHT/DARK, collected above via rover.roverConfig), not the
            // collapsed isDark boolean, so AUTO stays "follow the device" downstream. This is the
            // Hub-only override — standalone full-screen App Screens are reached outside this
            // composition and default to null (device). Provided high here so it covers the embedded
            // home experience wherever it hosts, and a config flip recomposes it in lockstep with the
            // Material theme above.
            LocalAppScreenColorSchemeOverride provides config.colorScheme
        ) {
            // The Hub owns the window's status bar (it draws the top chrome edge-to-edge). Embedded
            // experiences run with manageStatusBar = false and instead report their authored status-bar
            // styling up via the StatusBarConfigRegistry; we look up the current route's config and
            // apply it here. Routes with no experience config (inbox, post/conversation detail) fall
            // back to a tint that contrasts the Hub's own theme.
            val statusBarBackStackEntry by navController.currentBackStackEntryAsState()
            val statusBarRoute = statusBarBackStackEntry?.destination?.route
            val reportedStatusBarConfig = statusBarRoute?.let { statusBarRegistry.get(it) }

            // A screen reports its status-bar config only once its content composes, which is a beat
            // after the NavController switches routes. To avoid flashing the theme default during that
            // gap when navigating between experience screens, hold the previous experience config until
            // the incoming route reports its own. Non-experience routes (inbox, detail screens) have no
            // config and correctly fall back to a tint that contrasts the Hub's theme.
            val isExperienceRoute = statusBarRoute == "home" ||
                navRegistry.destinations.any { it.route == statusBarRoute }
            var lastExperienceStatusBarConfig by remember { mutableStateOf<StatusBarConfig?>(null) }
            if (reportedStatusBarConfig != null) {
                lastExperienceStatusBarConfig = reportedStatusBarConfig
            }
            // Stand down for the App Screens home: force a null config so no scrim band paints and no
            // authored Window.statusBarColor is applied there (the page self-styles the status-bar
            // area via its own full-bleed background + prefers-color-scheme). Every other route keeps
            // the existing behaviour, including holding the last experience config across the reporting
            // gap when navigating between V2 experience screens.
            val isV3HomeRoute = statusBarRoute == "home" && isV3Home
            val experienceStatusBarConfig = if (isV3HomeRoute) {
                null
            } else {
                reportedStatusBarConfig ?: lastExperienceStatusBarConfig?.takeIf { isExperienceRoute }
            }
            val lightStatusBarAppearance = experienceStatusBarConfig?.lightStatusBarAppearance ?: !isDark

            // Regime: a consumed status-bar inset > 0 means the window is edge-to-edge (transparent
            // bar), where the background is painted as the in-composition scrim below and
            // Window.statusBarColor is ignored. When the inset is 0 the window is legacy (opaque bar):
            // the scrim would be 0-height, so the Hub must colour the bar via Window.statusBarColor
            // instead, but only when it actually owns an experience's authored background.
            val edgeToEdge = WindowInsets.statusBars.getTop(LocalDensity.current) > 0
            val legacyStatusBarColor = if (!edgeToEdge) experienceStatusBarConfig?.backgroundColor?.toArgb() else null

            // The Hub's top chrome (bar inset, scrim, window styling) is only right when the Hub is top of
            // screen; WindowInsets.statusBars still reports the full inset under host chrome, so measure.
            val hubView = LocalView.current
            // Pessimistic until the first measurement: a wrong false costs a full-screen Hub one frame of
            // band, a wrong true flashes the very band this exists to remove. (LocalView cannot seed it.)
            var hubOwnsStatusBar by remember { mutableStateOf(false) }

            // remember-ed so the log tracker survives recomposition; kept separate from the state so the
            // first resolution is logged even when false, since embedded configurations fail invisibly.
            val onHubRectChanged: (RelativeLayoutBounds) -> Unit = remember(hubView) {
                var lastLogged: Boolean? = null
                { bounds ->
                    val topInWindow = bounds.boundsInWindow.top
                    val ownsStatusBar = overlapsStatusBar(hubView, topInWindow)
                    if (lastLogged != ownsStatusBar) {
                        lastLogged = ownsStatusBar
                        // Gates the bar inset, scrim and window styling at once; the top edge tells cases apart.
                        HubStatusBar.log.i(
                            "Hub status-bar ownership: $ownsStatusBar (top=$topInWindow)"
                        )
                    }
                    hubOwnsStatusBar = ownsStatusBar
                }
            }

            HubStatusBarEffect(
                // Stand down when not top of screen: overriding the host's icon tint would leave e.g. dark
                // icons on a dark host bar.
                active = hubOwnsStatusBar,
                lightStatusBarAppearance = lightStatusBarAppearance,
                legacyStatusBarColor = legacyStatusBarColor,
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    // debounceMillis = 0: the 64ms default is several frames of visibly wrong chrome.
                    .onLayoutRectChanged(debounceMillis = 0, callback = onHubRectChanged)
            ) {
              Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val isOnHomeRoute = currentRoute == "home"
                    val isOnInboxRoute = currentRoute == "inbox"
                    val isOnPostDetailRoute = currentRoute?.startsWith("postDetail/") == true
                    val isOnConversationDetailRoute = currentRoute?.startsWith("conversationDetail/") == true
                    val homeRouteContent = if (isOnHomeRoute) {
                        resolveHomeRouteContent(
                            isHomeViewEnabled = isHomeViewEnabled,
                            isInboxEnabled = isInboxEnabled,
                            experienceURL = experienceURL,
                            homeURLResolved = homeURLResolved,
                        )
                    } else {
                        null
                    }

                    val experienceAppBarConfig = appBarConfigFor(currentRoute)

                    // Extract post ID and fetch post for app bar title
                    val postId = if (isOnPostDetailRoute) {
                        currentBackStackEntry?.arguments?.getString("postId")
                    } else null

                    // Observe, rather than read once: the post may only reach Room after the
                    // detail route's sync, and the title must fill in when it does.
                    val postForTitle by androidx.compose.runtime.remember(postId) {
                        postId?.let { postsRepository.observePostWithSubscriptionById(it) }
                            ?: kotlinx.coroutines.flow.flowOf<PostWithSubscription?>(null)
                    }.collectAsState(initial = null)

                    val conversationId = if (isOnConversationDetailRoute) {
                        currentBackStackEntry?.arguments?.getString("conversationId")
                    } else null

                    val conversationTitle = if (isOnConversationDetailRoute && currentBackStackEntry != null && conversationId != null) {
                        val viewModel: ConversationDetailViewModel = viewModel(
                            viewModelStoreOwner = currentBackStackEntry!!,
                            key = conversationId,
                        ) {
                            ConversationDetailViewModel(
                                conversationsRepository = conversationsRepository,
                                conversationsSync = rover.conversationsSync,
                                conversationId = conversationId,
                                conversationNotificationPresenter = rover.conversationPushNotificationPresenter,
                            )
                        }
                        val uiState by viewModel.uiState.collectAsState()
                        uiState.title
                    } else {
                        null
                    }

                    // Determine app bar title
                    val appBarTitle: (@Composable () -> Unit)? = when {
                        isOnInboxRoute -> {
                            { Text("Messages") }
                        }

                        isOnPostDetailRoute && postForTitle != null -> {
                            { Text(
                                postForTitle?.post?.subject ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) }
                        }

                        isOnConversationDetailRoute -> {
                            { Text(conversationTitle ?: "Conversation") }
                        }

                        else -> experienceAppBarConfig?.title
                    }

                    // TODO: first pass at some animation (so topappbar doesn't appear and disappearing jarringly), but product-wise this isn't yet correct.
                    AnimatedVisibility(
                        visible = routeShowsAppBar(currentRoute),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        TopAppBar(
                            title = appBarTitle ?: { },
                            // Only reserve the status-bar band when the Hub actually sits under it.
                            windowInsets = if (hubOwnsStatusBar) {
                                TopAppBarDefaults.windowInsets
                            } else {
                                WindowInsets(0.dp)
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = experienceAppBarConfig?.backgroundColor
                                    ?: Color.Transparent
                            ),
                            navigationIcon = experienceAppBarConfig?.navigationIcon ?: {
                                if (isOnInboxRoute || isOnPostDetailRoute || isOnConversationDetailRoute) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            },
                            actions = {
                                // Experience's actions first
                                experienceAppBarConfig?.actions?.forEach { action ->
                                    action()
                                }

                                // Inbox button only over a real home experience with Inbox enabled (the fallback
                                // IS the inbox), never over a V3 home (own capsule; else it flashes into the bar).
                                if ((experienceAppBarConfig?.isRootScreen
                                        ?: true) && isHomeViewEnabled && isInboxEnabled &&
                                    isOnHomeRoute && homeRouteContent == HomeRouteContent.Home &&
                                    !isV3Home
                                ) {
                                    IconButton(
                                        onClick = { navController.navigate("inbox") },
                                        // BadgedBox does not include the badge overlay in its measured size,
                                        // so allocate extra width to keep the badge inside the app bar bounds.
                                        modifier = Modifier.size(width = 64.dp, height = 48.dp)
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                badgeText?.let { text ->
                                                    Badge { Text(text) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Email,
                                                contentDescription = "Inbox",
                                                // Match the tint of the experience's own app-bar icons
                                                // (authored buttonColor). Without this the icon falls back
                                                // to the Material3 default (onSurfaceVariant), which can be
                                                // dark-on-dark against an experience-authored dark app bar.
                                                tint = experienceAppBarConfig?.buttonColor
                                                    ?: LocalContentColor.current
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            ) { paddingValues ->
                // Merge the outer (host) Scaffold's padding with this one for start/end/bottom, never the
                // inner TOP: Material3 swaps in the status-bar inset when the bar is 0x0 and animates it.
                val layoutDirection = LocalLayoutDirection.current
                val routePadding = PaddingValues(
                    start = maxOf(
                        paddingValues.calculateStartPadding(layoutDirection),
                        contentPadding.calculateStartPadding(layoutDirection)
                    ),
                    top = 0.dp,
                    end = maxOf(
                        paddingValues.calculateEndPadding(layoutDirection),
                        contentPadding.calculateEndPadding(layoutDirection)
                    ),
                    bottom = contentPadding.calculateBottomPadding()
                )

                // The status-bar band the Hub's chrome reserves: the same inset the TopAppBar above
                // is given, and nothing when the Hub sits under host chrome.
                val statusBarInset = with(LocalDensity.current) {
                    TopAppBarDefaults.windowInsets.getTop(this).toDp()
                }
                // A route's settled offset below the Hub's top edge, from its own chrome so nothing moves as the
                // bar animates: V3 home none (page injects its own), under the bar its full height, else the band.
                fun routeTopPadding(route: String): Dp {
                    val statusBand = if (hubOwnsStatusBar) statusBarInset else 0.dp
                    return when {
                        route == "home" && rendersV3Home -> 0.dp
                        routeShowsAppBar(route) -> TopAppBarDefaults.TopAppBarExpandedHeight + statusBand
                        else -> statusBand
                    }
                }

                // Every route in the graph is laid out through this, below routeTopPadding.
                fun NavGraphBuilder.hubRoute(
                    route: String,
                    content: @Composable (NavBackStackEntry) -> Unit,
                ) {
                    composable(route) { backStackEntry ->
                        val resolvedRoute = backStackEntry.destination.route ?: route
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = routeTopPadding(resolvedRoute))
                        ) {
                            content(backStackEntry)
                        }
                    }
                }

                // Observe destinations to trigger recomposition when they change
                val destinations = navRegistry.destinations

                // Force NavHost to recompose when destinations change by using key
                key(destinations.size) {
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        // Dynamically registered experience screens
                        destinations.forEach { destination ->
                            hubRoute(destination.route) { backStackEntry ->
                                HubRouteConfigSinks(
                                    route = backStackEntry.destination.route ?: destination.route,
                                    appBarRegistry = appBarRegistry,
                                    statusBarRegistry = statusBarRegistry
                                ) {
                                    destination.content()
                                }
                            }
                        }

                        hubRoute("home") { backStackEntry ->
                            // Provide app bar + status bar config sinks that register config for the
                            // current route so the Hub can render a unified app bar and own the status bar.
                            HubRouteConfigSinks(
                                route = backStackEntry.destination.route ?: "home",
                                appBarRegistry = appBarRegistry,
                                statusBarRegistry = statusBarRegistry
                            ) {
                                HomeRoute(
                                    isHomeViewEnabled = isHomeViewEnabled,
                                    isInboxEnabled = isInboxEnabled,
                                    homeURLResolved = homeURLResolved,
                                    isV3Home = isV3Home,
                                    inboxBadgeText = badgeText,
                                    experienceURL = experienceURL,
                                    postsRepository = postsRepository,
                                    conversationsRepository = conversationsRepository,
                                    conversationsSync = rover.conversationsSync,
                                    linkOpen = linkOpen,
                                    hubCoordinator = hubCoordinator,
                                    defaultColorSchemeDark = isDark,
                                    contentPadding = routePadding,
                                    ownsStatusBar = hubOwnsStatusBar,
                                )
                            }
                        }

                        hubRoute("inbox") {
                            MessagesRoute(
                                postsRepository = postsRepository,
                                conversationsRepository = conversationsRepository,
                                conversationsSync = rover.conversationsSync,
                                hubCoordinator = hubCoordinator,
                                contentPadding = routePadding
                            )
                        }

                        hubRoute("postDetail/{postId}") { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId")
                            postId?.let {
                                PostDetailRoute(
                                    postId = it,
                                    postsRepository = postsRepository,
                                    hubCoordinator = hubCoordinator,
                                    linkOpen = linkOpen,
                                    contentPadding = routePadding
                                )
                            }
                        }

                        hubRoute("conversationDetail/{conversationId}") { backStackEntry ->
                            val conversationId = backStackEntry.arguments
                                ?.getString("conversationId")
                            conversationId?.let {
                                ConversationDetailRoute(
                                    conversationId = it,
                                    conversationsRepository = conversationsRepository,
                                    conversationsSync = rover.conversationsSync,
                                    hubCoordinator = hubCoordinator,
                                    linkOpen = linkOpen,
                                    contentPadding = routePadding
                                )
                            }
                        }
                    }
                }
              }

              // Status-bar background (edge-to-edge regime): when the current route is an experience
              // with an authored status-bar colour, paint that colour in the status-bar band on top of
              // the Hub chrome. This scrim is 0-height in the legacy regime (WindowInsets.statusBars is
              // 0 there); in that regime HubStatusBarEffect colours the opaque bar via
              // Window.statusBarColor instead.
              // Skipped under host chrome (hubOwnsStatusBar): no band to fill, the scrim would land mid-screen.
              if (hubOwnsStatusBar) {
                  experienceStatusBarConfig?.let { config ->
                      Box(
                          modifier = Modifier
                              .align(Alignment.TopCenter)
                              .fillMaxWidth()
                              .background(config.backgroundColor)
                              .statusBarsPadding()
                      )
                  }
              }
            }
        }
    }
}

/**
 * Execute one coordinator-queued navigation, routing Post/Conversation through the inbox first (iOS parity).
 * Suspends until the NavHost has a graph (SDK-355): the coordinator can queue before the NavHost composes.
 * The back-stack flow replays, so this returns immediately when the graph is already set.
 */
internal suspend fun executePendingHubNavigation(
    navController: NavController,
    nav: HubNavigation,
    isHomeViewEnabled: Boolean,
    isInboxEnabled: Boolean,
) {
    HubPendingNavigation.log.d("Waiting for the NavHost graph before executing pending navigation")
    navController.currentBackStackEntryFlow.first()
    HubPendingNavigation.log.i("Executing pending Hub navigation: ${nav::class.simpleName}")
    when (nav) {
        is HubNavigation.Post -> {
            if (isHomeViewEnabled && isInboxEnabled) {
                // Reset to home first, then navigate to inbox, then to post
                navController.navigate("inbox") {
                    popUpTo("home") { inclusive = false }
                }
            }
            navController.navigate("postDetail/${nav.postId}")
        }
        is HubNavigation.Conversation -> {
            if (isHomeViewEnabled && isInboxEnabled) {
                navController.navigate("inbox") {
                    popUpTo("home") { inclusive = false }
                }
            }
            // Encode to match the inbox tap path: conversation IDs can contain path separators,
            // which would otherwise split the route into an extra segment and fail to match.
            navController.navigate("conversationDetail/${Uri.encode(nav.conversationId)}")
        }
        is HubNavigation.Inbox -> {
            navController.navigate("inbox") {
                popUpTo("home") { inclusive = false }
            }
        }
        is HubNavigation.Home -> {
            navController.popBackStack("home", inclusive = false)
        }
    }
}

@Composable
private fun HubRouteConfigSinks(
    route: String,
    appBarRegistry: AppBarConfigRegistry,
    statusBarRegistry: StatusBarConfigRegistry,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppBarConfigSink provides { config ->
            appBarRegistry.register(route, config)
        },
        LocalStatusBarConfigSink provides { config ->
            statusBarRegistry.register(route, config)
        }
    ) {
        content()
    }
}

/**
 * Applies the Hub-owned status-bar styling to the host window: the icon tint
 * ([lightStatusBarAppearance]) always, and — in the legacy (opaque-bar) regime — the background colour
 * via `Window.statusBarColor` ([legacyStatusBarColor]). The host's original values are snapshotted so
 * they are restored when the Hub leaves composition. This lets the Hub own the status bar it draws
 * behind (embedded experiences run with `manageStatusBar = false` and report their authored styling up
 * instead of touching the window themselves).
 *
 * Pass [active] = false when the Hub is not the top-of-screen content: the host owns its status bar
 * there, so the Hub restores the host's values instead of imposing its own.
 *
 * In the edge-to-edge regime the bar is transparent and the background is drawn as an in-composition
 * scrim instead, so [legacyStatusBarColor] is null there and `Window.statusBarColor` (deprecated and
 * ignored under edge-to-edge) is left alone.
 */
@Composable
private fun HubStatusBarEffect(
    active: Boolean,
    lightStatusBarAppearance: Boolean,
    legacyStatusBarColor: Int?,
) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivityWindow() }

    // Snapshot the host's original tint and background once, so we can restore them on dispose.
    val originalAppearance = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars }
    }
    val originalStatusBarColor = remember(window) {
        @Suppress("DEPRECATION")
        window?.statusBarColor
    }

    LaunchedEffect(window, active, lightStatusBarAppearance, originalAppearance) {
        val w = window ?: return@LaunchedEffect
        // While inactive the host owns its status bar: restore whatever it had rather than imposing the
        // Hub's tint.
        val appearance = if (active) lightStatusBarAppearance else originalAppearance ?: return@LaunchedEffect
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = appearance
    }

    LaunchedEffect(window, active, legacyStatusBarColor) {
        val w = window ?: return@LaunchedEffect
        // Set the authored colour while the Hub owns an experience background in the legacy regime;
        // otherwise leave the bar at (or restore it to) the host's original colour.
        @Suppress("DEPRECATION")
        w.statusBarColor =
            (if (active) legacyStatusBarColor else null) ?: originalStatusBarColor ?: w.statusBarColor
    }

    DisposableEffect(window) {
        onDispose {
            window?.let { w ->
                originalAppearance?.let {
                    WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = it
                }
                originalStatusBarColor?.let {
                    @Suppress("DEPRECATION")
                    w.statusBarColor = it
                }
            }
        }
    }
}

/** Log-tag holder: `log` tags by receiver class, and the status-bar layout callback is a receiverless lambda. */
private object HubStatusBar

/** Log-tag holder for the top-level [executePendingHubNavigation] (see [HubStatusBar]). */
private object HubPendingNavigation

private tailrec fun Context.findActivityWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findActivityWindow()
        else -> null
    }

/**
 * What the Hub's "home" route should render right now.
 *
 * Split out of [HomeRoute] as a pure value so the in-flight case can be pinned by a plain unit test:
 * the difference between [Pending] and [Inbox] is the difference between an intact badge and one
 * silently zeroed by the inbox's reveal hook.
 */
internal enum class HomeRouteContent {
    /** The home experience, at a known URL. */
    Home,

    /** The inbox, either as the configured destination or as the settled fallback. */
    Inbox,

    /** Neither home nor inbox is available: an empty themed surface. */
    Empty,

    /**
     * The home URL fetch is still in flight, so we do not yet know whether there is a home. Render
     * something neutral and report NO route visibility — in particular do not compose the inbox,
     * whose reveal advances the seen watermark past every stored post.
     */
    Pending,
}

/**
 * Decide what the home route shows, distinguishing "the home URL has not come back yet"
 * ([homeURLResolved] false) from "it came back and there is no home" (resolved, null URL).
 *
 * A cached URL from a previous run short-circuits this: [experienceURL] is non-null on the very
 * first composition and home renders immediately, so [homeURLResolved] only ever gates the
 * null-URL fallback.
 */
internal fun resolveHomeRouteContent(
    isHomeViewEnabled: Boolean,
    isInboxEnabled: Boolean,
    experienceURL: String?,
    homeURLResolved: Boolean,
): HomeRouteContent = when {
    isHomeViewEnabled && experienceURL != null -> HomeRouteContent.Home
    isHomeViewEnabled && !homeURLResolved -> HomeRouteContent.Pending
    isInboxEnabled -> HomeRouteContent.Inbox
    else -> HomeRouteContent.Empty
}

/**
 * Home route that conditionally displays home view experience or falls back to inbox.
 */
@Composable
private fun HomeRoute(
    isHomeViewEnabled: Boolean,
    isInboxEnabled: Boolean,
    homeURLResolved: Boolean,
    isV3Home: Boolean,
    inboxBadgeText: String?,
    experienceURL: String?,
    postsRepository: io.rover.sdk.notifications.communicationhub.posts.PostsDataSource,
    conversationsRepository: io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource,
    conversationsSync: io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync,
    hubCoordinator: HubCoordinator,
    defaultColorSchemeDark: Boolean? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    ownsStatusBar: Boolean = true,
    linkOpen: LinkOpenInterface?
) {
    val navController = LocalExternalNavController.current
    val content = resolveHomeRouteContent(
        isHomeViewEnabled = isHomeViewEnabled,
        isInboxEnabled = isInboxEnabled,
        experienceURL = experienceURL,
        homeURLResolved = homeURLResolved,
    )
    // No state while pending: the route is not showing home *or* inbox yet, so reporting either
    // would be a lie to anything observing hub visibility (push suppression, conversation reveal).
    val navigationState = when (content) {
        HomeRouteContent.Home, HomeRouteContent.Empty -> HubNavigationState.ShowingHome
        HomeRouteContent.Inbox -> HubNavigationState.ShowingInbox
        HomeRouteContent.Pending -> null
    }
    val routeVisibilityModifier = navigationState
        ?.let { Modifier.reportHubRouteVisibility(hubCoordinator, it) }
        ?: Modifier
    if (navigationState != null) {
        ClearHubRouteVisibilityOnDispose(hubCoordinator, navigationState)
    }

    when (content) {
        HomeRouteContent.Home -> {
            // Display home view experience in pluggable mode
            val homeExperience: @Composable () -> Unit = {
                CompositionLocalProvider(LocalAppScreenOwnsStatusBar provides ownsStatusBar) {
                    ExperienceComposable(
                        url = Uri.parse(experienceURL),
                        // Pluggable navigation enables the Experience to participate in an existing NavHost.
                        // See documentation on NavigationMode.Pluggable for details.
                        navigationMode = NavigationMode.Pluggable,
                        defaultColorSchemeDark = defaultColorSchemeDark,
                        // The Hub owns the window's status bar (it draws the top chrome and manages the
                        // window). The experience reports its authored status-bar styling up via the
                        // StatusBarConfigRegistry, which the Hub applies; the experience itself must not touch
                        // the window.
                        manageStatusBar = false,
                        modifier = routeVisibilityModifier
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding)
                    )
                }
            }
            // App Screens (V3) home with the inbox enabled: hand App Screens the native inbox
            // affordance to render on its ROOT screen (an Email icon + unread badge, in the safe-area
            // top band). Tapping it drives the HUB's navigation to its own inbox route — App Screens
            // never interprets the tap. App Screens hides the affordance while a detail is pushed and
            // restores it on pop. When it is not a V3 home (or inbox is disabled) no provider is
            // installed, so the V1/V2 path is unchanged and standalone App Screens show no affordance.
            if (isV3Home && isInboxEnabled) {
                CompositionLocalProvider(
                    LocalAppScreenRootAffordance provides AppScreenRootAffordance(
                        icon = Icons.Default.Email,
                        badgeText = inboxBadgeText,
                        contentDescription = "Inbox",
                        onTap = { navController?.navigate("inbox") }
                    )
                ) {
                    homeExperience()
                }
            } else {
                homeExperience()
            }
        }
        HomeRouteContent.Inbox -> {
            // Fallback to inbox if home view not available but inbox is enabled
            MessagesComponent(
                displayTitle = "Messages",
                postsRepository = postsRepository,
                conversationsRepository = conversationsRepository,
                conversationsSync = conversationsSync,
                navController = navController,
                modifier = routeVisibilityModifier,
                contentPadding = contentPadding
            )
        }
        HomeRouteContent.Pending -> {
            // Home URL fetch still in flight: hold a plain themed surface (same paint as the
            // no-content case above) rather than briefly composing the inbox, which would reveal
            // it — and clear the badge — behind the user's back.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {}
        }
        HomeRouteContent.Empty -> {
            // Neither home view nor inbox enabled - show empty surface
            Surface(
                modifier = routeVisibilityModifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {}
        }
    }
}

/**
 * Inbox route that displays the messages screen.
 */
@Composable
private fun MessagesRoute(
    postsRepository: io.rover.sdk.notifications.communicationhub.posts.PostsDataSource,
    conversationsRepository: io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource,
    conversationsSync: io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync,
    hubCoordinator: HubCoordinator,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navController = LocalExternalNavController.current
    val routeVisibilityModifier = Modifier.reportHubRouteVisibility(
        hubCoordinator,
        HubNavigationState.ShowingInbox
    )
    ClearHubRouteVisibilityOnDispose(hubCoordinator, HubNavigationState.ShowingInbox)
    
    MessagesComponent(
        displayTitle = "Messages",
        postsRepository = postsRepository,
        conversationsRepository = conversationsRepository,
        conversationsSync = conversationsSync,
        navController = navController,
        modifier = routeVisibilityModifier,
        contentPadding = contentPadding
    )
}

internal fun Modifier.reportHubRouteVisibility(
    hubCoordinator: HubCoordinator,
    state: HubNavigationState,
): Modifier = this.onVisibilityChanged(minFractionVisible = 1f) { isVisible ->
    hubCoordinator.updateNavigationVisibility(state, isVisible)
}

@Composable
internal fun ClearHubRouteVisibilityOnDispose(
    hubCoordinator: HubCoordinator,
    state: HubNavigationState,
) {
    DisposableEffect(hubCoordinator, state) {
        onDispose {
            hubCoordinator.updateNavigationVisibility(state, false)
        }
    }
}


/**
 * Responsible for displaying the composite messages list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesComponent(
    displayTitle: String,
    postsRepository: io.rover.sdk.notifications.communicationhub.posts.PostsDataSource,
    conversationsRepository: io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource,
    conversationsSync: io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync,
    navController: androidx.navigation.NavController?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val viewModel: MessagesListViewModel = viewModel {
        MessagesListViewModel(
            postsRepository = postsRepository,
            conversationsRepository = conversationsRepository,
            conversationsSync = conversationsSync,
            syncCoordinator = Rover.shared.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
            inboxSeenWatermark = Rover.shared.inboxSeenWatermark,
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onMessagesListRevealed()
    }

    // Poll the conversations forward sync while the list is on screen. Bounded by both
    // composition (stops when navigating away, since the destination leaves composition)
    // and lifecycle STARTED (stops while the app is in the background).
    //
    // The same two boundaries also advance the inbox seen watermark a second time, so posts that
    // arrived while the user was looking at the list don't badge once they leave it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        // Tracks whether this observer has seen an ON_STOP, so the foreground hook below only
        // fires on a genuine background/foreground round trip. A freshly added observer receives a
        // catch-up ON_START immediately when the owner is already started, which would otherwise
        // duplicate the LaunchedEffect reveal at first composition.
        var wasStopped = false
        var concealed = false
        fun concealOnce() {
            if (!concealed) {
                concealed = true
                viewModel.onMessagesListConcealed()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.startForwardPolling()
                    if (wasStopped) {
                        wasStopped = false
                        concealed = false
                        // Returning to the foreground with the list still composed: the reveal
                        // effect won't re-run, so a post that arrived while backgrounded would
                        // badge over the open list. Scoped to the watermark/tray only — the
                        // history backfill must not re-run on every foreground.
                        viewModel.onMessagesListForegrounded()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    viewModel.stopForwardPolling()
                    concealOnce()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopForwardPolling()
            concealOnce()
        }
    }

    Messages(
        rows = uiState.rows,
        searchQuery = uiState.searchQuery,
        searchToken = uiState.searchToken,
        suggestions = uiState.suggestions,
        isExpanded = uiState.isExpanded,
        displayTitle = displayTitle,
        onSearchQueryChanged = { query -> viewModel.updateSearchQuery(query) },
        onSuggestionSelected = { token -> viewModel.selectSearchToken(token) },
        onSearchTokenCleared = { viewModel.clearSearchToken() },
        onExpandedChanged = { expanded -> viewModel.updateExpanded(expanded) },
        onPostClick = { postId ->
            viewModel.markPostAsRead(postId)
            navController?.navigate("postDetail/$postId")
        },
        onConversationClick = { conversationId ->
            navController?.navigate("conversationDetail/${Uri.encode(conversationId)}")
        },
        onRefresh = { viewModel.refresh() },
        isRefreshing = uiState.isRefreshing,
        modifier = modifier
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding),
    )
}
