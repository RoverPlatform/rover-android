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

@file:JvmName("Experience")

package io.rover.sdk.core.data.graphql.operations.data

import android.net.Uri
import io.rover.sdk.core.data.domain.Background
import io.rover.sdk.core.data.domain.BackgroundContentMode
import io.rover.sdk.core.data.domain.BackgroundScale
import io.rover.sdk.core.data.domain.Barcode
import io.rover.sdk.core.data.domain.BarcodeBlock
import io.rover.sdk.core.data.domain.BarcodeFormat
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.Border
import io.rover.sdk.core.data.domain.ButtonBlock
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Color
import io.rover.sdk.core.data.domain.Conversion
import io.rover.sdk.core.data.domain.Duration
import io.rover.sdk.core.data.domain.DurationUnit
import io.rover.sdk.core.data.domain.Font
import io.rover.sdk.core.data.domain.FontWeight
import io.rover.sdk.core.data.domain.Height
import io.rover.sdk.core.data.domain.HorizontalAlignment
import io.rover.sdk.core.data.domain.Image
import io.rover.sdk.core.data.domain.ImageBlock
import io.rover.sdk.core.data.domain.ImagePoll
import io.rover.sdk.core.data.domain.ImagePollBlock
import io.rover.sdk.core.data.domain.ImagePollBlockOption
import io.rover.sdk.core.data.domain.Insets
import io.rover.sdk.core.data.domain.Position
import io.rover.sdk.core.data.domain.RectangleBlock
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.data.domain.StatusBar
import io.rover.sdk.core.data.domain.StatusBarStyle
import io.rover.sdk.core.data.domain.Text
import io.rover.sdk.core.data.domain.TextAlignment
import io.rover.sdk.core.data.domain.TextBlock
import io.rover.sdk.core.data.domain.TextPoll
import io.rover.sdk.core.data.domain.TextPollBlock
import io.rover.sdk.core.data.domain.TextPollOption
import io.rover.sdk.core.data.domain.TitleBar
import io.rover.sdk.core.data.domain.TitleBarButtons
import io.rover.sdk.core.data.domain.UnitOfMeasure
import io.rover.sdk.core.data.domain.VerticalAlignment
import io.rover.sdk.core.data.domain.WebView
import io.rover.sdk.core.data.domain.WebViewBlock
import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.graphql.getStringIterable
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.data.graphql.safeGetUri
import io.rover.sdk.core.data.graphql.safeOptString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun ClassicExperienceModel.Companion.decodeJson(json: JSONObject, sourceUrl: Uri? = null): ClassicExperienceModel {
    var url: Uri? = null

    if (sourceUrl != null) {
        url = sourceUrl
    } else {
        val sourceUrlString = json.optString("sourceUrl")
        if (sourceUrlString.isNotEmpty()) {
            url = Uri.parse(sourceUrlString)
        }
    }

    return ClassicExperienceModel(
        id = json.safeGetString("id"),
        homeScreenId = json.safeGetString("homeScreenID"),
        screens = json.getJSONArray("screens").getObjectIterable().map {
            Screen.decodeJson(it)
        },
        keys = json.getJSONObject("keys").toStringHash(),
        tags = json.getJSONArray("tags").getStringIterable().toList(),
        name = json.safeGetString("name"),
        sourceUrl = url
    )
}

fun ClassicExperienceModel.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, ClassicExperienceModel::id) { it }
        putProp(this@encodeJson, ClassicExperienceModel::homeScreenId, "homeScreenID") { it }
        putProp(this@encodeJson, ClassicExperienceModel::screens) { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, ClassicExperienceModel::keys) { JSONObject(it) }
        putProp(this@encodeJson, ClassicExperienceModel::tags, "tags") { JSONArray(it) }
        putProp(this@encodeJson, ClassicExperienceModel::name, "name") { it }
        if (this@encodeJson.sourceUrl != null) {
            putProp(this@encodeJson, ClassicExperienceModel::sourceUrl, "sourceUrl") { it }
        }
    }
}

fun Color.Companion.decodeJson(json: JSONObject): Color {
    return Color(
        json.getInt("red"),
        json.getInt("green"),
        json.getInt("blue"),
        json.getDouble("alpha")
    )
}

