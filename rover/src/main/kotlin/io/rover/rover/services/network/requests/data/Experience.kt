package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.BarcodeBlock
import io.rover.rover.core.domain.BarcodeFormat
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.BlockAction
import io.rover.rover.core.domain.Border
import io.rover.rover.core.domain.ButtonBlock
import io.rover.rover.core.domain.ButtonState
import io.rover.rover.core.domain.Color
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.Font
import io.rover.rover.core.domain.FontWeight
import io.rover.rover.core.domain.HorizontalAlignment
import io.rover.rover.core.domain.ID
import io.rover.rover.core.domain.Image
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.domain.Insets
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Offsets
import io.rover.rover.core.domain.Position
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.domain.StatusBarStyle
import io.rover.rover.core.domain.Text
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.core.domain.TitleBarButtons
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment
import io.rover.rover.core.domain.WebViewBlock
import io.rover.rover.services.network.putProp
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

fun Experience.Companion.decodeJson(json: JSONObject): Experience {
    return Experience(
        id = ID(json.getString("id")),
        homeScreenId = ID(json.getString("homeScreenId")),
        screens = json.getJSONArray("screens").getObjectIterable().map {
            Screen.decodeJson(it)
        }
    )
}

fun Experience.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Experience::id) { it.rawValue }
        putProp(this@encodeJson, Experience::homeScreenId) { it.rawValue }
        putProp(this@encodeJson, Experience::screens) { JSONArray(it.map { it.encodeJson(this@encodeJson.id.rawValue) }) }
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
            Color::red, Color::green, Color::blue, Color::alpha
        ).forEach { putProp(this@encodeJson, it) }
    }
}

fun BackgroundContentMode.Companion.decodeJSON(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundContentMode type '$value'")

fun BarcodeFormat.Companion.decodeJson(value: String): BarcodeFormat =
    BarcodeFormat.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BarcodeFormat value '$value'")

fun Image.Companion.optDecodeJSON(json: JSONObject?): Image? = when (json) {
    null -> null
    else -> Image(
        json.getInt("width"),
        json.getInt("height"),
        json.getString("name"),
        json.getInt("size"),
        URI.create(json.getString("url"))
    )
}

fun Image?.optEncodeJson(): JSONObject? {
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

fun Length.Companion.decodeJson(json: JSONObject): Length {
    return Length(
        UnitOfMeasure.decodeJson(json.getString("unit")),
        json.getDouble("value")
    )
}


fun Length.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Length::unit) { it.wireFormat }
        putProp(this@encodeJson, Length::value)
    }
}

fun UnitOfMeasure.Companion.decodeJson(value: String): UnitOfMeasure =
    UnitOfMeasure.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown Unit type '$value'")

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

fun Offsets.Companion.decodeJson(json: JSONObject): Offsets {
    return Offsets(
        Length.decodeJson(json.getJSONObject("bottom")),
        Length.decodeJson(json.getJSONObject("center")),
        Length.decodeJson(json.getJSONObject("left")),
        Length.decodeJson(json.getJSONObject("middle")),
        Length.decodeJson(json.getJSONObject("right")),
        Length.decodeJson(json.getJSONObject("top"))
    )
}

fun Offsets.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Offsets::bottom) { it.encodeJson() }
        putProp(this@encodeJson, Offsets::center) { it.encodeJson() }
        putProp(this@encodeJson, Offsets::left) { it.encodeJson() }
        putProp(this@encodeJson, Offsets::middle) { it.encodeJson() }
        putProp(this@encodeJson, Offsets::right) { it.encodeJson() }
        putProp(this@encodeJson, Offsets::top) { it.encodeJson() }
    }
}

fun Font.Companion.decodeJson(json: JSONObject): Font {
    return Font(
        size = json.getInt("size"),
        weight = FontWeight.decodeJson(json.getString("weight"))
    )
}

fun Font.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Font::size)
        putProp(this@encodeJson, Font::weight) { it.wireFormat }
    }
}

fun Position.Companion.decodeJson(value: String): Position =
    Position.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown Position type '$value'")

fun HorizontalAlignment.Companion.decodeJson(value: String): HorizontalAlignment =
    HorizontalAlignment.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown HorizontalAlignment type '$value'.")

fun VerticalAlignment.Companion.decodeJson(value: String): VerticalAlignment =
    VerticalAlignment.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown VerticalAlignment type '$value'.")

fun TextAlignment.Companion.decodeJson(value: String): TextAlignment =
    TextAlignment.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown TextAlignment type '$value'.")

