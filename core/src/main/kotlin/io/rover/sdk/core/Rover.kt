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

package io.rover.sdk.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.rover.core.BuildConfig
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.ContainerResolver
import io.rover.sdk.core.container.InjectionContainer
import io.rover.sdk.core.logging.AndroidLogger
import io.rover.sdk.core.logging.GlobalStaticLogHolder
import io.rover.sdk.core.logging.LogBuffer
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.LinkOpenInterface

/**
 * Entry point for the Rover SDK.
 *
 * The Rover SDK consists of several discrete modules, which each offer a major vertical
 * (eg. Experiences and Location) of the Rover Platform.  It's up to you to select which
 * are appropriate to activate in your app.
 *
 * TODO: exhaustive usage information.
 *
 * Serves as a dependency injection container for the various components (modules) of the Rover SDK.
 */
class Rover(
    assemblers: List<Assembler>
) : ContainerResolver by InjectionContainer(assemblers) {
    init {
        // global, which we "inject" using static scope
        GlobalStaticLogHolder.globalLogEmitter =
            LogBuffer(
                // uses the resolver to discover when the EventQueueService is ready and can be used
                // to submit the logs.
                AndroidLogger()
            )

        initializeContainer()
    }

    // And here are several helper routines for common tasks, easily discoverable and
    // allowing the user to avoid doing container lookups.

    /**
     * If Rover can handle this link, returns an intent that can launch it.
     *
     * Returns null if this link not handled by Rover.
     */
    fun intentForLink(uri: Uri): Intent? = shared.resolveSingletonOrFail(LinkOpenInterface::class.java).intentForLink(
        shared.resolveSingletonOrFail(Context::class.java),
        uri
    )

    val associatedDomains: List<String>
        get() = shared.resolveSingletonOrFail(UrlSchemes::class.java).associatedDomains

    // And here is the singleton logic:

    companion object {
        // we have a global singleton of the Rover container.
        private var sharedInstanceBackingField: Rover? = null

        /**
         * Access the Rover singleton.
         */
        @JvmStatic
        val shared: Rover
            get() = sharedInstanceBackingField ?: throw RuntimeException(
                "Rover shared instance accessed before calling initialize.\n\n" +
                    "Did you remember to call Rover.initialize() in your Application.onCreate()?"
            )

        /**
         * This is a failable version of [Rover.sharedInstance], which you can use to test if
         * Rover is available if you want to avoid a crash if Rover is not initialized.
         */
        val failableShared: Rover?
            get() = sharedInstanceBackingField ?: log.w(
                "Rover shared instance accessed before calling initialize.\n\n" +
                        "Did you remember to call Rover.initialize() in your Application.onCreate()?"
            ).let { null }

        val sdkVersion: String
            get() = BuildConfig.ROVER_SDK_VERSION

        @JvmStatic
        fun initialize(vararg assemblers: Assembler) {
            val rover = Rover(assemblers.asList())
            if (sharedInstanceBackingField != null) {
                throw RuntimeException("Rover SDK is already initialized.  This is most likely a bug.")
            }
            sharedInstanceBackingField = rover
            log.i("Started Rover Android SDK v${BuildConfig.ROVER_SDK_VERSION}.")
        }


        @Deprecated("No longer needed, and may be safely removed.")
        @JvmStatic
        fun installSaneGlobalHttpCache(applicationContext: Context) {
            // no-op
        }
    }
}