fun Color.encodeJson(): JSONObject {
    return JSONObject().apply {
        listOf(
            Color::red,
            Color::green,
            Color::blue,
            Color::alpha
        ).forEach { putProp(this@encodeJson, it) }
    }
}

fun BackgroundContentMode.Companion.decodeJSON(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundContentMode type '$value'")

fun BarcodeFormat.Companion.decodeJson(value: String): BarcodeFormat =
    BarcodeFormat.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BarcodeFormat value '$value'")

fun Image.Companion.optDecodeJSON(json: JSONObject?): Image? = when (json) {
    null -> null
    else -> Image(
        width = json.getInt("width"),
        height = json.getInt("height"),
        name = json.safeGetString("name"),
        size = json.getInt("size"),
        url = json.safeGetUri("url"),
        accessibilityLabel = json.safeOptString("accessibilityLabel"),
        isDecorative = json.optBoolean("isDecorative")
    )
}

fun Image.Companion.decodeJson(json: JSONObject): Image {
    return Image(
        width = json.getInt("width"),
        height = json.getInt("height"),
        name = json.safeGetString("name"),
        size = json.getInt("size"),
        url = json.safeGetUri("url"),
        accessibilityLabel = json.safeOptString("accessibilityLabel"),
        isDecorative = json.optBoolean("isDecorative")
    )
}

fun Image?.optEncodeJson(): JSONObject? {
    return this?.let {
        JSONObject().apply {
            listOf(
                Image::height,
                Image::name,
                Image::size,
                Image::width,
                Image::accessibilityLabel,
                Image::isDecorative
            ).forEach { putProp(this@optEncodeJson, it) }

            putProp(this@optEncodeJson, Image::url) { it.toString() }
        }
    }
}

fun UnitOfMeasure.Companion.decodeJson(value: String): UnitOfMeasure =
    UnitOfMeasure.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown Unit type '$value'")

fun Insets.Companion.decodeJson(json: JSONObject): Insets {
    return Insets(
        json.getInt("bottom"),
        json.getInt("left"),
        json.getInt("right"),
        json.getInt("top")
    )
}

fun Insets.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Insets::bottom)
        putProp(this@encodeJson, Insets::left)
        putProp(this@encodeJson, Insets::right)
        putProp(this@encodeJson, Insets::top)
    }
}

fun Font.Companion.decodeJson(json: JSONObject): Font {
    return Font(
        size = json.getInt("size"),
        weight = FontWeight.decodeJson(json.safeGetString("weight"))
    )
}

fun Font.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Font::size)
        putProp(this@encodeJson, Font::weight) { it.wireFormat }
    }
}

fun Position.Companion.decodeJson(json: JSONObject): Position {
    return Position(
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getJSONObject("horizontalAlignment")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getJSONObject("verticalAlignment"))
    )
}

fun Position.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Position::horizontalAlignment, "horizontalAlignment") { it.encodeJson() }
        putProp(this@encodeJson, Position::verticalAlignment, "verticalAlignment") { it.encodeJson() }
    }
}

fun HorizontalAlignment.Companion.decodeJson(json: JSONObject): HorizontalAlignment {
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        "HorizontalAlignmentCenter" -> HorizontalAlignment.Center(
            offset = json.getDouble("offset"),
            width = json.getDouble("width")
        )
        "HorizontalAlignmentLeft" -> HorizontalAlignment.Left(
            offset = json.getDouble("offset"),
            width = json.getDouble("width")
        )
        "HorizontalAlignmentRight" -> HorizontalAlignment.Right(
            offset = json.getDouble("offset"),
            width = json.getDouble("width")
        )
        "HorizontalAlignmentFill" -> HorizontalAlignment.Fill(
            leftOffset = json.getDouble("leftOffset"),
            rightOffset = json.getDouble("rightOffset")
        )
        else -> throw JSONException("Unknown HorizontalAlignment type '$typeName'.")
    }
}

