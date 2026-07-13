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

package io.rover.sdk.notifications.communicationhub.conversations

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinkifiedTextTest {
    private fun linkify(text: String): AnnotatedString =
        linkifyReplyText(text, SpanStyle()) { }

    private fun AnnotatedString.links(): List<Pair<String, IntRange>> =
        getLinkAnnotations(0, length).map { range ->
            (range.item as LinkAnnotation.Url).url to (range.start until range.end)
        }

    @Test
    fun detectsHttpsUrl() {
        val text = "Check https://example.com/page for details"
        val annotated = linkify(text)

        assertThat(annotated.text, equalTo(text))
        val links = annotated.links()
        assertThat(links.size, equalTo(1))
        assertThat(links[0].first, equalTo("https://example.com/page"))
        val expectedStart = text.indexOf("https://example.com/page")
        assertThat(
            links[0].second,
            equalTo(expectedStart until expectedStart + "https://example.com/page".length),
        )
    }

    @Test
    fun bareDomainGetsSchemePrepended() {
        val text = "Visit rover.io today"
        val annotated = linkify(text)

        val links = annotated.links()
        assertThat(links.size, equalTo(1))
        assertThat(links[0].first, equalTo("http://rover.io"))
        val expectedStart = text.indexOf("rover.io")
        assertThat(links[0].second, equalTo(expectedStart until expectedStart + "rover.io".length))
    }

    @Test
    fun emailBecomesMailtoLink() {
        val text = "Contact support@rover.io for help"
        val annotated = linkify(text)

        val links = annotated.links()
        assertThat(links.size, equalTo(1))
        assertThat(links[0].first, equalTo("mailto:support@rover.io"))
    }

    @Test
    fun phoneNumberBecomesTelLink() {
        val text = "Call +1 555 867 5309 now"
        val annotated = linkify(text)

        val links = annotated.links()
        assertThat(links.size, equalTo(1))
        assertThat(links[0].first, equalTo("tel:+15558675309"))
    }

    @Test
    fun detectsMultipleLinksInOneString() {
        val text = "Email support@rover.io or visit https://rover.io/help"
        val annotated = linkify(text)

        assertThat(annotated.text, equalTo(text))
        val links = annotated.links().sortedBy { it.second.first }
        assertThat(links.size, equalTo(2))
        assertThat(links[0].first, equalTo("mailto:support@rover.io"))
        assertThat(links[1].first, equalTo("https://rover.io/help"))
    }

    @Test
    fun textWithoutLinksHasNoAnnotations() {
        val text = "Hello there, how are you?"
        val annotated = linkify(text)

        assertThat(annotated.text, equalTo(text))
        assertThat(annotated.links().isEmpty(), equalTo(true))
    }
}
