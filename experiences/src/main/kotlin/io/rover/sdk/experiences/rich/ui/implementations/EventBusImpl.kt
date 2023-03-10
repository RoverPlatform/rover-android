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

package io.rover.sdk.experiences.rich.ui.implementations

import io.rover.sdk.experiences.rich.core.events.EventBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EventBusImpl : EventBus {

    // / This MutableSharedFlow is the heart of the event bus itself.
    // /
    // / However, as a workaround for what we perceive as misbehaviour where the MSF appears
    // / to hang on certain events. It is unclear why.
    private val backingEventFlow = MutableSharedFlow<Any>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)

    override val eventFlow: SharedFlow<Any> = backingEventFlow.asSharedFlow()

    override suspend fun publish(event: Any) {
        backingEventFlow.emit(event)
    }
}