fun HorizontalAlignment.encodeJson(): JSONObject {
    return JSONObject().apply {
        put(
            "__typename",
            when (this@encodeJson) {
                is HorizontalAlignment.Center -> {
                    putProp(this@encodeJson, HorizontalAlignment.Center::offset, "offset")
                    putProp(this@encodeJson, HorizontalAlignment.Center::width, "width")
                    "HorizontalAlignmentCenter"
                }
                is HorizontalAlignment.Left -> {
                    putProp(this@encodeJson, HorizontalAlignment.Left::width, "width")
                    putProp(this@encodeJson, HorizontalAlignment.Left::offset, "offset")
                    "HorizontalAlignmentLeft"
                }
                is HorizontalAlignment.Right -> {
                    putProp(this@encodeJson, HorizontalAlignment.Right::width, "width")
                    putProp(this@encodeJson, HorizontalAlignment.Right::offset, "offset")
                    "HorizontalAlignmentRight"
                }
                is HorizontalAlignment.Fill -> {
                    putProp(this@encodeJson, HorizontalAlignment.Fill::leftOffset, "leftOffset")
                    putProp(this@encodeJson, HorizontalAlignment.Fill::rightOffset, "rightOffset")
                    "HorizontalAlignmentFill"
                }
            }
        )
    }
}

fun VerticalAlignment.encodeJson(): JSONObject {
    return JSONObject().apply {
        put(
            "__typename",
            when (this@encodeJson) {
                is VerticalAlignment.Bottom -> {
                    putProp(this@encodeJson, VerticalAlignment.Bottom::height, "height") { it.encodeJson() }
                    putProp(this@encodeJson, VerticalAlignment.Bottom::offset, "offset")
                    "VerticalAlignmentBottom"
                }
                is VerticalAlignment.Middle -> {
                    putProp(this@encodeJson, VerticalAlignment.Middle::height, "height") { it.encodeJson() }
                    putProp(this@encodeJson, VerticalAlignment.Middle::offset, "offset")
                    "VerticalAlignmentMiddle"
                }
                is VerticalAlignment.Fill -> {
                    putProp(this@encodeJson, VerticalAlignment.Fill::bottomOffset, "bottomOffset")
                    putProp(this@encodeJson, VerticalAlignment.Fill::topOffset, "topOffset")
                    "VerticalAlignmentFill"
                }
                is VerticalAlignment.Stacked -> {
                    putProp(this@encodeJson, VerticalAlignment.Stacked::bottomOffset, "bottomOffset")
                    putProp(this@encodeJson, VerticalAlignment.Stacked::height, "height") { it.encodeJson() }
                    putProp(this@encodeJson, VerticalAlignment.Stacked::topOffset, "topOffset")
                    "VerticalAlignmentStacked"
                }
                is VerticalAlignment.Top -> {
                    putProp(this@encodeJson, VerticalAlignment.Top::height, "height") { it.encodeJson() }
                    putProp(this@encodeJson, VerticalAlignment.Top::offset, "offset")
                    "VerticalAlignmentTop"
                }
            }
        )
    }
}

fun Height.Companion.decodeJson(json: JSONObject): Height {
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        "HeightIntrinsic" -> Height.Intrinsic()
        "HeightStatic" -> Height.Static(
            value = json.getDouble("value")
        )
        else -> throw JSONException("Unknown Height type '$typeName'.")
    }
}

fun Height.encodeJson(): JSONObject {
    return JSONObject().apply {
        put(
            "__typename",
            when (this@encodeJson) {
                is Height.Intrinsic -> {
                    "HeightIntrinsic"
                }
                is Height.Static -> {
                    putProp(this@encodeJson, Height.Static::value, "value")
                    "HeightStatic"
                }
            }
        )
    }
}

fun VerticalAlignment.Companion.decodeJson(json: JSONObject): VerticalAlignment {
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        "VerticalAlignmentBottom" -> VerticalAlignment.Bottom(
            offset = json.getDouble("offset"),
            height = Height.decodeJson(json.getJSONObject("height"))
        )
        "VerticalAlignmentMiddle" -> VerticalAlignment.Middle(
            offset = json.getDouble("offset"),
            height = Height.decodeJson(json.getJSONObject("height"))
        )
        "VerticalAlignmentFill" -> VerticalAlignment.Fill(
            topOffset = json.getDouble("topOffset"),
            bottomOffset = json.getDouble("bottomOffset")
        )
        "VerticalAlignmentStacked" -> VerticalAlignment.Stacked(
            topOffset = json.getDouble("topOffset"),
            bottomOffset = json.getDouble("bottomOffset"),
            height = Height.decodeJson(json.getJSONObject("height"))
        )
        "VerticalAlignmentTop" -> VerticalAlignment.Top(
            offset = json.getDouble("offset"),
            height = Height.decodeJson(json.getJSONObject("height"))
        )
        else -> throw JSONException("Unknown VerticalAlignment type '$typeName'.")
    }
}

