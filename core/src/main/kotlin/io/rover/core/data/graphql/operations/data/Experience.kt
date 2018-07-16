@file:JvmName("Experience")

package io.rover.core.data.graphql.operations.data

import io.rover.core.data.domain.Background
import io.rover.core.data.domain.BackgroundContentMode
import io.rover.core.data.domain.BackgroundScale
import io.rover.core.data.domain.Barcode
import io.rover.core.data.domain.BarcodeBlock
import io.rover.core.data.domain.BarcodeFormat
import io.rover.core.data.domain.Block
import io.rover.core.data.domain.Border
import io.rover.core.data.domain.ButtonBlock
import io.rover.core.data.domain.Color
import io.rover.core.data.domain.Experience
import io.rover.core.data.domain.Font
import io.rover.core.data.domain.FontWeight
import io.rover.core.data.domain.Height
import io.rover.core.data.domain.HorizontalAlignment
import io.rover.core.data.domain.ID
import io.rover.core.data.domain.Image
import io.rover.core.data.domain.ImageBlock
import io.rover.core.data.domain.Insets
import io.rover.core.data.domain.Position
import io.rover.core.data.domain.RectangleBlock
import io.rover.core.data.domain.Row
import io.rover.core.data.domain.Screen
import io.rover.core.data.domain.StatusBar
import io.rover.core.data.domain.StatusBarStyle
import io.rover.core.data.domain.Text
import io.rover.core.data.domain.TextAlignment
import io.rover.core.data.domain.TextBlock
import io.rover.core.data.domain.TitleBar
import io.rover.core.data.domain.TitleBarButtons
import io.rover.core.data.domain.UnitOfMeasure
import io.rover.core.data.domain.VerticalAlignment
import io.rover.core.data.domain.WebView
import io.rover.core.data.domain.WebViewBlock
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeGetUri
import io.rover.core.data.graphql.safeOptString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun Experience.Companion.decodeJson(json: JSONObject): Experience {
    return Experience(
        id = ID(json.safeGetString("id")),
        homeScreenId = ID(json.safeGetString("homeScreenID")),
        screens = json.getJSONArray("screens").getObjectIterable().map {
            Screen.decodeJson(it)
        },
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        campaignId = json.safeOptString("campaignID")
    )
}

internal fun Experience.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Experience::id) { it.rawValue }
        putProp(this@encodeJson, Experience::homeScreenId, "homeScreenID") { it.rawValue }
        putProp(this@encodeJson, Experience::screens) { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, Experience::keys) { it.encodeJson() }
        putProp(this@encodeJson, Experience::campaignId, "campaignID")
    }
}

internal fun Color.Companion.decodeJson(json: JSONObject): Color {
    return Color(
        json.getInt("red"),
        json.getInt("green"),
        json.getInt("blue"),
        json.getDouble("alpha")
    )
}

internal fun Color.encodeJson(): JSONObject {
    return JSONObject().apply {
        listOf(
            Color::red, Color::green, Color::blue, Color::alpha
        ).forEach { putProp(this@encodeJson, it) }
    }
}

internal fun BackgroundContentMode.Companion.decodeJSON(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundContentMode type '$value'")

internal fun BarcodeFormat.Companion.decodeJson(value: String): BarcodeFormat =
    BarcodeFormat.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BarcodeFormat value '$value'")

internal fun Image.Companion.optDecodeJSON(json: JSONObject?): Image? = when (json) {
    null -> null
    else -> Image(
        json.getInt("width"),
        json.getInt("height"),
        json.safeGetString("name"),
        json.getInt("size"),
        json.safeGetUri("url")
    )
}

internal fun Image?.optEncodeJson(): JSONObject? {
    return this?.let {
        JSONObject().apply {
            listOf(
                Image::height,
                Image::name,
                Image::size,
                Image::width
            ).forEach { putProp(this@optEncodeJson, it) }

            putProp(this@optEncodeJson, Image::url) { it.toString() }
        }
    }
}

internal fun UnitOfMeasure.Companion.decodeJson(value: String): UnitOfMeasure =
    UnitOfMeasure.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown Unit type '$value'")

internal fun Insets.Companion.decodeJson(json: JSONObject): Insets {
    return Insets(
        json.getInt("bottom"),
        json.getInt("left"),
        json.getInt("right"),
        json.getInt("top")
    )
}

internal fun Insets.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Insets::bottom)
        putProp(this@encodeJson, Insets::left)
        putProp(this@encodeJson, Insets::right)
        putProp(this@encodeJson, Insets::top)
    }
}

internal fun Font.Companion.decodeJson(json: JSONObject): Font {
    return Font(
        size = json.getInt("size"),
        weight = FontWeight.decodeJson(json.safeGetString("weight"))
    )
}

