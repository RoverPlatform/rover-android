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

package io.rover.sdk.core.routing

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.Rover
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Scope
import java.net.URI
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

internal const val OPEN_APP_ACTION = "io.rover.test.OPEN_APP"

/**
 * Routing runs every registered [Route], including ones from other modules and from the host app,
 * so any of them can throw. This activity is where inbound links land, and it is also where
 * NotificationOpen's fallback for a failed route ends up: that fallback is a plain ACTION_VIEW for
 * the original URI, whose intent filter points here, so the throw it swallowed arrives here next.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ThrowingLinkTestApplication::class)
class TransientLinkLaunchActivityTest {

    @Test
    fun aThrowingRouteOpensTheAppRatherThanTakingItDown() {
        val application = RuntimeEnvironment.getApplication()
        val intent = TransientLinkLaunchActivity.makeIntent(
            application,
            URI("rv-myapp://posts/post-1"),
        )

        // Reaching create() without throwing is most of the assertion: an exception from
        // intentForLink escapes onCreate and takes the process down on a link tap.
        val controller = Robolectric.buildActivity(TransientLinkLaunchActivity::class.java, intent)
            .create()

        val started = shadowOf(application).nextStartedActivity
        assertThat(started?.action, equalTo(OPEN_APP_ACTION))
        assertThat(controller.get().isFinishing, equalTo(true))
    }
}

class ThrowingLinkTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Rover.failableShared != null) return

        Rover.initialize(
            object : Assembler {
                override fun assemble(container: Container) {
                    container.register(Scope.Singleton, Intent::class.java, "openApp") { _ ->
                        Intent(OPEN_APP_ACTION)
                    }
                    container.register(Scope.Singleton, LinkOpenInterface::class.java) { _ ->
                        object : LinkOpenInterface {
                            override fun intentForLink(context: Context, uri: Uri): Intent {
                                throw IllegalStateException("a registered Route threw for $uri")
                            }

                            @Deprecated("Use intentForLink() instead.")
                            override fun localIntentForReceived(receivedUri: URI): List<Intent> {
                                throw IllegalStateException("a registered Route threw for $receivedUri")
                            }
                        }
                    }
                }
            }
        )
    }
}
