package io.rover.sdk.core.platform

import android.content.pm.PackageInfo
import io.rover.core.BuildConfig
import java.net.URLConnection

internal fun URLConnection.setRoverUserAgent(packageInfo: PackageInfo) {
    // get the version number of the app we're embedded into (thus can't use BuildConfig for that)
    val appDescriptor = "${packageInfo.packageName}/${packageInfo.versionName}"
    val roverDescriptor = "RoverSDK/${BuildConfig.ROVER_SDK_VERSION}"
    setRequestProperty("User-Agent", "${ System.getProperty("http.agent")} $appDescriptor $roverDescriptor")
}
