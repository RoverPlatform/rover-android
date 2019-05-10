package io.rover.sdk.platform

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Date.dateAsIso8601(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(this)
    } else {
        // On legacy Android, we are using the RFC 822 (email) vs ISO 8601 date format, and
        // we use the following regex to transform it to something 8601 compatible.
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(this)
            .replace(Regex("(\\d\\d)(\\d\\d)$"), "$1:$2")
    }
}