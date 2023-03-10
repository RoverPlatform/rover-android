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

package io.rover.sdk.experiences.rich.compose.model.values

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import io.rover.sdk.experiences.rich.compose.model.nodes.Image

@Suppress("CanSealedSubClassBeObject")
internal sealed class PageControlStyle {

    @JsonClass(generateAdapter = true)
    class DefaultPageControlStyle : PageControlStyle()

    @JsonClass(generateAdapter = true)
    class LightPageControlStyle : PageControlStyle()

    @JsonClass(generateAdapter = true)
    class DarkPageControlStyle : PageControlStyle()

    @JsonClass(generateAdapter = true)
    class InvertedPageControlStyle : PageControlStyle()

    @JsonClass(generateAdapter = true)
    data class CustomPageControlStyle(
        val normalColor: ColorReference,
        val currentColor: ColorReference
    ) : PageControlStyle() {
        override fun setRelationships(documentColors: Map<String, DocumentColor>) {
            normalColor.setRelationships(documentColors)
            currentColor.setRelationships(documentColors)
        }
    }

    @JsonClass(generateAdapter = true)
    data class ImagePageControlStyle(
        val normalColor: ColorReference,
        val currentColor: ColorReference,
        val normalImage: Image,
        val currentImage: Image
    ) : PageControlStyle() {
        override fun setRelationships(documentColors: Map<String, DocumentColor>) {
            normalColor.setRelationships(documentColors)
            currentColor.setRelationships(documentColors)
        }
    }

    companion object {
        val PageControlStylePolyAdapterFactory: PolymorphicJsonAdapterFactory<PageControlStyle> = PolymorphicJsonAdapterFactory.of(
            PageControlStyle::class.java,
            "__caseName"
        )
            .withSubtype(DefaultPageControlStyle::class.java, PageControlStyleType.DEFAULT.code)
            .withSubtype(LightPageControlStyle::class.java, PageControlStyleType.LIGHT.code)
            .withSubtype(DarkPageControlStyle::class.java, PageControlStyleType.DARK.code)
            .withSubtype(InvertedPageControlStyle::class.java, PageControlStyleType.INVERTED.code)
            .withSubtype(CustomPageControlStyle::class.java, PageControlStyleType.CUSTOM.code)
            .withSubtype(ImagePageControlStyle::class.java, PageControlStyleType.IMAGE.code)
    }

    open fun setRelationships(
        documentColors: Map<String, DocumentColor>
    ) {
        // no-op.
    }
}

internal enum class PageControlStyleType(val code: String) {
    DEFAULT("default"),
    LIGHT("light"),
    DARK("dark"),
    INVERTED("inverted"),
    CUSTOM("custom"),
    IMAGE("image"),
}
