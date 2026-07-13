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

package io.rover.sdk.notifications.communicationhub.posts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.experiences.rich.compose.ui.LocalExternalNavController
import io.rover.sdk.notifications.communicationhub.ui.ClearHubRouteVisibilityOnDispose
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigationState
import io.rover.sdk.notifications.communicationhub.openLink
import io.rover.sdk.notifications.communicationhub.ui.reportHubRouteVisibility
import androidx.compose.ui.platform.LocalContext

/**
 * Post detail route that fetches a post by ID and displays its details.
 */
@Composable
internal fun PostDetailRoute(
    postId: String,
    postsRepository: PostsRepository,
    hubCoordinator: HubCoordinator,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    linkOpen: LinkOpenInterface?
) {
    val context = LocalContext.current
    val navController = LocalExternalNavController.current
    val navigationState = HubNavigationState.ShowingPost(postId)
    val routeVisibilityModifier = Modifier.reportHubRouteVisibility(hubCoordinator, navigationState)
    ClearHubRouteVisibilityOnDispose(hubCoordinator, navigationState)

    val post by androidx.compose.runtime.produceState<PostWithSubscription?>(
        initialValue = null,
        key1 = postId
    ) {
        value = postsRepository.getPostWithSubscriptionById(postId)
    }

    post?.let { postWithSubscription ->
        PostDetail(
            post = postWithSubscription,
            postsRepository = postsRepository,
            onBackClick = { navController?.popBackStack() },
            onOpenUrl = { url -> linkOpen?.openLink(url, context) },
            modifier = routeVisibilityModifier.padding(contentPadding).consumeWindowInsets(contentPadding)
        )
    }
}