fun FontWeight.Companion.decodeJson(value: String): FontWeight =
    FontWeight.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown FontWeight type '$value'.")

fun BackgroundContentMode.Companion.decodeJson(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundContentMode type '$value'.")

fun BackgroundScale.Companion.decodeJson(value: String): BackgroundScale =
    BackgroundScale.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundScale type '$value'.")

fun StatusBarStyle.Companion.decodeJson(value: String): StatusBarStyle =
    StatusBarStyle.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown StatusBarStyle type '$value'.")

fun TitleBarButtons.Companion.decodeJson(value: String): TitleBarButtons =
    TitleBarButtons.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown StatusBTitleBarButtonsarStyle type '$value'.")

fun ButtonState.Companion.decodeJson(json: JSONObject): ButtonState {
    return ButtonState(
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        textAlignment = TextAlignment.decodeJson(json.getString("textAlignment")),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        textFont = Font.decodeJson(json.getJSONObject("textFont")),
        text = json.getString("text")
    )
}

fun ButtonState.encodeJson(): JSONObject {
    return JSONObject().apply {
        this@encodeJson.encodeBackgroundToJson(this)
        this@encodeJson.encodeBorderToJson(this)
        this@encodeJson.encodeTextToJson(this)
    }
}

fun BarcodeBlock.Companion.decodeJson(json: JSONObject): BarcodeBlock {
    return BarcodeBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        barcodeScale = json.getInt("barcodeScale"),
        barcodeText = json.getString("barcodeText"),
        barcodeFormat = BarcodeFormat.decodeJson(json.getString("barcodeFormat")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width"))
    )
}

fun Background.encodeBackgroundToJson(json: JSONObject) {
    json.putProp(this, Background::backgroundColor) { it.encodeJson() }
    json.putProp(this, Background::backgroundContentMode) { it.wireFormat }
    json.putProp(this, Background::backgroundImage) { it.optEncodeJson() ?: JSONObject.NULL }
    json.putProp(this, Background::backgroundScale) { it.wireFormat }
}

fun Border.encodeBorderToJson(json: JSONObject) {
    json.putProp(this, Border::borderColor) { it.encodeJson() }
    json.putProp(this, Border::borderRadius)
    json.putProp(this, Border::borderWidth)
}

fun Text.encodeTextToJson(json: JSONObject) {
    json.putProp(this, Text::text)
    json.putProp(this, Text::textAlignment) { it.wireFormat }
    json.putProp(this, Text::textColor) { it.encodeJson() }
    json.putProp(this, Text::textFont) { it.encodeJson() }
}

fun Block.encodeJson(experienceId: String, screenId: String, rowId: String): JSONObject {
    return JSONObject().apply {
        // do common fields
        put("experienceId", experienceId)
        put("screenId", screenId)
        put("rowId", rowId)
        putProp(this@encodeJson, Block::action) { it.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeJson, Block::autoHeight)
        putProp(this@encodeJson, Block::height) { it.encodeJson() }
        putProp(this@encodeJson, Block::id) { it.rawValue }
        putProp(this@encodeJson, Block::insets) { it.encodeJson() }
        putProp(this@encodeJson, Block::horizontalAlignment) { it.wireFormat }
        putProp(this@encodeJson, Block::offsets) { it.encodeJson() }
        putProp(this@encodeJson, Block::opacity)
        putProp(this@encodeJson, Block::position) { it.wireFormat }
        putProp(this@encodeJson, Block::verticalAlignment) { it.wireFormat }
        putProp(this@encodeJson, Block::width) { it.encodeJson() }
        put("__typename", when (this@encodeJson) {
            is BarcodeBlock -> {
                putProp(this@encodeJson, BarcodeBlock::barcodeScale)
                putProp(this@encodeJson, BarcodeBlock::barcodeText)
                putProp(this@encodeJson, BarcodeBlock::barcodeFormat) { it.wireFormat }
                this@encodeJson.encodeBackgroundToJson(this)
                this@encodeJson.encodeBorderToJson(this)
                BarcodeBlock.resourceName
            }
            is ButtonBlock -> {
                putProp(this@encodeJson, ButtonBlock::disabled) { it.encodeJson() }
                putProp(this@encodeJson, ButtonBlock::highlighted) { it.encodeJson() }
                putProp(this@encodeJson, ButtonBlock::normal) { it.encodeJson() }
                putProp(this@encodeJson, ButtonBlock::selected) { it.encodeJson() }
                ButtonBlock.resourceName
            }
            is ImageBlock -> {
                putProp(this@encodeJson, ImageBlock::image) { it.optEncodeJson() ?: JSONObject.NULL }
                this@encodeJson.encodeBackgroundToJson(this)
                this@encodeJson.encodeBorderToJson(this)
                ImageBlock.resourceName
            }
            is WebViewBlock -> {
                putProp(this@encodeJson, WebViewBlock::isScrollingEnabled)
                putProp(this@encodeJson, WebViewBlock::url) { it.toString() }
                this@encodeJson.encodeBackgroundToJson(this)
                this@encodeJson.encodeBorderToJson(this)
                WebViewBlock.resourceName
            }
            is RectangleBlock -> {
                this@encodeJson.encodeBackgroundToJson(this)
                this@encodeJson.encodeBorderToJson(this)
                RectangleBlock.resourceName
            }
            is TextBlock -> {
                this@encodeJson.encodeBackgroundToJson(this)
                this@encodeJson.encodeBorderToJson(this)
                this@encodeJson.encodeTextToJson(this)
                TextBlock.resourceName
            }
            else -> throw RuntimeException("Unsupported Block type for serialization")
        })
    }
}

fun ButtonBlock.Companion.decodeJson(json: JSONObject): ButtonBlock {
    return ButtonBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        disabled = ButtonState.decodeJson(json.getJSONObject("disabled")),
        highlighted = ButtonState.decodeJson(json.getJSONObject("highlighted")),
        normal = ButtonState.decodeJson(json.getJSONObject("normal")),
        selected = ButtonState.decodeJson(json.getJSONObject("selected"))
    )
}

