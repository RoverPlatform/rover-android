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

package io.rover.sdk.core.platform

import android.os.Build
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface DateFormattingInterface {
    fun dateAsIso8601(date: Date, localTime: Boolean = false): String

    fun iso8601AsDate(iso8601Date: String, localTime: Boolean = false): Date
}

class DateFormatting : DateFormattingInterface {
    // we must construct the date format objects lazily for each request, because they are not thread safe.
    private fun format8601() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun format8601WithTimeZone() = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    ) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US) else {
        // on legacy Android use the RFC 822 format, and below for outgoing values we'll transform
        // it to 8601.
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    }

    override fun dateAsIso8601(date: Date, localTime: Boolean): String =
        if (localTime) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Using the new 'XXX' date formatter above.
                format8601WithTimeZone().format(date)
            } else {
                // On legacy Android, we are using the RFC 822 (email) vs ISO 8601 date format, and
                // we use the following regex to transform it to something 8601 compatible.
                format8601WithTimeZone().format(
                    date
                ).replace(Regex("(\\d\\d)(\\d\\d)$"), "$1:$2")
            }
        } else format8601().format(date)

    override fun iso8601AsDate(iso8601Date: String, localTime: Boolean): Date = try {
        // ISO 8601 allows specifying UTC as 'Z', but as per above our formatter for local time (aka
        // timestamps with explicit time zones) may not support it depending on the version of
        // Android in use. So if it's present transform it to be +0000 which can be parsed by either
        // Z or XXX.
        if (localTime) format8601WithTimeZone().parse(iso8601Date.replace(Regex("Z$"), "+0000")) else format8601().parse(iso8601Date)
    } catch (throwable: Throwable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            throw JSONException("Could not parse date '$iso8601Date' as ${if (localTime) "local" else "utc"}", throwable)
        } else {
            throw JSONException("Could not parse date '$iso8601Date' as ${if (localTime) "local" else "utc"}, because ${throwable.message}")
        }
    }
}
