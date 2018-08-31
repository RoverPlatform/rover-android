package io.rover.core.events.contextproviders

import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.events.UserInfoInterface
import io.rover.core.events.domain.Event

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