fun RectangleBlock.Companion.decodeJson(json: JSONObject): RectangleBlock {
    return RectangleBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width"))
    )
}

fun WebViewBlock.Companion.decodeJson(json: JSONObject): WebViewBlock {
    return WebViewBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        isScrollingEnabled = json.getBoolean("isScrollingEnabled"),
        url = URI.create(json.getString("url"))
    )
}

fun TextBlock.Companion.decodeJson(json: JSONObject): TextBlock {
    return TextBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        textAlignment = TextAlignment.decodeJson(json.getString("textAlignment")),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        textFont = Font.decodeJson(json.getJSONObject("textFont")),
        text = json.getString("text")
    )
}

fun ImageBlock.Companion.decodeJson(json: JSONObject): ImageBlock {
    return ImageBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        image = Image.optDecodeJSON(json.optJSONObject("image"))
    )
}

val BlockAction.OpenUrlAction.Companion.resourceName get() = "OpenUrlAction"
val BlockAction.GoToScreenAction.Companion.resourceName get() = "GoToScreenAction"

fun BlockAction.Companion.optDecodeJson(json: JSONObject?): BlockAction? {
    if (json == null) return null
    // BlockAction has subclasses, so we need to delegate to the appropriate deserializer for each
    // block action type.

    val typeName = json.getString("__typename")

    return when (typeName) {
        BlockAction.OpenUrlAction.resourceName -> BlockAction.OpenUrlAction(
            url = URI.create(json.getString("url"))
        )
        BlockAction.GoToScreenAction.resourceName -> BlockAction.GoToScreenAction(
            experienceId = ID(json.getString("experienceId")),
            screenId = ID(json.getString("screenId"))
        )
        else -> throw RuntimeException("Unsupported Block Action type '$typeName'.")
    }
}

fun BlockAction?.optEncodeJson(): JSONObject? {
    return this?.let {
        JSONObject().apply {
            put("__typename", when (this@optEncodeJson) {
                is BlockAction.OpenUrlAction -> {
                    putProp(this@optEncodeJson, BlockAction.OpenUrlAction::url) { it.toString() }
                    BlockAction.OpenUrlAction.resourceName
                }
                is BlockAction.GoToScreenAction -> {
                    putProp(this@optEncodeJson, BlockAction.GoToScreenAction::experienceId) { it.rawValue }
                    putProp(this@optEncodeJson, BlockAction.GoToScreenAction::screenId) { it.rawValue }
                    BlockAction.GoToScreenAction.resourceName
                }
            })
        }
    }
}

val BarcodeBlock.Companion.resourceName get() = "BarcodeBlock"
val ButtonBlock.Companion.resourceName get() = "ButtonBlock"
val RectangleBlock.Companion.resourceName get() = "RectangleBlock"
val WebViewBlock.Companion.resourceName get() = "WebViewBlock"
val TextBlock.Companion.resourceName get() = "TextBlock"
val ImageBlock.Companion.resourceName get() = "ImageBlock"