fun TextAlignment.Companion.decodeJson(value: String): TextAlignment =
    TextAlignment.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown TextAlignment type '$value'.")

fun FontWeight.Companion.decodeJson(value: String): FontWeight =
    FontWeight.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown FontWeight type '$value'.")

fun BackgroundContentMode.Companion.decodeJson(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundContentMode type '$value'.")

fun BackgroundScale.Companion.decodeJson(value: String): BackgroundScale =
    BackgroundScale.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundScale type '$value'.")

fun StatusBarStyle.Companion.decodeJson(value: String): StatusBarStyle =
    StatusBarStyle.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown StatusBarStyle type '$value'.")

fun TitleBarButtons.Companion.decodeJson(value: String): TitleBarButtons =
    TitleBarButtons.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown StatusBar TitleBarButtonsStyle type '$value'.")

fun Barcode.Companion.decodeJson(json: JSONObject): Barcode {
    return Barcode(
        format = BarcodeFormat.decodeJson(json.getString("format")),
        text = json.safeGetString("text")
    )
}

fun Barcode.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Barcode::format, "format") { it.wireFormat }
        putProp(this@encodeJson, Barcode::text, "text")
    }
}

fun StatusBar.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, StatusBar::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, StatusBar::style, "style") { it.wireFormat }
    }
}

fun TitleBar.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, TitleBar::backgroundColor, "backgroundColor") { it.encodeJson() }
        putProp(this@encodeJson, TitleBar::buttonColor, "buttonColor") { it.encodeJson() }
        putProp(this@encodeJson, TitleBar::buttons, "buttons") { it.wireFormat }
        putProp(this@encodeJson, TitleBar::text, "text")
        putProp(this@encodeJson, TitleBar::textColor, "textColor") { it.encodeJson() }
        putProp(this@encodeJson, TitleBar::useDefaultStyle, "useDefaultStyle")
    }
}

fun BarcodeBlock.Companion.decodeJson(json: JSONObject): BarcodeBlock {
    return BarcodeBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        barcode = Barcode.decodeJson(json.getJSONObject("barcode")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun Background.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Background::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Background::contentMode, "contentMode") { it.wireFormat }
        putProp(this@encodeJson, Background::image, "image") { it.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeJson, Background::scale, "scale") { it.wireFormat }
    }
}

fun Border.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Border::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Border::radius, "radius")
        putProp(this@encodeJson, Border::width, "width")
    }
}

fun Text.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Text::rawValue, "rawValue")
        putProp(this@encodeJson, Text::alignment, "alignment") { it.wireFormat }
        putProp(this@encodeJson, Text::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Text::font, "font") { it.encodeJson() }
    }
}

fun Image.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Image::height, "height")
        putProp(this@encodeJson, Image::name, "name")
        putProp(this@encodeJson, Image::size, "size")
        putProp(this@encodeJson, Image::url, "url") { it.toString() }
        putProp(this@encodeJson, Image::width, "width")
        putProp(this@encodeJson, Image::accessibilityLabel, "accessibilityLabel")
        putProp(this@encodeJson, Image::isDecorative, "isDecorative")
    }
}

fun DurationUnit.encodeJson(): String = when (this) {
    DurationUnit.DAYS -> "d"
    DurationUnit.HOURS -> "h"
    DurationUnit.MINUTES -> "m"
    DurationUnit.SECONDS -> "s"
}

fun Duration.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Duration::unit, "unit") { it.encodeJson() }
        putProp(this@encodeJson, Duration::value, "value")
    }
}

