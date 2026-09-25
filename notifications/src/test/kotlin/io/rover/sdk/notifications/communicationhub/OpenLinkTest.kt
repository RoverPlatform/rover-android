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

package io.rover.sdk.notifications.communicationhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.routing.LinkOpenInterface
import java.net.URI
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The router isolates throwing routes itself these days, but [openLink] is a click handler in
 * Rover UI, so it keeps its own guard as a second line of defence and has to survive a router
 * that throws at the interface: an escaping exception is a crash on a link tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenLinkTest {

    // openLink is called from Rover UI with an Activity context, and startActivity from anything
    // else needs FLAG_ACTIVITY_NEW_TASK, so drive it from a real Activity as production does.
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private val throwingLinkOpen = object : LinkOpenInterface {
        override fun intentForLink(context: Context, uri: Uri): Intent {
            throw IllegalStateException("a registered Route threw for $uri")
        }

        @Deprecated("Use intentForLink() instead.")
        override fun localIntentForReceived(receivedUri: URI): List<Intent> {
            throw IllegalStateException("a registered Route threw for $receivedUri")
        }
    }

    @Test
    fun aThrowingRouteFallsThroughToCustomTabsForAWebLink() {
        throwingLinkOpen.openLink("https://example.com/page", activity)

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started?.action, equalTo(Intent.ACTION_VIEW))
        assertThat(started?.dataString, equalTo("https://example.com/page"))
        // Custom Tabs marks its intent with the session extra; a plain ACTION_VIEW would not.
        assertThat(started?.hasExtra("android.support.customtabs.extra.SESSION"), equalTo(true))
    }

    @Test
    fun aThrowingRouteFallsThroughToActionViewForARoverLink() {
        // For a Rover scheme this ACTION_VIEW resolves back to TransientLinkLaunchActivity, which
        // guards intentForLink itself, so the throw does not simply recur one hop later.
        throwingLinkOpen.openLink("rv-myapp://posts/post-1", activity)

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started?.action, equalTo(Intent.ACTION_VIEW))
        assertThat(started?.dataString, equalTo("rv-myapp://posts/post-1"))
        assertThat(started?.hasExtra("android.support.customtabs.extra.SESSION"), equalTo(false))
    }
}
