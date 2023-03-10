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

package io.rover.sdk.experiences.rich.compose.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.rover.sdk.experiences.rich.compose.model.nodes.Node
import io.rover.sdk.experiences.rich.compose.model.nodes.WebViewSource
import io.rover.sdk.experiences.rich.compose.model.values.*
import io.rover.sdk.experiences.rich.compose.model.values.Action
import io.rover.sdk.experiences.rich.compose.model.values.Alignment
import io.rover.sdk.experiences.rich.compose.model.values.Appearance
import io.rover.sdk.experiences.rich.compose.model.values.AssetSource
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Emphases
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.model.values.Font
import io.rover.sdk.experiences.rich.compose.model.values.FontWeight
import io.rover.sdk.experiences.rich.compose.model.values.GradientReference
import io.rover.sdk.experiences.rich.compose.model.values.MaxHeight
import io.rover.sdk.experiences.rich.compose.model.values.MaxWidth
import io.rover.sdk.experiences.rich.compose.model.values.MenuItemVisibility
import io.rover.sdk.experiences.rich.compose.model.values.OffsetJsonAdapter
import io.rover.sdk.experiences.rich.compose.model.values.PageControlStyle
import io.rover.sdk.experiences.rich.compose.model.values.Predicate
import io.rover.sdk.experiences.rich.compose.model.values.ResizingMode
import io.rover.sdk.experiences.rich.compose.model.values.StatusBarStyle
import io.rover.sdk.experiences.rich.compose.model.values.TextAlignment
import io.rover.sdk.experiences.rich.compose.model.values.TextTransform
import io.rover.sdk.experiences.rich.compose.model.values.VideoResizingMode

internal object JsonParser {
    val moshi: Moshi = Moshi.Builder()
        // Polymorphic model adapters.
        .add(Node.NodePolyAdapterFactory)
        .add(ColorReference.ColorReferencePolyAdapterFactory)
        .add(Font.FontPolyAdapterFactory)
        .add(PageControlStyle.PageControlStylePolyAdapterFactory)
        .add(Action.ActionPolyAdapterFactory)
        .add(AssetSource.AssetSourcePolyAdapterFactory)
        .add(GradientReference.GradientReferencePolyAdapterFactory)
        .add(Fill.FillPolyAdapterFactory)
        .add(SegueStyle.SegueStylePolyAdapterFactory)
        .add(WebViewSource.WebViewSourcePolyAdapterFactory)
        // Model adapters.
        .add(SeguePresentationStyleType.SeguePresentationStyleAdapter())
        .add(FontWeight.FontWeightJsonAdapter())
        .add(Emphases.EmphasesAdapter())
        .add(Appearance.AppearanceAdapter())
        .add(MaxHeight.MaxHeightJsonAdapter())
        .add(MaxWidth.MaxWidthJsonAdapter())
        .add(Alignment.AlignmentJsonAdapter())
        .add(VideoResizingMode.VideoResizingModeAdapter())
        .add(TextAlignment.TextAlignmentJsonAdapter())
        .add(ResizingMode.PatternTypeJsonAdapter())
        .add(Axis.ScrollAxisJsonAdapter())
        .add(TextTransform.TextTransformAdapter())
        .add(StatusBarStyle.StatusBarStyleJsonAdapter())
        .add(MenuItemVisibility.MenuItemVisibilityJsonAdapter())
        .add(HttpMethod.HttpMethodJsonAdapter())
        .add(Predicate.PredicateJsonAdapter())
        .add(ShareExperienceActionTypes.ShareExperienceJsonAdapter())
        .add(Authorizer.AuthorizerMethodAdapter())
        //        .add(analyticsEventAdapter)
        .add(OffsetJsonAdapter())
        .add(DimensionsJsonAdapter())
        .build()

    private val adapter = moshi.adapter(ExperienceModel::class.java)

    fun parseExperience(json: String): ExperienceModel? {
        return adapter.fromJson(json)
    }

//    fun parseAnalyticsEvents(json: String): List<AnalyticsEvent> {
//        val parameterizedType = Types.newParameterizedType(
//            MutableList::class.java,
//            AnalyticsEvent::class.java
//        )
//        val adapter: JsonAdapter<List<AnalyticsEvent>> = moshi.adapter(parameterizedType)
//
//        return adapter.fromJson(json) ?: listOf()
//    }

    fun parseDictionaryMap(json: String): Map<String, Any> {
        val adapter: JsonAdapter<Map<String, Any>> = io.rover.sdk.experiences.rich.compose.data.JsonParser.moshi
            .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
        return adapter.fromJson(json) ?: emptyMap()
    }

    fun encodeDictionaryMap(map: Map<String, Any>): String {
        val adapter: JsonAdapter<Map<String, Any>> = io.rover.sdk.experiences.rich.compose.data.JsonParser.moshi
            .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
        return adapter.toJson(map)
    }

//    fun encodeAnalyticsEvents(events: List<AnalyticsEvent>): String {
//        val parameterizedType = Types.newParameterizedType(
//            MutableList::class.java,
//            AnalyticsEvent::class.java
//        )
//        val adapter: JsonAdapter<List<AnalyticsEvent>> = moshi.adapter(parameterizedType)
//        return adapter.toJson(events)
//    }
}
