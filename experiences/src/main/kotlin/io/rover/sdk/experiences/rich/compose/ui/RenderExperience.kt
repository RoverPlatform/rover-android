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

package io.rover.sdk.experiences.rich.compose.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.rover.sdk.experiences.NavigationMode
import io.rover.sdk.experiences.rich.compose.model.nodes.Screen
import io.rover.sdk.experiences.rich.compose.model.values.ExperienceModel
import io.rover.sdk.experiences.rich.compose.ui.graphics.enterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.exitTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popEnterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popExitTransition
import io.rover.sdk.experiences.rich.compose.ui.layers.ScreenLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.ScreenContent
import io.rover.sdk.experiences.rich.compose.ui.utils.getDarkModeValue
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigRegistry
import io.rover.sdk.experiences.rich.compose.ui.LocalAppBarConfigSink

/**
 * This presents an [ExperienceModel].
 *
 * Note this expects a [Environment.LocalAssetContext], [Environment.LocalTypefaceMapping], and
 * [Environment.LocalAuthorizerHandler] to have been set up.
 *
 * @param experience The experience model to render
 * @param modifier Modifier to apply to the experience
 * @param initialScreenID The ID of the screen to show first. (If not provided, defaults to
 * either the `screenID` query parameter in [Environment.LocalUrlParameters], or, ultimately,
 * the screen marked as initial screen in the experience.)
 * @param defaultColorSchemeDark For experiences with color scheme set to auto, should it use dark mode? null to follow system.
 * @param navigationMode Controls how navigation is handled (standalone  = null vs pluggable)
 */
@Composable
internal fun RenderExperience(
    experience: ExperienceModel,
    modifier: Modifier = Modifier,
    initialScreenID: String? = null,
    defaultColorSchemeDark: Boolean? = null,
    navigationMode: NavigationMode = NavigationMode.Standalone
) {
    when (navigationMode) {
        NavigationMode.Standalone -> RenderExperienceStandalone(
            experience = experience,
            modifier = modifier,
            initialScreenID = initialScreenID
        )
        NavigationMode.Pluggable -> RenderExperiencePluggable(
            experience = experience,
            modifier = modifier,
            initialScreenID = initialScreenID,
            defaultColorSchemeDark = defaultColorSchemeDark
        )
    }
}

/**
 * Renders an experience in standalone mode with its own internal NavHost.
 * This is the original behavior.
 */