fun Conversion.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Conversion::tag, "tag")
        putProp(this@encodeJson, Conversion::expires, "expires") { it.encodeJson() }
    }
}

fun Conversion?.optEncodeJson(): JSONObject? {
    return this?.encodeJson()
}

fun Background.Companion.decodeJson(json: JSONObject): Background {
    return Background(
        color = Color.decodeJson(json.getJSONObject("color")),
        contentMode = BackgroundContentMode.decodeJSON(json.safeGetString("contentMode")),
        image = Image.optDecodeJSON(json.optJSONObject("image")),
        scale = BackgroundScale.decodeJson(json.safeGetString("scale"))
    )
}

fun Border.Companion.decodeJson(json: JSONObject): Border {
    return Border(
        color = Color.decodeJson(json.getJSONObject("color")),
        radius = json.getInt("radius"),
        width = json.getInt("width")
    )
}

fun Text.Companion.decodeJson(json: JSONObject): Text {
    return Text(
        rawValue = json.safeGetString("rawValue"),
        alignment = TextAlignment.decodeJson(json.safeGetString("alignment")),
        color = Color.decodeJson(json.getJSONObject("color")),
        font = Font.decodeJson(json.getJSONObject("font"))
    )
}

fun Block.encodeJson(): JSONObject {
    // dispatch to each
    return when (this) {
        is BarcodeBlock -> this.encodeJson()
        is ButtonBlock -> this.encodeJson()
        is ImageBlock -> this.encodeJson()
        is RectangleBlock -> this.encodeJson()
        is TextBlock -> this.encodeJson()
        is WebViewBlock -> this.encodeJson()
        is TextPollBlock -> this.encodeJson()
        is ImagePollBlock -> this.encodeJson()
        else -> throw RuntimeException("Unsupported Block type for serialization")
    }
}

fun Block.encodeSharedJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeSharedJson, Block::tapBehavior, "tapBehavior") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::id, "id") { it }
        putProp(this@encodeSharedJson, Block::insets, "insets") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::opacity, "opacity")
        putProp(this@encodeSharedJson, Block::position, "position") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::background, "background") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::border, "border") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::keys, "keys") { JSONObject(it) }
        putProp(this@encodeSharedJson, Block::name, "name") { it }
        putProp(this@encodeSharedJson, Block::conversion, "conversion") {
            it.optEncodeJson() ?: JSONObject.NULL
        }
        putProp(this@encodeSharedJson, Block::tags) { JSONArray(it) }
    }
}

fun ImagePollBlockOption.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, ImagePollBlockOption::id, "id")
        putProp(this@encodeJson, ImagePollBlockOption::text, "text") { it.encodeJson() }
        putProp(this@encodeJson, ImagePollBlockOption::image, "image") {
            it.optEncodeJson() ?: JSONObject.NULL
        }
        putProp(this@encodeJson, ImagePollBlockOption::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, ImagePollBlockOption::border, "border") { it.encodeJson() }
        putProp(this@encodeJson, ImagePollBlockOption::opacity, "opacity")
        putProp(this@encodeJson, ImagePollBlockOption::topMargin, "topMargin")
        putProp(this@encodeJson, ImagePollBlockOption::leftMargin, "leftMargin")
        putProp(
            this@encodeJson,
            ImagePollBlockOption::resultFillColor,
            "resultFillColor"
        ) { it.encodeJson() }
    }
}

fun TextPollOption.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, TextPollOption::id, "id")
        putProp(this@encodeJson, TextPollOption::text, "text") { it.encodeJson() }
        putProp(this@encodeJson, TextPollOption::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, TextPollOption::border, "border") { it.encodeJson() }
        putProp(this@encodeJson, TextPollOption::opacity, "opacity")
        putProp(this@encodeJson, TextPollOption::height, "height")
        putProp(this@encodeJson, TextPollOption::topMargin, "topMargin")
        putProp(
            this@encodeJson,
            TextPollOption::resultFillColor,
            "resultFillColor"
        ) { it.encodeJson() }
        putProp(this@encodeJson, TextPollOption::background, "background") { it.encodeJson() }
    }
}

fun ImagePollBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "ImagePollBlock")
        putProp(this@encodeJson, ImagePollBlock::imagePoll, "imagePoll") { it.encodeJson() }
    }
}

