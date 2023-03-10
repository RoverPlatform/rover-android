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

package io.rover.sdk.advertising

import android.content.Context
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.Scheduler

/**
 * Add this module to your project to include the Google Advertising Identifier in your device
 * context sent with events.
 */
class AdvertisingAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            ContextProvider::class.java,
            "advertising"
        ) { resolver ->
            AdvertisingIdContentProvider(
                resolver.resolveSingletonOrFail(Context::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java).addContextProvider(
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "advertising")
        )
    }
}