@Composable
private fun RenderExperienceStandalone(
    experience: ExperienceModel,
    modifier: Modifier = Modifier,
    initialScreenID: String? = null
) {
    val tag = "Experience"

    // As the initial [io.rover.sdk.experiences.rich.compose.model.nodes.Node] is always a [Screen], we create its [ScreenLayer] manually.
    // This [ScreenLayer] begins the chain for creating every children [io.rover.sdk.experiences.rich.compose.model.nodes.Node] in the tree.
    var screenNode by remember { mutableStateOf<Screen?>(null) }
    var screens by remember { mutableStateOf<List<Screen>>(emptyList()) }
    val navController = rememberNavController()
    val context = LocalContext.current

    // We need to hold Environment.LocalData information for passing it into child screens.
    // Serializing the Any type for using the navigation arguments is not achievable at the moment.
    val localDataByScreenId: MutableMap<String, Any?> = mutableMapOf()
    val closeActivity = { (context as? Activity)?.finish() }
    val popBackNavigationStack = {
        localDataByScreenId.remove(navController.currentBackStackEntry?.id)
        navController.popBackStack()
    }

    val urlScreenID = Environment.LocalUrlParameters.current["screenID"]
    Services.Inject { services ->
        LaunchedEffect(initialScreenID, urlScreenID) {
            screens = experience.nodes.filterIsInstance<Screen>()

            val initialScreen = screens.firstOrNull { it.id == initialScreenID }
                ?: screens.firstOrNull { it.id == urlScreenID }
                ?: screens.firstOrNull { it.id == experience.initialScreenID }
                ?: screens.firstOrNull()

            if (initialScreen == null) {
                Log.e(tag, "Could not find initial Screen in this Rover experience.")
                return@LaunchedEffect
            }

            screenNode = initialScreen
        }

        screenNode?.let { initialScreen ->
            CompositionLocalProvider(
                Environment.LocalNavigateToScreen provides { destination, localData ->
                    localDataByScreenId[destination] = localData
                    navController.navigate(destination)
                },
                Environment.LocalNavigateUp provides { if (navController.previousBackStackEntry != null) popBackNavigationStack() else closeActivity() },
                Environment.LocalDismissExperience provides { closeActivity() },
                Environment.LocalExperienceModel provides experience,
                Environment.LocalDocumentFonts provides experience.fonts,
                Environment.LocalIsDarkTheme provides experience.appearance.getDarkModeValue(),
                Environment.LocalAuthenticationContext provides services.authenticationContext
            ) {
                // Every screen needs to be created as a potential route here.
                // The first one is chosen by its id, so it's the only one to be composed at this point.
                NavHost(
                    navController,
                    startDestination = initialScreen.id,
                    modifier = modifier
                ) {
                    screens.forEach { screen ->

                        composable(
                            route = screen.id,
                            enterTransition = { enterTransition() },
                            exitTransition = { exitTransition() },
                            popEnterTransition = { popEnterTransition() },
                            popExitTransition = { popExitTransition() }
                        ) {
                            BackHandler(enabled = screen.id != initialScreen.id) {
                                popBackNavigationStack()
                            }

                            CompositionLocalProvider(
                                Environment.LocalData provides localDataByScreenId[screen.id]
                            ) {
                                ScreenLayer(
                                    node = screen,
                                    appearance = experience.appearance
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
 * Renders an experience in pluggable mode, integrating with an external NavHost.
 * 
 * This mode:
 * - Registers child screens into the parent's NavHost via LocalNavDestinationRegistry
 * - Renders the root screen inline
 * - Reports AppBar config to parent via LocalAppBarConfigSink
 * - Uses external NavController for navigation
 */
@Composable
private fun RenderExperiencePluggable(
    experience: ExperienceModel,
    defaultColorSchemeDark: Boolean? = null,
    modifier: Modifier = Modifier,
    initialScreenID: String? = null
) {
    val tag = "Experience"
    
    var screenNode by remember { mutableStateOf<Screen?>(null) }
    var screens by remember { mutableStateOf<List<Screen>>(emptyList()) }
    
    val navRegistry = LocalNavDestinationRegistry.current
    val externalNavController = LocalExternalNavController.current
    val appBarRegistry = LocalAppBarConfigRegistry.current
    
    if (externalNavController == null) {
        Log.e(tag, "RenderExperiencePluggable requires LocalExternalNavController to be provided")
        return
    }
    
    val context = LocalContext.current
    
    // We need to hold Environment.LocalData information for passing it into child screens.
    val localDataByScreenId: MutableMap<String, Any?> = remember { mutableMapOf() }
    val closeActivity = { (context as? Activity)?.finish() }
    val popBackNavigationStack = {
        localDataByScreenId.remove(externalNavController.currentBackStackEntry?.id)
        externalNavController.popBackStack()
    }
    
    val urlScreenID = Environment.LocalUrlParameters.current["screenID"]
    
    Services.Inject { services ->
        LaunchedEffect(initialScreenID, urlScreenID) {
            screens = experience.nodes.filterIsInstance<Screen>()
            
            val initialScreen = screens.firstOrNull { it.id == initialScreenID }
                ?: screens.firstOrNull { it.id == urlScreenID }
                ?: screens.firstOrNull { it.id == experience.initialScreenID }
                ?: screens.firstOrNull()
            
            if (initialScreen == null) {
                Log.e(tag, "Could not find initial Screen in this Rover experience.")
                return@LaunchedEffect
            }
            
            screenNode = initialScreen
            
            // Register all non-root screens as destinations in the parent NavHost
            screens.filter { it.id != initialScreen.id }.forEach { screen ->
                navRegistry?.register(screen.id) {
                    CompositionLocalProvider(
                        LocalAppBarConfigSink provides { config ->
                            // Register app bar config for this screen's route
                            appBarRegistry?.register(screen.id, config)
                        },
                        Environment.LocalNavigateToScreen provides { destination, localData ->
                            localDataByScreenId[destination] = localData
                            externalNavController.navigate(destination)
                        },
                        Environment.LocalNavigateUp provides { 
                            if (externalNavController.previousBackStackEntry != null) {
                                popBackNavigationStack()
                            } else {
                                closeActivity()
                            }
                        },
                        Environment.LocalDismissExperience provides { closeActivity() },
                        Environment.LocalExperienceModel provides experience,
                        Environment.LocalDocumentFonts provides experience.fonts,
                        Environment.LocalIsDarkTheme provides experience.appearance.getDarkModeValue(
                            darkModeDefault = defaultColorSchemeDark
                        ),
                        Environment.LocalAuthenticationContext provides services.authenticationContext,
                        Environment.LocalData provides localDataByScreenId[screen.id],
                        // Add missing composition locals for action handlers and event tracking
                        Environment.LocalServices provides services,
                        Environment.LocalUserInfo provides Environment.LocalUserInfo.current,
                        Environment.LocalUrlParameters provides Environment.LocalUrlParameters.current,
                        Environment.LocalDeviceContext provides Environment.LocalDeviceContext.current,
                        Environment.LocalExperienceId provides Environment.LocalExperienceId.current,
                        Environment.LocalExperienceName provides Environment.LocalExperienceName.current,
                        Environment.LocalExperienceUrl provides Environment.LocalExperienceUrl.current
                    ) {
                        ScreenContent(
                            node = screen,
                            appearance = experience.appearance,
                            isRootScreen = false
                        )
                    }
                }
            }
        }
        
        screenNode?.let { initialScreen ->
            CompositionLocalProvider(
                Environment.LocalNavigateToScreen provides { destination, localData ->
                    localDataByScreenId[destination] = localData
                    externalNavController.navigate(destination)
                },
                Environment.LocalNavigateUp provides { 
                    if (externalNavController.previousBackStackEntry != null) {
                        popBackNavigationStack()
                    } else {
                        closeActivity()
                    }
                },
                Environment.LocalDismissExperience provides { closeActivity() },
                Environment.LocalExperienceModel provides experience,
                Environment.LocalDocumentFonts provides experience.fonts,
                Environment.LocalIsDarkTheme provides experience.appearance.getDarkModeValue(
                    defaultColorSchemeDark
                ),
                Environment.LocalAuthenticationContext provides services.authenticationContext,
                // Add same missing locals for consistency (root screen already has access via Services.Inject, but explicit is better)
                Environment.LocalServices provides services,
                Environment.LocalUserInfo provides Environment.LocalUserInfo.current,
                Environment.LocalUrlParameters provides Environment.LocalUrlParameters.current,
                Environment.LocalDeviceContext provides Environment.LocalDeviceContext.current,
                Environment.LocalExperienceId provides Environment.LocalExperienceId.current,
                Environment.LocalExperienceName provides Environment.LocalExperienceName.current,
                Environment.LocalExperienceUrl provides Environment.LocalExperienceUrl.current
            ) {
                // Render root screen inline (parent will handle the AppBar)
                ScreenContent(
                    node = initialScreen,
                    appearance = experience.appearance,
                    isRootScreen = true
                )
            }
        }
    }
}
