package io.rover.campaigns.core.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

/**
 * Add application name and version details to [DeviceContext]s.
 */
class ApplicationContextProvider(
    applicationContext: android.content.Context
) : ContextProvider {
    private val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)!!

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            appIdentifier = packageInfo.packageName,
            appVersion = packageInfo.versionName,
            appBuild = packageInfo.versionCode.toString()
        )
    }
}