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

package io.rover.sdk.core.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.platform.DeviceIdentificationInterface

class DeviceIdentifierContextProvider(
    private val deviceIdentification: DeviceIdentificationInterface
) : ContextProvider {
    private val identifier get() = deviceIdentification.installationIdentifier
    private val deviceName get() = deviceIdentification.deviceName

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            deviceIdentifier = identifier,
            deviceName = deviceName
        )
    }
}
