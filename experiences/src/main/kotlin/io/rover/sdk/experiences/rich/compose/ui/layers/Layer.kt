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

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import io.rover.sdk.experiences.rich.compose.model.nodes.*
import io.rover.sdk.experiences.rich.compose.model.nodes.Collection
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.HStackLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.VStackLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.ZStackLayer

@Composable
internal fun Layer(node: Node, modifier: Modifier) {
    val tag = "RoverExperiences.Layer"

    CompositionLocalProvider(Environment.LocalNode provides node) {
        when (node) {
            is Collection -> CollectionLayer(node, modifier)
            is Conditional -> ConditionalLayer(node, modifier)
            is DataSource -> DataSourceLayer(node, modifier)
            is Audio -> AudioLayer(node, modifier)
            is Carousel -> CarouselLayer(node, modifier)
            is Divider -> DividerLayer(node, modifier)
            is HStack -> HStackLayer(node, modifier)
            is VStack -> VStackLayer(node, modifier)
            is ZStack -> ZStackLayer(node, modifier)
            is Icon -> IconLayer(node, modifier)
            is Image -> ImageLayer(node, modifier)
            is PageControl -> PageControlLayer(node, modifier)
            is Rectangle -> RectangleLayer(node, modifier)
            is ScrollContainer -> ScrollContainerLayer(node, modifier)
            is Spacer -> SpacerLayer(node, modifier)
            is Text -> TextLayer(node, modifier)
            is Video -> VideoLayer(node, modifier)
            is WebView -> WebViewLayer(node, modifier)
            is Screen -> Log.e(tag, "Tried to Compose a ScreenLayer through ChildrenComposable. These should be handled by navigation.")
            is AppBar -> Log.v(tag, "Tried to Compose an AppBarLayer through ChildrenComposable. Ignoring.")
            else -> Log.e(tag, "Unable to find Composable for Node: ${node.javaClass.simpleName}")
        }
    }
}
