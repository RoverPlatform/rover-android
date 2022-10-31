@file:JvmName("UriExtensions")

package io.rover.campaigns.experiences.platform

import android.net.Uri
import java.net.URI

internal fun URI.asAndroidUri(): Uri = Uri.parse(this.toString())