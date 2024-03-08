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

package io.rover.sdk.experiences.rich.compose.ui.layers

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.rover.sdk.experiences.rich.compose.model.nodes.Video
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.ViewID
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandMeasurePolicy

@Composable
internal fun VideoLayer(node: Video, modifier: Modifier = Modifier) {
    val collectionIndex = Environment.LocalCollectionIndex.current
    val viewID = ViewID(node.id, collectionIndex)

    MediaPlayer(
        source = node.source,
        looping = node.looping,
        autoPlay = node.autoPlay,
        showControls = node.showControls,
        resizingMode = node.resizingMode,
        removeAudio = node.removeAudio,
        posterImageURL = node.posterImageName,
        timeoutControls = true,
        viewID = viewID,
        modifier = modifier.background(Color.Black),
        layerModifiers = LayerModifiers(node),
        // video layers always expand:
        measurePolicy = ExpandMeasurePolicy(false)
    )
}
