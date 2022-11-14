package io.rover.sdk.core.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.domain.Event

/**
 * Allows you to include arbitrary attributes (name/value pairs) within the [DeviceContext] sent
 * alongside [Event]s.
 *
 * See [UserInfoInterface.update].
 */
class UserInfoContextProvider(
    private val userInfo: UserInfoInterface
) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            userInfo = userInfo.currentUserInfo
        )
    }
}