internal fun Font.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Font::size)
        putProp(this@encodeJson, Font::weight) { it.wireFormat }
    }
}



internal fun Position.Companion.decodeJson(json: JSONObject): Position {
    return Position(
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getJSONObject("horizontalAlignment")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getJSONObject("verticalAlignment"))
    )
}

internal fun Position.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson,  Position::horizontalAlignment, "horizontalAlignment") { it.encodeJson() }
        putProp(this@encodeJson,  Position::verticalAlignment, "horizontalAlignment") { it.encodeJson() }
    }
}

internal fun HorizontalAlignment.Companion.decodeJson(json: JSONObject): HorizontalAlignment {
    val typeName = json.safeGetString("__typename")

    return when(typeName) {
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
        else ->  throw JSONException("Unknown HorizontalAlignment type '$typeName'.")
    }
}

internal fun HorizontalAlignment.encodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when(this@encodeJson) {
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
        })
    }
}

internal fun VerticalAlignment.encodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when(this@encodeJson) {
            is VerticalAlignment.Bottom -> {
                putProp(this@encodeJson, VerticalAlignment.Bottom::height, "height")
                putProp(this@encodeJson, VerticalAlignment.Bottom::offset, "offset")
                "VerticalAlignmentBottom"
            }
            is VerticalAlignment.Middle -> {
                putProp(this@encodeJson, VerticalAlignment.Middle::height, "height")
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
                putProp(this@encodeJson, VerticalAlignment.Stacked::height, "height")
                putProp(this@encodeJson, VerticalAlignment.Stacked::topOffset, "topOffset")
                "VerticalAlignmentStacked"
            }
            is VerticalAlignment.Top -> {
                putProp(this@encodeJson, VerticalAlignment.Top::height, "height")
                putProp(this@encodeJson, VerticalAlignment.Top::offset, "bottomOffset")
                "VerticalAlignmentTop"
            }
        })
    }
}

internal fun Height.Companion.decodeJson(json: JSONObject): Height {
    val typeName = json.safeGetString("__typename")

    return when(typeName) {
        "HeightIntrinsic" -> Height.Intrinsic()
        "HeightStatic" -> Height.Static(
            value = json.getDouble("value")
        )
        else -> throw JSONException("Unknown Height type '$typeName'.")
    }
}

internal fun Height.encodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when(this@encodeJson) {
            is Height.Intrinsic -> {
                "HeightIntrinsic"
            }
            is Height.Static -> {
                putProp(this@encodeJson, Height.Static::value, "value")
                "HeightStatic"
            }
        })
    }
}

internal fun VerticalAlignment.Companion.decodeJson(json: JSONObject): VerticalAlignment {
    val typeName = json.safeGetString("__typename")

    return when(typeName) {
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

internal fun TextAlignment.Companion.decodeJson(value: String): TextAlignment =
    TextAlignment.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown TextAlignment type '$value'.")

internal fun FontWeight.Companion.decodeJson(value: String): FontWeight =
    FontWeight.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown FontWeight type '$value'.")

internal fun BackgroundContentMode.Companion.decodeJson(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundContentMode type '$value'.")

internal fun BackgroundScale.Companion.decodeJson(value: String): BackgroundScale =
    BackgroundScale.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown BackgroundScale type '$value'.")

internal fun StatusBarStyle.Companion.decodeJson(value: String): StatusBarStyle =
    StatusBarStyle.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown StatusBarStyle type '$value'.")

internal fun TitleBarButtons.Companion.decodeJson(value: String): TitleBarButtons =
    TitleBarButtons.values().firstOrNull { it.wireFormat == value } ?: throw JSONException("Unknown StatusBar TitleBarButtonsStyle type '$value'.")

internal fun Barcode.Companion.decodeJson(json: JSONObject): Barcode {
    return Barcode(
        scale = json.getInt("scale"),
        format = BarcodeFormat.decodeJson(json.getString("format")),
        text = json.safeGetString("text")
    )
}

internal fun Barcode.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Barcode::format, "format") { it.wireFormat }
        putProp(this@encodeJson, Barcode::scale, "scale")
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

internal fun BarcodeBlock.Companion.decodeJson(json: JSONObject): BarcodeBlock {
    return BarcodeBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        barcode = Barcode.decodeJson(json.getJSONObject("barcode"))
    )
}

internal fun Background.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Background::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Background::contentMode, "contentMode") { it.wireFormat }
        putProp(this@encodeJson, Background::image, "image") { it.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeJson, Background::scale, "scale") { it.wireFormat }
    }
}

internal fun Border.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Border::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Border::radius, "radius")
        putProp(this@encodeJson, Border::width, "width")
    }
}

