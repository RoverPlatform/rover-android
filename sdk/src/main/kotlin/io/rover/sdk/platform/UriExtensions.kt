@file:JvmName("UriExtensions")

package io.rover.sdk.platform

import android.net.Uri
import java.net.URI

fun URI.asAndroidUri(): Uri = Uri.parse(this.toString())