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

package io.rover.sdk.experiences.rich.compose.model.nodes

import com.squareup.moshi.JsonClass
import io.rover.sdk.experiences.rich.compose.model.values.*

@JsonClass(generateAdapter = true)
internal data class Icon(
    override val id: String,
    override val name: String? = null,
    override val metadata: Metadata? = null,
    override val opacity: Float? = null,
    override val aspectRatio: Float? = null,
    override val frame: Frame? = null,
    override val padding: Padding? = null,
    override val layoutPriority: Int? = null,
    override val offset: Point? = null,
    override val shadow: Shadow? = null,
    override val mask: Node? = null,
    override val accessibility: Accessibility? = null,
    override val overlay: Overlay? = null,
    override val background: Background? = null,
    override val action: Action? = null,
    override var children: List<Node> = emptyList(),
    override val childIDs: List<String> = emptyList(),
    val icon: NamedIcon,
    val color: ColorReference,
    val pointSize: Int,
    val border: Border? = null
) : Node {
    override val typeName = NodeType.ICON.code

    override fun setRelationships(
        nodes: Map<String, Node>,
        documentColors: Map<String, DocumentColor>,
        documentGradients: Map<String, DocumentGradient>,
        screens: Map<String, Screen>
    ) {
        super.setRelationships(nodes, documentColors, documentGradients, screens)
        color.setRelationships(documentColors)
        border?.setRelationships(documentColors)
    }
}