internal fun Text.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Text::rawValue, "rawValue")
        putProp(this@encodeJson, Text::alignment, "alignment") { it.wireFormat }
        putProp(this@encodeJson, Text::color, "color") { it.encodeJson() }
        putProp(this@encodeJson, Text::font, "font") { it.encodeJson() }
    }
}

internal fun Image.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Image::height, "height")
        putProp(this@encodeJson, Image::name, "name")
        putProp(this@encodeJson, Image::size, "size")
        putProp(this@encodeJson, Image::url, "url") { it.toString() }
        putProp(this@encodeJson, Image::width, "width")
    }
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

internal fun Block.encodeJson(): JSONObject {
    // dispatch to each
    return when(this) {
        is BarcodeBlock -> this.encodeJson()
        is ButtonBlock -> this.encodeJson()
        is ImageBlock -> this.encodeJson()
        is RectangleBlock -> this.encodeJson()
        is TextBlock -> this.encodeJson()
        is WebViewBlock -> this.encodeJson()
        else -> throw RuntimeException("Unsupported Block type for serialization")
    }
}

internal fun Block.encodeSharedJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeSharedJson, Block::tapBehavior, "tapBehavior") { it?.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeSharedJson, Block::id, "id") { it.rawValue }
        putProp(this@encodeSharedJson, Block::insets, "insets") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::opacity, "opacity")
        putProp(this@encodeSharedJson, Block::position, "position") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::background, "background") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::border, "border") { it.encodeJson() }
        putProp(this@encodeSharedJson, Block::keys, "keys") { it.encodeJson() }
    }
}

internal fun BarcodeBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "BarcodeBlock")
        putProp(this@encodeJson, BarcodeBlock::barcode, "barcode") { it.encodeJson() }
    }
}

internal fun RectangleBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "RectangleBlock")
    }
}

internal fun WebView.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, WebView::isScrollingEnabled, "isScrollingEnabled")
        putProp(this@encodeJson, WebView::url, "url") { it.toString() }
    }
}

internal fun WebViewBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "WebViewBlock")
        putProp(this@encodeJson, WebViewBlock::webView, "webView") { it.encodeJson() }
    }
}

internal fun TextBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "TextBlock")
        putProp(this@encodeJson, TextBlock::text, "text") { it.encodeJson() }
    }
}

internal fun ImageBlock.encodeJson(): JSONObject {
    return encodeSharedJson().apply {
        put("__typename", "ImageBlock")
        putProp(this@encodeJson, ImageBlock::image, "image") { it?.encodeJson() ?: JSONObject.NULL }
    }
}

internal fun ButtonBlock.Companion.decodeJson(json: JSONObject): ButtonBlock {
    return ButtonBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        text = Text.decodeJson(json.getJSONObject("text"))
    )
}

internal fun RectangleBlock.Companion.decodeJson(json: JSONObject): RectangleBlock {
    return RectangleBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash()
    )
}

internal fun WebViewBlock.Companion.decodeJson(json: JSONObject): WebViewBlock {
    return WebViewBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        webView = WebView.decodeJson(json.getJSONObject("webView"))
    )
}

internal fun WebView.Companion.decodeJson(json: JSONObject): WebView {
    return WebView(
        isScrollingEnabled = json.getBoolean("isScrollingEnabled"),
        url = json.safeGetUri("url").toURL()
    )
}

internal fun TextBlock.Companion.decodeJson(json: JSONObject): TextBlock {
    return TextBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        text = Text.decodeJson(json.getJSONObject("text"))
    )
}

internal fun ImageBlock.Companion.decodeJson(json: JSONObject): ImageBlock {
    return ImageBlock(
        tapBehavior = Block.TapBehavior.optDecodeJson(json.optJSONObject("tapBehavior")),
        background = Background.decodeJson(json.getJSONObject("background")),
        border = Border.decodeJson(json.getJSONObject("border")),
        id = ID(json.safeGetString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getJSONObject("position")),
        keys = json.getJSONObject("keys").toFlatAttributesHash(),
        image = Image.optDecodeJSON(json.optJSONObject("image"))
    )
}

internal fun Block.TapBehavior.Companion.optDecodeJson(json: JSONObject?): Block.TapBehavior? {
    if (json == null) return null
    val typeName = json.safeGetString("__typename")

    return when(typeName) {
        "NoneBlockTapBehavior" -> Block.TapBehavior.None()
        "OpenURLBlockTapBehavior" -> Block.TapBehavior.OpenUri(
            uri = json.safeGetUri("url")
        )
        "GoToScreenBlockTapBehavior" -> Block.TapBehavior.GoToScreen(
            screenId = ID(json.getString("screenID"))
        )
        "PresentWebsiteBlockTapBehavior" -> Block.TapBehavior.PresentWebsite(
            url = json.safeGetUri("url")
        )
        else -> throw JSONException("Unsupported Block TapBehavior type `$typeName`.")
    }
}

