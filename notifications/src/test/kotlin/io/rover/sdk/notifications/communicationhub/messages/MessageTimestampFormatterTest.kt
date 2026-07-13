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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageTimestampFormatterTest {
    private val originalTimeZone = TimeZone.getDefault()
    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatsTodayDivider() {
        assertThat(formatConversationDayDivider(Date()), equalTo("Today"))
    }

    @Test
    fun formatsYesterdayDivider() {
        assertThat(formatConversationDayDivider(daysAgo(1)), equalTo("Yesterday"))
    }

    @Test
    fun formatsWeekdayDividerForRecentDates() {
        val date = daysAgo(3)
        val expected = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        assertThat(formatConversationDayDivider(date), equalTo(expected))
    }

    @Test
    fun formatsSameYearDividerWithMonthAndDay() {
        val date = daysAgo(30)
        val expected = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        assertThat(formatConversationDayDivider(date), equalTo(expected))
    }

    @Test
    fun formatsOlderDividerWithIsoDate() {
        val date = daysAgo(400)
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        assertThat(formatConversationDayDivider(date), equalTo(expected))
    }

    private fun daysAgo(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }

}
