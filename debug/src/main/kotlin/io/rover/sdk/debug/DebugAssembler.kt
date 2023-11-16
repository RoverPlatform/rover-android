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

@file:JvmName("Debug")

package io.rover.sdk.debug

import android.content.Context
import io.rover.sdk.core.Rover
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.routing.Router
import io.rover.sdk.debug.routes.DebugRoute

/**
 * The Debug module adds certain useful bits of debug functionality to the Rover SDK, namely a
 * new `isTestDevice` boolean to each event that is tracked through the EventQueue and a hidden
 * activity for managing its value.
 *
 * Note that it may safely be used in production as well as dev builds of your app, but it is
 * optional should you want to be extra sure that debug functionality cannot be inadvertently
 * exposed.
 */
class DebugAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            DebugPreferences::class.java,
        ) { resolver ->
            DebugPreferences(
                resolver.resolveSingletonOrFail(Context::class.java),
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(Router::class.java).apply {
            registerRoute(
                DebugRoute(
                    resolver.resolveSingletonOrFail(Context::class.java),
                ),
            )
        }

        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            .addContextProvider(
                TestDeviceContextProvider(
                    resolver.resolveSingletonOrFail(DebugPreferences::class.java),
                ),
            )
    }
}

val Rover.debugPreferences: DebugPreferences
    get() = this.resolve(DebugPreferences::class.java) ?: throw missingDependencyError("DebugPreferences")

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include DebugAssembler() in the assembler list?")
}
