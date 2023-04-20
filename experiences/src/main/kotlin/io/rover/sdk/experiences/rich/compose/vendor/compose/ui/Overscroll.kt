/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rover.sdk.experiences.rich.compose.vendor.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Renders overscroll from the provided [overscrollEffect].
 *
 * This modifier is a convenience method to call [OverscrollEffect.effectModifier], which
 * renders the actual effect. Note that this modifier is only responsible for the visual part of
 * overscroll - on its own it will not handle input events. In addition to using this modifier you
 * also need to propagate events to the [overscrollEffect], most commonly by using a
 * [androidx.compose.foundation.gestures.scrollable].
 *
 * @sample androidx.compose.foundation.samples.OverscrollSample
 *
 * @param overscrollEffect the [OverscrollEffect] to render
 */
@ExperimentalFoundationApi
internal fun Modifier.overscroll(overscrollEffect: OverscrollEffect): Modifier =
    this.then(overscrollEffect.effectModifier)


// ROVER: see AndroidOverScroll.kt, this method is defined there. expect/actual is a forward
//   declaration mechanism that Compose is using to have these in separate modules, but we're
//   building them together so this declaration is not needed.

//@OptIn(ExperimentalFoundationApi::class)
//@Composable
//internal expect fun rememberOverscrollEffect(): OverscrollEffect

@OptIn(ExperimentalFoundationApi::class)
internal object NoOpOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset = performScroll(delta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) { performFling(velocity) }

    override val isInProgress: Boolean
        get() = false

    override val effectModifier: Modifier
        get() = Modifier
}
