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

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.ui.executePendingHubNavigation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SDK-355: a push-tap deep link queues a pending navigation on the HubCoordinator before the
 * Hub ever composes. On the Hub's first frame the pending-navigation effect runs before the
 * NavHost (inside the Scaffold's subcomposed content) has set a graph on the NavController,
 * so an immediate navigate() crashes with "Navigation graph has not been set". The executor
 * must instead wait for the graph and then perform the navigation.
 */
@RunWith(RobolectricTestRunner::class)
class PendingHubNavigationTest {

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun navControllerWithoutGraph(): NavHostController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return NavHostController(context).apply {
            setLifecycleOwner(TestLifecycleOwner())
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
        }
    }

    private fun NavHostController.setHubGraph() {
        graph = createGraph(startDestination = "home") {
            composable("home") { }
            composable("inbox") { }
            composable("postDetail/{postId}") { }
            composable("conversationDetail/{conversationId}") { }
        }
    }

    @Test
    fun pendingInboxNavigationWaitsForGraphInsteadOfCrashing() = runTest {
        val navController = navControllerWithoutGraph()

        // Start executing the pending navigation before the NavHost has set a graph,
        // exactly as LaunchedEffect(pendingNavigation) does on the Hub's first frame.
        val execution = launch(start = CoroutineStart.UNDISPATCHED) {
            executePendingHubNavigation(
                navController = navController,
                nav = HubNavigation.Inbox,
                isHomeViewEnabled = true,
                isInboxEnabled = true,
            )
        }

        // No graph yet: the executor must be suspended, not crashed.
        assertThat(execution.isActive, equalTo(true))

        // The NavHost composes and sets the graph; the queued navigation should now run.
        navController.setHubGraph()
        execution.join()

        assertThat(navController.currentDestination?.route, equalTo("inbox"))
    }

    @Test
    fun pendingPostNavigationLandsOnPostDetailOnceGraphIsSet() = runTest {
        val navController = navControllerWithoutGraph()

        val execution = launch(start = CoroutineStart.UNDISPATCHED) {
            executePendingHubNavigation(
                navController = navController,
                nav = HubNavigation.Post("post-1"),
                isHomeViewEnabled = true,
                isInboxEnabled = true,
            )
        }

        navController.setHubGraph()
        execution.join()

        assertThat(navController.currentDestination?.route, equalTo("postDetail/{postId}"))
        // The inbox hop is preserved beneath the detail screen.
        assertThat(
            navController.previousBackStackEntry?.destination?.route,
            equalTo("inbox")
        )
    }

    @Test
    fun pendingConversationNavigationEncodesTheConversationId() = runTest {
        val navController = navControllerWithoutGraph()
        navController.setHubGraph()

        // Conversation IDs can contain path separators (the inbox tap path Uri-encodes them);
        // the pending navigation path must do the same or navigate() throws no-matching-destination.
        executePendingHubNavigation(
            navController = navController,
            nav = HubNavigation.Conversation("conversation/1"),
            isHomeViewEnabled = true,
            isInboxEnabled = true,
        )

        assertThat(
            navController.currentDestination?.route,
            equalTo("conversationDetail/{conversationId}")
        )
        assertThat(
            navController.currentBackStackEntry?.arguments?.getString("conversationId"),
            equalTo("conversation/1")
        )
    }

    @Test
    fun pendingNavigationWithGraphAlreadySetNavigatesImmediately() = runTest {
        val navController = navControllerWithoutGraph()
        navController.setHubGraph()

        executePendingHubNavigation(
            navController = navController,
            nav = HubNavigation.Inbox,
            isHomeViewEnabled = true,
            isInboxEnabled = true,
        )

        assertThat(navController.currentDestination?.route, equalTo("inbox"))
    }
}
