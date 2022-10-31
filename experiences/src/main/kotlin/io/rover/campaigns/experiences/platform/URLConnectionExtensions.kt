package io.rover.campaigns.experiences.platform

import android.content.pm.PackageInfo
import io.rover.campaigns.experiences.BuildConfig
import java.net.URLConnection

internal fun URLConnection.setRoverUserAgent(packageInfo: PackageInfo) {
    // get the version number of the app we're embedded into (thus can't use BuildConfig for that)
    val appDescriptor = "${packageInfo.packageName}/${packageInfo.versionName}"
    val roverDescriptor = "RoverSDK/${BuildConfig.VERSION_NAME}"
    setRequestProperty("User-Agent", "${ System.getProperty("http.agent")} $appDescriptor $roverDescriptor")
}