internal fun Block.TapBehavior.optEncodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when(this@optEncodeJson) {
            is Block.TapBehavior.GoToScreen -> {
                putProp(this@optEncodeJson, Block.TapBehavior.GoToScreen::screenId, "screenID") { it.toString() }
                "GoToScreenBlockTapBehavior"
            }
            is Block.TapBehavior.OpenUri -> {
                putProp(this@optEncodeJson, Block.TapBehavior.OpenUri::uri, "url") { it.toString() }
                "OpenURLBlockTapBehavior"
            }
            is Block.TapBehavior.PresentWebsite -> {
                putProp(this@optEncodeJson, Block.TapBehavior.PresentWebsite::url) { it.toString() }
                "PresentWebsiteBlockTapBehavior"
            }
            is Block.TapBehavior.None -> {
                "NoneBlockTapBehavior"
            }
        })
    }
}

internal val BarcodeBlock.Companion.resourceName get() = "BarcodeBlock"
internal val ButtonBlock.Companion.resourceName get() = "ButtonBlock"
internal val RectangleBlock.Companion.resourceName get() = "RectangleBlock"
internal val WebViewBlock.Companion.resourceName get() = "WebViewBlock"
internal val TextBlock.Companion.resourceName get() = "TextBlock"
internal val ImageBlock.Companion.resourceName get() = "ImageBlock"

internal fun Block.Companion.decodeJson(json: JSONObject): Block {
    // Block has subclasses, so we need to delegate to the appropriate deserializer for each
    // block type.
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        BarcodeBlock.resourceName -> BarcodeBlock.decodeJson(json)
        ButtonBlock.resourceName -> ButtonBlock.decodeJson(json)
        RectangleBlock.resourceName -> RectangleBlock.decodeJson(json)
        WebViewBlock.resourceName -> WebViewBlock.decodeJson(json)
        TextBlock.resourceName -> TextBlock.decodeJson(json)
        ImageBlock.resourceName -> ImageBlock.decodeJson(json)
        else -> throw RuntimeException("Unsupported Block type '$typeName'.")
    }
}

internal fun Row.Companion.decodeJSON(json: JSONObject): Row {
    return Row(
        background = Background.decodeJson(json.getJSONObject("background")),
        blocks = json.getJSONArray("blocks").getObjectIterable().map { Block.decodeJson(it) },
        height = Height.decodeJson(json.getJSONObject("height")),
        id = ID(json.safeGetString("id"))
    )
}

internal fun Row.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Row::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, Row::blocks, "blocks") { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, Row::height, "height") { it.encodeJson() }
        putProp(this@encodeJson, Row::id, "id") { it.rawValue }
    }
}

internal fun StatusBar.Companion.decodeJson(json: JSONObject): StatusBar {
    return StatusBar(
        style = StatusBarStyle.decodeJson(json.safeGetString("style")),
        color = Color.decodeJson(json.getJSONObject("color"))
    )
}

internal fun TitleBar.Companion.decodeJson(json: JSONObject): TitleBar {
    return TitleBar(
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        buttons = TitleBarButtons.decodeJson(json.safeGetString("buttons")),
        buttonColor = Color.decodeJson(json.getJSONObject("buttonColor")),
        text = json.safeGetString("text"),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        useDefaultStyle = json.getBoolean("useDefaultStyle")
    )
}

internal fun Screen.Companion.decodeJson(json: JSONObject): Screen {
    return Screen(
        background = Background.decodeJson(json.getJSONObject("background")),
        id = ID(json.safeGetString("id")),
        isStretchyHeaderEnabled = json.getBoolean("isStretchyHeaderEnabled"),
        rows = json.getJSONArray("rows").getObjectIterable().map {
            Row.decodeJSON(it)
        },
        statusBar = StatusBar.decodeJson(json.getJSONObject("statusBar")),
        titleBar = TitleBar.decodeJson(json.getJSONObject("titleBar")),
        keys = json.getJSONObject("keys").toFlatAttributesHash()
    )
}

internal fun Screen.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Screen::isStretchyHeaderEnabled, "isStretchyHeaderEnabled")
        putProp(this@encodeJson, Screen::background, "background") { it.encodeJson() }
        putProp(this@encodeJson, Screen::id) { it.rawValue }
        putProp(this@encodeJson, Screen::rows) { JSONArray(it.map { it.encodeJson() }) }
        putProp(this@encodeJson, Screen::statusBar, "statusBar") { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBar, "titleBar") { it.encodeJson() }
        putProp(this@encodeJson, Screen::keys) { it.encodeJson() }
    }
}

