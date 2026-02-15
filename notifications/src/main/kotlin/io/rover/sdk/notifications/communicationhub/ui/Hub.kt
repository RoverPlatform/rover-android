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

import android.net.Uri
import android.util.LayoutDirection
import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.config.CommHubColorScheme
import io.rover.sdk.core.refreshHubExperienceURL
import io.rover.sdk.core.roverConfig
import io.rover.sdk.core.hubHomeExperienceURL
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.experiences.ExperienceComposable
import io.rover.sdk.experiences.NavigationMode
import io.rover.sdk.experiences.rich.compose.ui.AppBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigSink
import io.rover.sdk.experiences.rich.compose.ui.LocalExternalNavController
import io.rover.sdk.experiences.rich.compose.ui.LocalNavDestinationRegistry
import io.rover.sdk.experiences.rich.compose.ui.NavDestinationRegistry
import io.rover.sdk.notifications.roverEngageRepository
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.openLink
import io.rover.sdk.notifications.hubCoordinator
import io.rover.sdk.notifications.roverBadge
import io.rover.sdk.notifications.ui.screens.Messages
import io.rover.sdk.notifications.ui.screens.PostDetail
import io.rover.sdk.notifications.ui.viewmodels.PostsListViewModel

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

    val postsRepository = rover.roverEngageRepository
    val linkOpen = rover.resolve(LinkOpenInterface::class.java)

    // Derive feature flags from config
    val isHomeViewEnabled = config.hub.isHomeEnabled
    val isInboxEnabled = config.hub.isInboxEnabled

    // Create navigation components
    val navController = rememberNavController()
    val navRegistry = remember { NavDestinationRegistry() }
    val appBarRegistry = remember { AppBarConfigRegistry() }
    
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
            rover.refreshHubExperienceURL()
        }
    }

    // Determine color scheme from config and generate a Material 3 theme from it.
    val isDark = when (config.colorScheme) {
        CommHubColorScheme.DARK -> true
        CommHubColorScheme.LIGHT -> false
        CommHubColorScheme.AUTO, null -> isSystemInDarkTheme()
    }
    val accentColor = config.accentColor?.toColorInt()?.let { Color(it) } ?: Color.Blue
    val colorScheme =
        automaticMaterialScheme(sourceColor = accentColor, isDark = isDark, dynamicColor = false)

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalExternalNavController provides navController,
            LocalNavDestinationRegistry provides navRegistry,
            LocalAppBarConfigRegistry provides appBarRegistry
        ) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val isOnHomeRoute = currentRoute == "home"
                    val isOnInboxRoute = currentRoute == "inbox"
                    val isOnPostDetailRoute = currentRoute?.startsWith("postDetail/") == true

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

                        else -> experienceAppBarConfig?.title
                    }

                    // Show app bar if: on home with both features enabled, OR Experience provides config, OR on inbox/post detail
                    val shouldShowAppBar = (isOnHomeRoute && isInboxEnabled && isHomeViewEnabled) ||
                            experienceAppBarConfig != null ||
                            isOnInboxRoute ||
                            isOnPostDetailRoute

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
                                if (isOnInboxRoute || isOnPostDetailRoute) {
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
                                        onClick = { navController.navigate("inbox") }
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
                                                contentDescription = "Inbox"
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
                            composable(destination.route) {
                                destination.content()
                            }
                        }

                        composable("home") {
                            // Provide app bar config sink that registers config for the current route
                            CompositionLocalProvider(
                                LocalAppBarConfigSink provides { config ->
                                    // Register config for this route in the registry
                                    appBarRegistry.register("home", config)
                                }
                            ) {
                                HomeRoute(
                                    isHomeViewEnabled = isHomeViewEnabled,
                                    isInboxEnabled = isInboxEnabled,
                                    experienceURL = experienceURL,
                                    postsRepository = postsRepository,
                                    linkOpen = linkOpen,
                                    defaultColorSchemeDark = isDark,
                                    contentPadding = mergedPadding
                                )
                            }
                        }

                        composable("inbox") {
                            MessagesRoute(
                                postsRepository = postsRepository,
                                contentPadding = mergedPadding
                            )
                        }

                        composable("postDetail/{postId}") { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId")
                            postId?.let {
                                PostDetailRoute(
                                    postId = it,
                                    postsRepository = postsRepository,
                                    linkOpen = linkOpen,
                                    contentPadding = mergedPadding
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home route that conditionally displays home view experience or falls back to inbox.
 */
@Composable
private fun HomeRoute(
    isHomeViewEnabled: Boolean,
    isInboxEnabled: Boolean,
    experienceURL: String?,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository,
    defaultColorSchemeDark: Boolean? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    linkOpen: LinkOpenInterface?
) {
    val navController = LocalExternalNavController.current
    
    when {
        isHomeViewEnabled && experienceURL != null -> {
            // Display home view experience in pluggable mode
            ExperienceComposable(
                url = Uri.parse(experienceURL),
                // Pluggable navigation enables the Experience to participate in an existing NavHost.
                // See documentation on NavigationMode.Pluggable for details.
                navigationMode = NavigationMode.Pluggable,
                defaultColorSchemeDark = defaultColorSchemeDark,
                modifier = Modifier
                    // experiences do not yet support edge-to-edge use on Android, so transparent
                    // app bars are not yet supported.
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
            )
        }
        isInboxEnabled -> {
            // Fallback to inbox if home view not available but inbox is enabled
            MessagesComponent(
                displayTitle = "Messages",
                postsRepository = postsRepository,
                navController = navController,
                contentPadding = contentPadding
            )
        }
        else -> {
            // Neither home view nor inbox enabled - show empty surface
            Surface(
                modifier = Modifier.fillMaxSize(),
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
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navController = LocalExternalNavController.current
    
    MessagesComponent(
        displayTitle = "Messages",
        postsRepository = postsRepository,
        navController = navController,
        contentPadding = contentPadding
    )
}

/**
 * Post detail route that fetches a post by ID and displays its details.
 */
@Composable
private fun PostDetailRoute(
    postId: String,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    linkOpen: LinkOpenInterface?
) {
    val context = LocalContext.current
    val navController = LocalExternalNavController.current
    
    // Fetch post by ID from repository
    val post by androidx.compose.runtime.produceState<PostWithSubscription?>(
        initialValue = null,
        key1 = postId
    ) {
        value = postsRepository.getPostWithSubscriptionById(postId)
    }
    
    post?.let { postWithSubscription ->
        PostDetail(
            post = postWithSubscription,
            postsRepository = postsRepository,
            onBackClick = { navController?.popBackStack() },
            onOpenUrl = { url -> linkOpen?.openLink(url, context) },
            modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
        )
    }
}


/**
 * Responsible for displaying the inbox of posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesComponent(
    displayTitle: String,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository,
    navController: androidx.navigation.NavController?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val viewModel: PostsListViewModel = viewModel {
        PostsListViewModel(postsRepository)
    }

    val uiState by viewModel.uiState.collectAsState()

    Messages(
        posts = uiState.posts,
        searchQuery = uiState.searchQuery,
        isExpanded = uiState.isExpanded,
        displayTitle = displayTitle,
        onSearchQueryChanged = { query -> viewModel.updateSearchQuery(query) },
        onExpandedChanged = { expanded -> viewModel.updateExpanded(expanded) },
        onPostClick = { postId ->
            viewModel.markPostAsRead(postId)
            navController?.navigate("postDetail/$postId")
        },
        onRefresh = { viewModel.refresh() },
        isRefreshing = uiState.isRefreshing,
        modifier = modifier
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding),
    )
}
