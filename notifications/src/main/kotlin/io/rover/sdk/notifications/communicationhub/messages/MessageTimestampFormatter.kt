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

package io.rover.sdk.notifications.communicationhub.messages

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a reply's send time as a bare time-of-day (e.g. "3:45 PM").
 *
 * Used for the per-group timestamp shown under the most recent message group. Unlike
 * [formatMessageTimestamp] this is never relative ("Yesterday", weekday names): day separation
 * is already handled by day dividers, so the in-thread timestamp always shows the clock time.
 * Mirrors the iOS group timestamp, which uses `.formatted(date: .omitted, time: .shortened)`.
 */
internal fun formatReplyClockTime(date: Date): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return formatter.format(date)
}

internal fun formatMessageTimestamp(date: Date): String {
    val now = Date()
    val dateCalendar = Calendar.getInstance().apply { time = date }
    val nowCalendar = Calendar.getInstance().apply { time = now }

    if (
        dateCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
            dateCalendar.get(Calendar.MONTH) == nowCalendar.get(Calendar.MONTH) &&
            dateCalendar.get(Calendar.DAY_OF_MONTH) == nowCalendar.get(Calendar.DAY_OF_MONTH)
    ) {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(date)
    }

    val yesterday = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_MONTH, -1)
    }
    if (
        dateCalendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            dateCalendar.get(Calendar.MONTH) == yesterday.get(Calendar.MONTH) &&
            dateCalendar.get(Calendar.DAY_OF_MONTH) == yesterday.get(Calendar.DAY_OF_MONTH)
    ) {
        return "Yesterday"
    }

    val oneWeekAgo = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_MONTH, -7)
    }
    if (date.after(oneWeekAgo.time)) {
        val formatter = SimpleDateFormat("EEEE", Locale.getDefault())
        return formatter.format(date)
    }

    if (dateCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)) {
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        return formatter.format(date)
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(date)
}

internal fun formatConversationDayDivider(date: Date): String {
    val now = Date()
    val dateCalendar = Calendar.getInstance().apply { time = date }
    val nowCalendar = Calendar.getInstance().apply { time = now }

    if (
        dateCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
            dateCalendar.get(Calendar.MONTH) == nowCalendar.get(Calendar.MONTH) &&
            dateCalendar.get(Calendar.DAY_OF_MONTH) == nowCalendar.get(Calendar.DAY_OF_MONTH)
    ) {
        return "Today"
    }

    val yesterday = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_MONTH, -1)
    }
    if (
        dateCalendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            dateCalendar.get(Calendar.MONTH) == yesterday.get(Calendar.MONTH) &&
            dateCalendar.get(Calendar.DAY_OF_MONTH) == yesterday.get(Calendar.DAY_OF_MONTH)
    ) {
        return "Yesterday"
    }

    val oneWeekAgo = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_MONTH, -7)
    }
    if (date.after(oneWeekAgo.time)) {
        val formatter = SimpleDateFormat("EEEE", Locale.getDefault())
        return formatter.format(date)
    }

    if (dateCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)) {
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        return formatter.format(date)
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(date)
}
