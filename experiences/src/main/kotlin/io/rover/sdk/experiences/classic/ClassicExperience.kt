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

package io.rover.sdk.experiences.classic

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.navigation.ExperienceExternalNavigationEvent
import io.rover.sdk.experiences.classic.toolbar.ExperienceToolbar
import io.rover.sdk.experiences.platform.asAndroidUri
import io.rover.sdk.experiences.rich.compose.ui.Services

/**
 * Render a Rover Classic Experience.
 */
@Composable
internal fun ClassicExperience(
    classicExperience: ClassicExperienceModel,
    url: Uri,
    urlParams: Map<String, String>
) {
    // TODO: replace the icicle pattern with instead state lifting in view model(s), then can store
    //  up here with rememberSaveable (needed to maintain experience backstack on rotation).
    //  ie, the storage concern should not be down in the VM, but up here. using icicle as-is
    //  has a push-pull problem.

    Services.Inject { services ->
        val lifecycleOwner = LocalLifecycleOwner.current
        val lifecycle = lifecycleOwner.lifecycle

        val viewModel = remember {
            services.experiencesClassic.viewModels.experienceNavigationViewModel(
                classicExperience,
                url,
                urlParams["campaignID"],
                urlParams["screenID"],
                lifecycle
            )
        }

        // always enabled, when back stack is entry ClassicExperience() handles it
        // by calling finish() on activity.

        BackHandler(viewModel.canGoBack()) {
            viewModel.pressBack()
        }

        val containingContext = LocalContext.current

        LaunchedEffect(url) {
            // launch a coroutine in main thread scope to observe events from view model to handle
            // nav events emitted from the VM.

            viewModel
                .externalNavigationEvents
                // this use of lifecycle to dispose of this subscriber is better than nothing
                // but a bit wrong, because we should be binding it to the coroutine scope. A
                // bit of impedance mismatch between Classic's custom reactive streams
                // implementation and modern Kotlin coroutine-land. Micro-Reactive Streams should
                // be retired throughout the SDK and replaced with Kotlin coroutines.
                .androidLifecycleDispose(lifecycleOwner)
                .subscribe({ event ->
                    // TODO: there is an argument to promote this to a parameter of the
                    //   composable & view model, to be provided by containing activity or
                    //   fragment.  However, that adds complexity, and it appears that
                    //   the below approach will work for now.
                    when (event) {
                        is ExperienceExternalNavigationEvent.Exit -> {
                            (containingContext as? Activity)?.finish()
                        }
                        is ExperienceExternalNavigationEvent.OpenUri -> {
                            try {
                                // give Rover's router a first crack at the URI before defaulting
                                // to just making an intent for it, thus allowing universal links
                                // to other experiences to work more reliably without relying on
                                // OS routing.

                                // TODO: can this use LocalUriHandler?  Could we inject
                                //   our own LocalUriHandler that uses Rover's router and intents?
                                //   then we could use that both here and in new Experience's
                                //   ActionModifier.

                                val intent = Rover.shared.intentForLink(event.uri.asAndroidUri()) ?: Intent(
                                    Intent.ACTION_VIEW,
                                    event.uri.asAndroidUri()
                                )
                                
                                if (event.dismiss) {
                                    (containingContext as? Activity)?.finish()
                                }

                                containingContext.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                log.v("No activity found to handle URI: ${event.uri}")
                            }
                        }
                        is ExperienceExternalNavigationEvent.PresentWebsite -> {
                            services.experiencesClassic.webBrowserDisplay.intentForViewingWebsiteViaEmbeddedBrowser(
                                event.url.toString()
                            ).let { intent ->
                                containingContext.startActivity(intent)
                            }
                        }
                        is ExperienceExternalNavigationEvent.Custom -> {
                            TODO() // future ticket for Custom Actions
                        }
                    }
                }, { error ->
                    log.e("Error in Classic Experience view model event stream: $error, from ${error.stackTrace}")
                })
        }

        // Status bar concerns:
        var defaultStatusBarColor: Int? by remember { mutableStateOf(null) }
        LaunchedEffect(true) {
            // run once on first composition, grab the current status bar color to keep it for
            // use as default, since one you set it the default value is lost.
            defaultStatusBarColor = (containingContext as? Activity)?.window?.statusBarColor
        }

        LaunchedEffect(viewModel.backlight) {
            val activity = containingContext as? Activity
            activity?.window?.let { window ->
                if (viewModel.backlight) {
                    window.attributes = (window.attributes ?: WindowManager.LayoutParams()).apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    }
                }
            }
        }

        // whenever status bar color setting changes, set it on the host activity:
        LaunchedEffect(viewModel.toolbar) {
            val activity = containingContext as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = if (viewModel.toolbar?.useExistingStyle != false) {
                    defaultStatusBarColor ?: window.statusBarColor
                } else {
                    viewModel.toolbar?.statusBarColor ?: window.statusBarColor
                }
            }
        }

        Scaffold(
            topBar = {
                viewModel.toolbar?.let { toolbar ->
                    ExperienceToolbar(
                        toolbar = toolbar,
                        onBackButtonPressed = { viewModel.toolbarPressBack() },
                        onCloseButtonPressed = { viewModel.toolbarPressClose() }
                    )
                }
            },
            content = { paddingValues ->
                AndroidView(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    factory = { context ->
                        RenderClassicExperienceView(
                            context
                        ).apply {
                            viewModelBinding = MeasuredBindableView.Binding(
                                viewModel
                            )
                        }
                    }
                )
            }
        )
    }
}
