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

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.experiences.Authorizers
import io.rover.sdk.experiences.RoverExperiencesClassic
import io.rover.sdk.experiences.data.http.RoverExperiencesWebService
import io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient
import io.rover.sdk.experiences.rich.compose.data.LargeImageInterceptor
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader
import io.rover.sdk.experiences.services.EventEmitter

/**
 * Various singletons and services.
 */
internal data class Services(
    val context: Context,
    /**
     * The Rover singleton.
     */
    val rover: Rover
) {
    val httpClient: ExperiencesHttpClient by lazy {
        ExperiencesHttpClient(context)
    }

    val webService: RoverExperiencesWebService by lazy {
        RoverExperiencesWebService.make(httpClient)
    }

    val fontLoader: FontLoader by lazy {
        FontLoader(context, httpClient)
    }

    // and some accessors for pulling a few things out of the Rover container:

    val experiencesClassic: RoverExperiencesClassic
        get() = rover.resolveSingletonOrFail(RoverExperiencesClassic::class.java)

    val eventQueue: EventQueueServiceInterface
        get() = rover.resolveSingletonOrFail(EventQueueServiceInterface::class.java)

    val eventEmitter: EventEmitter
        get() = rover.resolveSingletonOrFail(EventEmitter::class.java)

    val authorizers: Authorizers
        get() = rover.resolveSingletonOrFail(Authorizers::class.java)

    val authenticationContext: AuthenticationContextInterface
        get() = rover.resolveSingletonOrFail(AuthenticationContextInterface::class.java)

    /**
     * Wherever needed, we use this loader instead of setting it through Coil.setImageLoader, as that
     * may override customer set ImageLoader settings.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                //Prevent large images from crashing the app, see: https://github.com/coil-kt/coil/issues/1349
                //and https://issuetracker.google.com/issues/244854452?pli=1
                //Simply limiting based on screen width and height seems to cause issues,
                //so we're using the number provided in the coil thread.
                add(LargeImageInterceptor(
                        maxWidth = 5000,
                        maxHeight = 5000
                ))
            }
            .okHttpClient {
                httpClient.client
            }
            .build()
    }

    companion object {
        /**
         * The purpose of this composable is to construct the graph of services
         * and their dependencies. [Services] is then available via [Environment.LocalServices].
         *
         * If it turns out to have already been initialized in the current environment, then
         * nothing is done. This behaviour exists to account for the multiple entry
         * points for Rover composables in the public API.
         *
         * For convenience, it also makes Services available as a parameter to the nested composable.
         */
        @Composable
        fun Inject(
            content: @Composable (Services) -> Unit
        ) {
            val rover = Rover.shared
            
            val currentServices = Environment.LocalServices.current
            if (currentServices == null) {
                val services = Services(LocalContext.current, rover)
                CompositionLocalProvider(
                    Environment.LocalServices provides services
                ) {
                    content(services)
                }
            } else {
                content(currentServices)
            }
        }
    }
}
