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
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import io.rover.sdk.experiences.appscreens.LocalAppScreenRootAffordance
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
import io.rover.sdk.notifications.postsRepository
import io.rover.sdk.notifications.roverBadge

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
    val statusBarRegistry = remember { StatusBarConfigRegistry() }
    val experienceCache = remember { ExperienceCache() }

    // Resolve HubCoordinator and observe pending navigation
    val hubCoordinator = remember { rover.hubCoordinator }
    val pendingNavigation by hubCoordinator.pendingNavigation.collectAsState()
    
    // Handle pending navigation from coordinator
    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { nav ->
            when (nav) {
                is HubNavigation.Post -> {
                    // Navigate to post detail
                    // If home view and inbox are both enabled, navigate through inbox first
                    // to match iOS behavior
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
                    navController.navigate("conversationDetail/${nav.conversationId}")
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
            hubCoordinator.clearPendingNavigation()
        }
    }

    // Fetch home view URL when enabled
    LaunchedEffect(isHomeViewEnabled) {
        if (isHomeViewEnabled) {
            log.d("refreshHubExperienceURL start")
            rover.refreshHubExperienceURL()
            log.d("refreshHubExperienceURL complete")
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

            HubStatusBarEffect(
                lightStatusBarAppearance = lightStatusBarAppearance,
                legacyStatusBarColor = legacyStatusBarColor,
            )

            Box(modifier = modifier.fillMaxSize()) {
              Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val isOnHomeRoute = currentRoute == "home"
                    val isOnInboxRoute = currentRoute == "inbox"
                    val isOnPostDetailRoute = currentRoute?.startsWith("postDetail/") == true
                    val isOnConversationDetailRoute = currentRoute?.startsWith("conversationDetail/") == true

                    // Look up app bar config from registry based on current route
                    val experienceAppBarConfig = currentRoute?.let { appBarRegistry.get(it) }

                    // Extract post ID and fetch post for app bar title
                    val postId = if (isOnPostDetailRoute) {
                        currentBackStackEntry?.arguments?.getString("postId")
                    } else null

                    // Fetch post for title display
                    val postForTitle by androidx.compose.runtime.produceState<PostWithSubscription?>(
                        initialValue = null,
                        key1 = postId
                    ) {
                        value = postId?.let { postsRepository.getPostWithSubscriptionById(it) }
                    }

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

                    // Show app bar if: on home with both features enabled, OR Experience provides config, OR on inbox/post detail
                    // Exception (one-chrome rule): never show the Hub app bar over an App Screens (V3)
                    // home — that page renders its own top chrome inside its injected safe-area band.
                    val shouldShowAppBar = (isOnHomeRoute && isInboxEnabled && isHomeViewEnabled && !isV3Home) ||
                            experienceAppBarConfig != null ||
                            isOnInboxRoute ||
                            isOnPostDetailRoute ||
                            isOnConversationDetailRoute

                    // TODO: first pass at some animation (so topappbar doesn't appear and disappearing jarringly), but product-wise this isn't yet correct.
                    AnimatedVisibility(
                        visible = shouldShowAppBar,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        TopAppBar(
                            title = appBarTitle ?: { },
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

                                // Hub inbox button (appears only on root screen when both features enabled)
                                if ((experienceAppBarConfig?.isRootScreen
                                        ?: true) && isHomeViewEnabled && isInboxEnabled && isOnHomeRoute
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
                // Merge padding from both the outer (parent app's) Scaffold and this inner Scaffold. This allows the inner content within the Hub to account for the full set of insets
                // both as provided by the Hub's usage in the customer's app (which may well have a Scaffold), and its own internal space consumed by the bars.
                // This approach is more powerful than just consuming the outer paddingValues, because this allows the inner content to draw content underneath
                // inset areas.
                // - paddingValues: from inner Scaffold's topBar
                // - contentPadding: from outer Scaffold (e.g., bottom tab bar)
                // Use max() for start/end, take only relevant padding for top/bottom
                val layoutDirection = LocalLayoutDirection.current
                val mergedPadding = PaddingValues(
                    start = maxOf(
                        paddingValues.calculateStartPadding(layoutDirection),
                        contentPadding.calculateStartPadding(layoutDirection)
                    ),
                    top = paddingValues.calculateTopPadding(), // Only inner (app bar)
                    end = maxOf(
                        paddingValues.calculateEndPadding(layoutDirection),
                        contentPadding.calculateEndPadding(layoutDirection)
                    ),
                    bottom = contentPadding.calculateBottomPadding() // Only outer (tab bar)
                )


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
                            composable(destination.route) { backStackEntry ->
                                HubRouteConfigSinks(
                                    route = backStackEntry.destination.route ?: destination.route,
                                    appBarRegistry = appBarRegistry,
                                    statusBarRegistry = statusBarRegistry
                                ) {
                                    destination.content()
                                }
                            }
                        }

                        composable("home") { backStackEntry ->
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
                                    isV3Home = isV3Home,
                                    inboxBadgeText = badgeText,
                                    experienceURL = experienceURL,
                                    postsRepository = postsRepository,
                                    conversationsRepository = conversationsRepository,
                                    conversationsSync = rover.conversationsSync,
                                    linkOpen = linkOpen,
                                    hubCoordinator = hubCoordinator,
                                    defaultColorSchemeDark = isDark,
                                    contentPadding = mergedPadding
                                )
                            }
                        }

                        composable("inbox") {
                            MessagesRoute(
                                postsRepository = postsRepository,
                                conversationsRepository = conversationsRepository,
                                conversationsSync = rover.conversationsSync,
                                hubCoordinator = hubCoordinator,
                                contentPadding = mergedPadding
                            )
                        }

                        composable("postDetail/{postId}") { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId")
                            postId?.let {
                                PostDetailRoute(
                                    postId = it,
                                    postsRepository = postsRepository,
                                    hubCoordinator = hubCoordinator,
                                    linkOpen = linkOpen,
                                    contentPadding = mergedPadding
                                )
                            }
                        }

                        composable("conversationDetail/{conversationId}") { backStackEntry ->
                            val conversationId = backStackEntry.arguments
                                ?.getString("conversationId")
                            conversationId?.let {
                                ConversationDetailRoute(
                                    conversationId = it,
                                    conversationsRepository = conversationsRepository,
                                    conversationsSync = rover.conversationsSync,
                                    hubCoordinator = hubCoordinator,
                                    linkOpen = linkOpen,
                                    contentPadding = mergedPadding
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
 * In the edge-to-edge regime the bar is transparent and the background is drawn as an in-composition
 * scrim instead, so [legacyStatusBarColor] is null there and `Window.statusBarColor` (deprecated and
 * ignored under edge-to-edge) is left alone.
 */
@Composable
private fun HubStatusBarEffect(
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

    LaunchedEffect(window, lightStatusBarAppearance) {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
                .isAppearanceLightStatusBars = lightStatusBarAppearance
        }
    }

    LaunchedEffect(window, legacyStatusBarColor) {
        val w = window ?: return@LaunchedEffect
        // Set the authored colour while the Hub owns an experience background in the legacy regime;
        // otherwise leave the bar at (or restore it to) the host's original colour.
        @Suppress("DEPRECATION")
        w.statusBarColor = legacyStatusBarColor ?: originalStatusBarColor ?: w.statusBarColor
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

private tailrec fun Context.findActivityWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findActivityWindow()
        else -> null
    }

/**
 * Home route that conditionally displays home view experience or falls back to inbox.
 */
@Composable
private fun HomeRoute(
    isHomeViewEnabled: Boolean,
    isInboxEnabled: Boolean,
    isV3Home: Boolean,
    inboxBadgeText: String?,
    experienceURL: String?,
    postsRepository: io.rover.sdk.notifications.communicationhub.posts.PostsDataSource,
    conversationsRepository: io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource,
    conversationsSync: io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync,
    hubCoordinator: HubCoordinator,
    defaultColorSchemeDark: Boolean? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    linkOpen: LinkOpenInterface?
) {
    val navController = LocalExternalNavController.current
    val navigationState = if (isHomeViewEnabled && experienceURL != null) {
        HubNavigationState.ShowingHome
    } else if (isInboxEnabled) {
        HubNavigationState.ShowingInbox
    } else {
        HubNavigationState.ShowingHome
    }
    val routeVisibilityModifier = Modifier.reportHubRouteVisibility(hubCoordinator, navigationState)
    ClearHubRouteVisibilityOnDispose(hubCoordinator, navigationState)

    when {
        isHomeViewEnabled && experienceURL != null -> {
            // One-inset rule for the App Screens (V3) home: the embedded page owns its own top chrome
            // by injecting the real status-bar inset as safe-area padding, and its full-bleed
            // background paints behind the (transparent, edge-to-edge) status bar. So the Hub must NOT
            // also apply the status-bar top inset here — doing so both pushes the page down by the
            // status-bar height AND leaves it to inject that inset a second time (a double top inset),
            // while the pushed-down page uncovers the bar (an opaque band). Strip only the TOP for V3;
            // the horizontal insets and the bottom (tab-bar) inset are still applied so the page sits
            // above the host's bottom chrome. Every non-V3 home keeps the padding unchanged.
            val layoutDirection = LocalLayoutDirection.current
            val experiencePadding = if (isV3Home) {
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = 0.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding()
                )
            } else {
                contentPadding
            }
            // Display home view experience in pluggable mode
            val homeExperience: @Composable () -> Unit = {
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
                        .padding(experiencePadding)
                        .consumeWindowInsets(experiencePadding)
                )
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
        isInboxEnabled -> {
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
        else -> {
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
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onMessagesListRevealed()
    }

    // Poll the conversations forward sync while the list is on screen. Bounded by both
    // composition (stops when navigating away, since the destination leaves composition)
    // and lifecycle STARTED (stops while the app is in the background).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startForwardPolling()
                Lifecycle.Event.ON_STOP -> viewModel.stopForwardPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopForwardPolling()
        }
    }

    Messages(
        rows = uiState.rows,
        searchQuery = uiState.searchQuery,
        isExpanded = uiState.isExpanded,
        displayTitle = displayTitle,
        onSearchQueryChanged = { query -> viewModel.updateSearchQuery(query) },
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
