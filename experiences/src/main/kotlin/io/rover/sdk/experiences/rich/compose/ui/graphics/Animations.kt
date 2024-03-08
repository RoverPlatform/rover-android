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

package io.rover.sdk.experiences.rich.compose.ui.graphics

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

private object Duration {
    const val NAVIGATION = 300
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition =   slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(Duration.NAVIGATION))


internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(Duration.NAVIGATION))


internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition(): EnterTransition = slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(Duration.NAVIGATION))


internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition(): ExitTransition = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(Duration.NAVIGATION))