fun ImagePoll.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, ImagePoll::question, "question") { it.encodeJson() }
        putProp(
            this@encodeJson,
            ImagePoll::options,
            "options"
        ) { JSONArray(it.map { it.encodeJson() }) }
    }
}

fun TextPoll.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, TextPoll::question, "question") { it.encodeJson() }
        putProp(
            this@encodeJson,
            TextPoll::options,
            "options"
        ) { JSONArray(it.map { it.encodeJson() }) }
    }
}

fun TextPollBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "TextPollBlock")
        putProp(this@encodeJson, TextPollBlock::textPoll, "textPoll") { it.encodeJson() }
    }
}

fun BarcodeBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "BarcodeBlock")
        putProp(this@encodeJson, BarcodeBlock::barcode, "barcode") { it.encodeJson() }
    }
}

fun RectangleBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "RectangleBlock")
    }
}

fun WebView.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, WebView::isScrollingEnabled, "isScrollingEnabled")
        putProp(this@encodeJson, WebView::url, "url") { it.toString() }
    }
}

fun WebViewBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "WebViewBlock")
        putProp(this@encodeJson, WebViewBlock::webView, "webView") { it.encodeJson() }
    }
}

fun TextBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "TextBlock")
        putProp(this@encodeJson, TextBlock::text, "text") { it.encodeJson() }
    }
}

fun ImageBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "ImageBlock")
        putProp(this@encodeJson, ImageBlock::image, "image") { it?.encodeJson() ?: JSONObject.NULL }
    }
}

fun ButtonBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "ButtonBlock")
        putProp(this@encodeJson, ButtonBlock::text, "text") { it.encodeJson() }
    }
}

