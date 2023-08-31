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

package io.rover.sdk.seatgeek

import android.app.Application
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.rover.sdk.core.Rover
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.LocalStorage

class SeatGeekAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            SeatGeekAuthorizer::class.java
        ) { resolver ->
            resolver.resolveSingletonOrFail(SeatGeekManager::class.java)
        }

        container.register(
            Scope.Singleton,
                SeatGeekManager::class.java
        ) { resolver ->
            SeatGeekManager(
                resolver.resolveSingletonOrFail(UserInfoInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }
    }
}

val Rover.seatGeekAuthorizer: SeatGeekAuthorizer
    get() = Rover.shared.resolveSingletonOrFail(SeatGeekAuthorizer::class.java)
