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

package io.rover.sdk.core.privacy

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * This object is responsible for the global privacy settings of the Rover SDK.
 */
class PrivacyService(
    private val localStorage: LocalStorage,

) : ContextProvider {

    private val _trackingEnabledFlow: MutableStateFlow<TrackingMode> = MutableStateFlow(
        localStorage.getKeyValueStorageFor("PrivacyService")["trackingMode"]?.let { stringValue ->
            TrackingMode.values().singleOrNull() { it.wireFormat == stringValue }
        } ?: TrackingMode.Default,
    )

    var trackingModeFlow: SharedFlow<TrackingMode> = _trackingEnabledFlow.asSharedFlow()

    enum class TrackingMode(
        val wireFormat: String,
    ) {
        Default("default"),
        Anonymized("anonymized"),
    }

    var trackingMode: TrackingMode
        get() {
            return _trackingEnabledFlow.value
        }
        set(value) {
            val oldValue = _trackingEnabledFlow.value
            localStorage.getKeyValueStorageFor("PrivacyService")["trackingMode"] =
                value.wireFormat
            _trackingEnabledFlow.value = value
            log.i("Privacy tracking mode changed from ${oldValue.wireFormat} to ${value.wireFormat}")
            listeners.forEach { it.onTrackingModeChanged(value) }
        }

    private var listeners = mutableListOf<TrackingEnabledChangedListener>()

    fun registerTrackingEnabledChangedListener(listener: TrackingEnabledChangedListener) {
        listeners.add(listener)
        listener.onTrackingModeChanged(trackingMode)
    }

    interface TrackingEnabledChangedListener {
        fun onTrackingModeChanged(trackingMode: TrackingMode)
    }

    fun refreshAllListeners() {
        listeners.forEach { it.onTrackingModeChanged(trackingMode) }
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            trackingMode = trackingMode.wireFormat,
        )
    }
}
