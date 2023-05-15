/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Clip the content to the bounds of a layer defined at this modifier.
 */
// ROVER: replaced graphicsLayer() with our own vendored version that tolerates packed intrinsics.
@Stable
internal fun Modifier.clipToBounds() = graphicsLayer(clip = true)

/**
 * Clip the content to [shape].
 *
 * @param shape the content will be clipped to this [Shape].
 */
// ROVER: replaced graphicsLayer() with our own vendored version that tolerates packed intrinsics.
@Stable
internal fun Modifier.clip(shape: Shape) = graphicsLayer(shape = shape, clip = true)