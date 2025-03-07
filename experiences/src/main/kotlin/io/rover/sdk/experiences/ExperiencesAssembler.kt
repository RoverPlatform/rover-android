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
import android.content.Intent
import android.net.Uri
import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.toArgb
import com.squareup.moshi.JsonReader.Token
import io.rover.sdk.core.Rover
import io.rover.sdk.core.UrlSchemes
import io.rover.sdk.core.authenticationContext
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.data.http.NetworkClient
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.experiences.data.URLRequest
import io.rover.sdk.experiences.services.ClassicEventEmitter
import io.rover.sdk.experiences.services.ContextProviderService
import io.rover.sdk.experiences.services.EventEmitter
import io.rover.sdk.experiences.services.InterpolatedConversionsTrackerService
import io.rover.sdk.experiences.services.ModularContextProvider
import startListening

/**
 * Location Assembler contains the Rover SDK subsystems for Geofence, Beacon, and location tracking.
 *
 * It can automatically use the Google Location services, which you can opt out of by passing false
 * to the following boolean parameters.  You may wish to do this if you want to use a Location SDK
 * from a vendor other than Google, integrate with your own location implementation, or do not
 * require the functionality.
 *
 * Note: if you use any of the below, then you must complete the Google Play Services setup as per
 * the SDK documentation (also needed for the Notifications module).
 *
 * @param appThemeDescription Customize the appearance of the app bar presented in Classic
 * Experiences.
 * @param experienceIntent The intent to launch when an experience is opened. Defaults to use the
 * built-in [ExperienceActivity].  Set this value if you wish to replace [ExperienceActivity] with
 * your own subclass
 */
class ExperiencesAssembler(
    private val appThemeDescription: AppThemeDescription = AppThemeDescription(),
    private val experienceIntent: (Context, Uri) -> Intent = { context, uri -> ExperienceActivity.makeIntent(context, uri) }
) : Assembler {
    // TODO: here are some judo parameters that are probably needed:
    //    val experienceCacheSize: Long = Environment.Sizes.EXPERIENCE_CACHE_SIZE,
    //    val imageCacheSize: Long = Environment.Sizes.IMAGE_CACHE_SIZE,
    //    var authorizers: List<Authorizer> = emptyList()

    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            EventEmitter::class.java
        ) { resolver ->
            EventEmitter(
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            RoverExperiencesClassic::class.java
        ) { resolver ->
            RoverExperiencesClassic(
                resolver.resolveSingletonOrFail(Application::class.java),
                resolver.resolveSingletonOrFail(String::class.java, "accountToken"),
                resolver.resolveSingletonOrFail(Int::class.java, "chromeTabBackgroundColor"),
                resolver.resolveSingletonOrFail(GraphQlApiServiceInterface::class.java),
                appThemeDescription = appThemeDescription,
                networkClient = resolver.resolveSingletonOrFail(NetworkClient::class.java),
            )
        }

        container.register(
            Scope.Singleton,
            Authorizers::class.java
        ) { _ ->
            Authorizers()
        }

        container.register(
            Scope.Singleton,
            ContextProviderService::class.java
        ) { _ ->
            ModularContextProvider()
        }

        container.register(
            Scope.Singleton,
            InterpolatedConversionsTrackerService::class.java
        ) { resolver ->
            InterpolatedConversionsTrackerService(
                resolver.resolveSingletonOrFail(ConversionsTrackerService::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        super.afterAssembly(resolver)
        resolver.resolveSingletonOrFail(Router::class.java).apply {
            val urlSchemes = resolver.resolveSingletonOrFail(UrlSchemes::class.java)
            val context = resolver.resolveSingletonOrFail(Context::class.java)
            registerRoute(
                PresentExperienceRoute(
                    context = context,
                    urlSchemes = urlSchemes.schemes,
                    associatedDomains = urlSchemes.associatedDomains,
                    experienceIntent = experienceIntent
                )
            )
        }

        // Add the context providers to the context provider service, for use with string interpolation
        val contextProvider = resolver.resolveSingletonOrFail(ContextProviderService::class.java)
        listOf(
                resolver.resolveSingletonOrFail(PrivacyService::class.java),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "device"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "locale"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "darkMode"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "reachability"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "screen"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "telephony"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "timeZone"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "attributes"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "application"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "deviceIdentifier"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "sdkVersion"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "locationAuthorization"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "conversions"),
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "lastSeen"),
        ).forEach { contextProvider.addContextProvider(it) }

        resolver.resolve(ContextProvider::class.java, "location").whenNotNull { locationContextProvider ->
            contextProvider.addContextProvider(locationContextProvider)
        }

        resolver.resolve(ContextProvider::class.java, "notification").whenNotNull { notificationContextProvider ->
            contextProvider.addContextProvider(notificationContextProvider)
        }

        /**
         * Attempt to retrieve the [ClassicEventEmitter] instance from the rover sdk in order to receive
         * events for analytics and automation purposes.
         */
        val classicEventEmitter = resolver.resolve(RoverExperiencesClassic::class.java)?.classicEventEmitter

        classicEventEmitter?.let {
            ClassicEventDispatcher(
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            ).startListening(it)

            resolver.resolveSingletonOrFail(ConversionsTrackerService::class.java).startListening(it)
        }
            ?: log.w("A Rover SDK event emitter wasn't available; Rover events will not be tracked.  Make sure you call Rover.initialize() before initializing the Rover SDK.")
    }
}

/**
 * Call this method to register a callback that can mutate outgoing HTTP requests to
 * Data Source APIs being used in Experiences.
 *
 * Use this to add your own custom authentication headers for API keys, etc.
 */
fun Rover.authorize(pattern: String, callback: (URLRequest) -> Unit) {
    this.resolveSingletonOrFail(Authorizers::class.java).registerAuthorizer(pattern, callback)
}

/**
 * Call this method to register a callback that can mutate outgoing HTTP requests to
 * Data Source APIs being used in Experiences. This version can accept a suspend function,
 * allowing you to do perform an async task to acquire credentials.
 *
 * Use this to add your own custom authentication headers for API keys, etc.
 */
fun Rover.authorizeAsync(pattern: String, callback: suspend (URLRequest) -> Unit) {
    this.resolveSingletonOrFail(Authorizers::class.java).registerAuthorizer(pattern, callback)
}

@Deprecated("If possible, migrate to using Rover.shared.registerScreenViewedCallback { }", ReplaceWith("Rover.shared.registerScreenViewedCallback"))
val Rover.classicEventEmitter: ClassicEventEmitter
    get() = resolveSingletonOrFail(RoverExperiencesClassic::class.java).classicEventEmitter

/**
 * A set of colors that describe the theme of your application.
 *
 * Used for providing default styling App Bars set to Auto in Rover
 * Classic Experiences.
 *
 * If unset, defaults to stock Material Design Components colors.
 *
 * Note: if you are embedding an Experience within your own UI,
 * the Jetpack Compose Material theme settings will apply instead of these.
 */
data class AppThemeDescription(
    val lightColors: ThemeColors = ThemeColors(lightColors()),
    val darkColors: ThemeColors = ThemeColors(darkColors())
) {
    constructor(
        lightColors: Colors,
        darkColors: Colors
    ) : this(
        lightColors = ThemeColors(lightColors),
        darkColors = ThemeColors(darkColors)
    )

    data class ThemeColors(
        val primary: Int,
        val onPrimary: Int
    ) {
        constructor(
            composeColors: Colors
        ) : this(
            composeColors.primary.toArgb(),
            composeColors.onPrimary.toArgb()
        )
    }
}