fun ButtonBlock.Companion.decodeJson(json: JSONObject): ButtonBlock {
    return ButtonBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.getJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        text = Text.decodeJson(json.getJSONObject("text")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun Duration.Companion.decodeJson(json: JSONObject): Duration {
    val unit = when (json.getString("unit")) {
        "s" -> DurationUnit.SECONDS
        "m" -> DurationUnit.MINUTES
        "h" -> DurationUnit.HOURS
        "d" -> DurationUnit.DAYS
        else -> DurationUnit.DAYS
    }
    return Duration(value = json.getInt("value"), unit = unit)
}

fun Conversion.Companion.optDecodeJson(json: JSONObject?): Conversion? = when (json) {
    null -> null
    else -> Conversion(
        json.getString("tag"),
        Duration.decodeJson(json.getJSONObject("expires"))
    )
}

fun RectangleBlock.Companion.decodeJson(json: JSONObject): RectangleBlock {
    return RectangleBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun WebViewBlock.Companion.decodeJson(json: JSONObject): WebViewBlock {
    return WebViewBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        webView = WebView.decodeJson(json.getJSONObject("webView")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun WebView.Companion.decodeJson(json: JSONObject): WebView {
    return WebView(
        isScrollingEnabled = json.getBoolean("isScrollingEnabled"),
        url = json.safeGetUri("url").toURL()
    )
}

fun TextBlock.Companion.decodeJson(json: JSONObject): TextBlock {
    return TextBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        text = Text.decodeJson(json.getJSONObject("text")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun ImageBlock.Companion.decodeJson(json: JSONObject): ImageBlock {
    return ImageBlock(
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        image = Image.decodeJson(json.optJSONObject("image")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun Block.TapBehavior.Companion.decodeJson(json: JSONObject): Block.TapBehavior {
    return when (val typeName = json.safeGetString("__typename")) {
        "NoneBlockTapBehavior" -> Block.TapBehavior.None
        "OpenURLBlockTapBehavior" -> Block.TapBehavior.OpenUri(
            uri = json.safeGetUri("url"),
            dismiss = json.optBoolean("dismiss")
        )
        "GoToScreenBlockTapBehavior" -> Block.TapBehavior.GoToScreen(
            screenId = json.getString("screenID")
        )
        "PresentWebsiteBlockTapBehavior" -> Block.TapBehavior.PresentWebsite(
            url = json.safeGetUri("url")
        )
        "CustomBlockTapBehavior" -> Block.TapBehavior.Custom
        else -> throw JSONException("Unsupported Block TapBehavior type `$typeName`.")
    }
}

fun ImagePollBlockOption.Companion.decodeJson(json: JSONObject): ImagePollBlockOption {
    return ImagePollBlockOption(
        id = json.safeGetString("id"),
        text = Text.decodeJson(json.getJSONObject("text")),
        image = Image.optDecodeJSON(json.optJSONObject("image")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        opacity = json.getDouble("opacity"),
        topMargin = json.getInt("topMargin"),
        leftMargin = json.getInt("leftMargin"),
        resultFillColor = Color.decodeJson(json.getJSONObject("resultFillColor"))
    )
}

fun ImagePollBlock.Companion.decodeJson(json: JSONObject): ImagePollBlock {
    return ImagePollBlock(
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        background = Background.decodeJson(json.getJSONObject("background")),
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        border = Border.decodeJson(json.getJSONObject("border")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList(),
        imagePoll = ImagePoll.decodeJson(json.getJSONObject("imagePoll"))
    )
}

fun ImagePoll.Companion.decodeJson(json: JSONObject): ImagePoll {
    return ImagePoll(
        question = Text.decodeJson(json.getJSONObject("question")),
        options = json.getJSONArray("options").getObjectIterable()
            .map { ImagePollBlockOption.decodeJson(it) }
    )
}

fun TextPollOption.Companion.decodeJson(json: JSONObject): TextPollOption {
    return TextPollOption(
        id = json.safeGetString("id"),
        text = Text.decodeJson(json.getJSONObject("text")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        opacity = json.getDouble("opacity"),
        height = json.getInt("height"),
        resultFillColor = Color.decodeJson(json.getJSONObject("resultFillColor")),
        topMargin = json.getInt("topMargin")
    )
}

fun TextPoll.Companion.decodeJson(json: JSONObject): TextPoll {
    return TextPoll(
        question = Text.decodeJson(json.getJSONObject("question")),
        options = json.getJSONArray("options").getObjectIterable()
            .map { TextPollOption.decodeJson(it) }
    )
}

fun TextPollBlock.Companion.decodeJson(json: JSONObject): TextPollBlock {
    return TextPollBlock(
        id = json.safeGetString("id"),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toStringHash(),
        background = Background.decodeJson(json.getJSONObject("background")),
        tapBehavior = Block.TapBehavior.decodeJson(json.optJSONObject("tapBehavior")),
        border = Border.decodeJson(json.getJSONObject("border")),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList(),
        textPoll = TextPoll.decodeJson(json.getJSONObject("textPoll"))
    )
}

fun Block.TapBehavior.encodeJson(): JSONObject {
    return JSONObject().apply {
        put(
            "__typename",
            when (this@encodeJson) {
                is Block.TapBehavior.GoToScreen -> {
                    putProp(
                        this@encodeJson,
                        Block.TapBehavior.GoToScreen::screenId,
                        "screenID"
                    ) { it }
                    "GoToScreenBlockTapBehavior"
                }
                is Block.TapBehavior.OpenUri -> {
                    putProp(
                        this@encodeJson,
                        Block.TapBehavior.OpenUri::uri,
                        "url"
                    ) { it.toString() }
                    "OpenURLBlockTapBehavior"
                }
                is Block.TapBehavior.PresentWebsite -> {
                    putProp(
                        this@encodeJson,
                        Block.TapBehavior.PresentWebsite::url
                    ) { it.toString() }
                    "PresentWebsiteBlockTapBehavior"
                }
                is Block.TapBehavior.None -> {
                    "NoneBlockTapBehavior"
                }
                is Block.TapBehavior.Custom -> {
                    "CustomBlockTapBehavior"
                }
            }
        )
    }
}

val BarcodeBlock.Companion.resourceName get() = "BarcodeBlock"
val ButtonBlock.Companion.resourceName get() = "ButtonBlock"
val RectangleBlock.Companion.resourceName get() = "RectangleBlock"
val WebViewBlock.Companion.resourceName get() = "WebViewBlock"
val TextBlock.Companion.resourceName get() = "TextBlock"
val ImageBlock.Companion.resourceName get() = "ImageBlock"
val TextPollBlock.Companion.resourceName get() = "TextPollBlock"
val ImagePollBlock.Companion.resourceName get() = "ImagePollBlock"

fun Block.Companion.decodeJson(json: JSONObject): Block {
    // Block has subclasses, so we need to delegate to the appropriate deserializer for each
    // block type.

    return when (val typeName = json.safeGetString("__typename")) {
        BarcodeBlock.resourceName -> BarcodeBlock.decodeJson(json)
        ButtonBlock.resourceName -> ButtonBlock.decodeJson(json)
        RectangleBlock.resourceName -> RectangleBlock.decodeJson(json)
        WebViewBlock.resourceName -> WebViewBlock.decodeJson(json)
        TextBlock.resourceName -> TextBlock.decodeJson(json)
        ImageBlock.resourceName -> ImageBlock.decodeJson(json)
        TextPollBlock.resourceName -> TextPollBlock.decodeJson(json)
        ImagePollBlock.resourceName -> ImagePollBlock.decodeJson(json)
        else -> throw RuntimeException("Unsupported Block type '$typeName'.")
    }
}

fun Row.Companion.decodeJSON(json: JSONObject): Row {
    return Row(
        background = Background.decodeJson(json.getJSONObject("background")),
        blocks = json.getJSONArray("blocks").getObjectIterable().map { Block.decodeJson(it) },
        height = Height.decodeJson(json.getJSONObject("height")),
        keys = json.getJSONObject("keys").toStringHash(),
        id = json.safeGetString("id"),
        name = json.safeGetString("name"),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun Row.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Row::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, Row::blocks, "blocks") { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, Row::height, "height") { it.encodeJson() }
        putProp(this@encodeJson, Row::id, "id") { it }
        putProp(this@encodeJson, Row::keys) { JSONObject(it) }
        putProp(this@encodeJson, Row::name, "name") { it }
        putProp(this@encodeJson, Row::tags) { JSONArray(it) }
    }
}

fun StatusBar.Companion.decodeJson(json: JSONObject): StatusBar {
    return StatusBar(
        style = StatusBarStyle.decodeJson(json.safeGetString("style")),
        color = Color.decodeJson(json.getJSONObject("color"))
    )
}

fun TitleBar.Companion.decodeJson(json: JSONObject): TitleBar {
    return TitleBar(
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        buttons = TitleBarButtons.decodeJson(json.safeGetString("buttons")),
        buttonColor = Color.decodeJson(json.getJSONObject("buttonColor")),
        text = json.safeGetString("text"),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        useDefaultStyle = json.getBoolean("useDefaultStyle")
    )
}

fun Screen.Companion.decodeJson(json: JSONObject): Screen {
    return Screen(
        background = Background.decodeJson(json.getJSONObject("background")),
        id = json.safeGetString("id"),
        isStretchyHeaderEnabled = json.getBoolean("isStretchyHeaderEnabled"),
        rows = json.getJSONArray("rows").getObjectIterable().map {
            Row.decodeJSON(it)
        },
        statusBar = StatusBar.decodeJson(json.getJSONObject("statusBar")),
        titleBar = TitleBar.decodeJson(json.getJSONObject("titleBar")),
        keys = json.getJSONObject("keys").toStringHash(),
        name = json.safeGetString("name"),
        conversion = Conversion.optDecodeJson(json.optJSONObject("conversion")),
        tags = json.getJSONArray("tags").getStringIterable().toList()
    )
}

fun Screen.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Screen::isStretchyHeaderEnabled, "isStretchyHeaderEnabled")
        putProp(this@encodeJson, Screen::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, Screen::id) { it }
        putProp(this@encodeJson, Screen::rows) { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, Screen::statusBar, "statusBar") { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBar, "titleBar") { it.encodeJson() }
        putProp(this@encodeJson, Screen::keys) { JSONObject(it) }
        putProp(this@encodeJson, Screen::tags) { JSONArray(it) }
        putProp(this@encodeJson, Screen::conversion, "conversion") {
            it.optEncodeJson() ?: JSONObject.NULL
        }
        putProp(this@encodeJson, Screen::name) { it }
    }
}