fun Block.Companion.decodeJson(json: JSONObject): Block {
    // Block has subclasses, so we need to delegate to the appropriate deserializer for each
    // block type.
    val typeName = json.getString("__typename")

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

fun Row.Companion.decodeJSON(json: JSONObject, namedField: String? = null): Row {
    return Row(
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        blocks = json.getJSONArray("blocks").getObjectIterable().map { Block.decodeJson(it) },
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id"))
    )
}

fun Row.encodeJson(experienceId: String, screenId: String): JSONObject {
    return JSONObject().apply {
        put("experienceId", experienceId)
        put("screenId", screenId)
        putProp(this@encodeJson, Row::autoHeight)
        putProp(this@encodeJson, Row::backgroundColor) { it.encodeJson() }
        putProp(this@encodeJson, Row::backgroundContentMode) { it.wireFormat }
        putProp(this@encodeJson, Row::backgroundImage) { it.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeJson, Row::backgroundScale) { it.wireFormat }
        putProp(this@encodeJson, Row::blocks) { JSONArray(it.map { it.encodeJson(experienceId, screenId, this@encodeJson.id.rawValue) }) }
        putProp(this@encodeJson, Row::height) { it.encodeJson() }
        putProp(this@encodeJson, Row::id) { it.rawValue }
    }
}

fun Screen.Companion.decodeJson(json: JSONObject): Screen {
    return Screen(
        autoColorStatusBar = json.getBoolean("autoColorStatusBar"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJson(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        id = ID(json.getString("id")),
        isStretchyHeaderEnabled = json.getBoolean("isStretchyHeaderEnabled"),
        rows = json.getJSONArray("rows").getObjectIterable().map {
            Row.decodeJSON(it)
        },
        statusBarStyle = StatusBarStyle.decodeJson(json.getString("statusBarStyle")),
        statusBarColor = Color.decodeJson(json.getJSONObject("statusBarColor")),
        titleBarBackgroundColor = Color.decodeJson(json.getJSONObject("titleBarBackgroundColor")),
        titleBarButtons = TitleBarButtons.decodeJson(json.getString("titleBarButtons")),
        titleBarButtonColor = Color.decodeJson(json.getJSONObject("titleBarButtonColor")),
        titleBarText = json.getString("titleBarText"),
        titleBarTextColor = Color.decodeJson(json.getJSONObject("titleBarTextColor")),
        useDefaultTitleBarStyle = json.getBoolean("useDefaultTitleBarStyle")
    )
}

fun Screen.encodeJson(experienceId: String): JSONObject {
    return JSONObject().apply {
        val primitiveProps = listOf(
            Screen::autoColorStatusBar,
            Screen::isStretchyHeaderEnabled,
            Screen::titleBarText,
            Screen::useDefaultTitleBarStyle
        )

        primitiveProps.forEach { putProp(this@encodeJson, it) }
        put("experienceId", experienceId)

        putProp(this@encodeJson, Screen::backgroundColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::backgroundContentMode) { it.wireFormat }
        putProp(this@encodeJson, Screen::backgroundImage) { it.optEncodeJson() ?: JSONObject.NULL }
        putProp(this@encodeJson, Screen::backgroundScale) { it.wireFormat }
        putProp(this@encodeJson, Screen::id) { it.rawValue }
        putProp(this@encodeJson, Screen::rows) { JSONArray(it.map { it.encodeJson(experienceId, this@encodeJson.id.rawValue) }) }
        putProp(this@encodeJson, Screen::statusBarStyle) { it.wireFormat }
        putProp(this@encodeJson, Screen::statusBarColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBarBackgroundColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBarButtons) { it.wireFormat }
        putProp(this@encodeJson, Screen::titleBarButtonColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBarTextColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::titleBarBackgroundColor) { it.encodeJson() }
        putProp(this@encodeJson, Screen::useDefaultTitleBarStyle)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.getIterable(): Iterable<T> {
    return object : Iterable<T> {
        private var counter = 0
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                override fun hasNext(): Boolean = counter < this@getIterable.length()

                override fun next(): T {
                    if (counter >= this@getIterable.length()) {
                        throw Exception("Iterator ran past the end!")
                    }
                    val jsonObject = this@getIterable.get(counter)
                    counter++
                    return jsonObject as T
                }
            }
        }
    }
}

fun JSONArray.getObjectIterable(): Iterable<JSONObject> = getIterable()

fun JSONArray.getStringIterable(): Iterable<String> = getIterable()