package io.rover.rover.services.network.requests.data

import android.graphics.Rect
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.BarcodeBlock
import io.rover.rover.core.domain.BarcodeFormat
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.BlockAction
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
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.core.domain.TitleBarButtons
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment
import io.rover.rover.core.domain.WebViewBlock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

fun Experience.Companion.decodeJson(json: JSONObject): Experience {
    return Experience(
        id = ID(json.getString("id")),
        homeScreen = Screen.decodeJson(json.getJSONObject("homeScreen")),
        screens = json.getJSONArray("screens").getObjectIterable().map {
            Screen.decodeJson(it)
        }
    )
}

fun Color.Companion.decodeJson(json: JSONObject): Color {
    return Color(
        json.getInt("red"),
        json.getInt("green"),
        json.getInt("blue"),
        json.getDouble("alpha")
    )
}

fun BackgroundContentMode.Companion.decodeJSON(value: String): BackgroundContentMode =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundContentMode type '$value'")

fun BarcodeFormat.Companion.decodeJson(value: String): BarcodeFormat =
    BarcodeFormat.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BarcodeFormat value '$value'")

fun Image.Companion.optDecodeJSON(json: JSONObject?): Image? = when(json) {
    null -> null
    else -> Image(
        json.getInt("height"),
        json.getBoolean("isURLOptimizationEnabled"),
        json.getString("name"),
        json.getInt("size"),
        json.getInt("width"),
        URL(json.getString("url"))
    )
}

fun Length.Companion.decodeJson(json: JSONObject): Length {
    return Length(
        UnitOfMeasure.decodeJson(json.getString("unit")),
        json.getDouble("value")
    )
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

fun Font.Companion.decodeJson(json: JSONObject): Font {
    return Font(
        size = json.getInt("size"),
        weight = FontWeight.decodeJson(json.getString("weight"))
    )
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

fun BackgroundContentMode.Companion.decodeJson(value: String): BackgroundContentMode  =
    BackgroundContentMode.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundContentMode type '$value'.")

fun BackgroundScale.Companion.decodeJson(value: String): BackgroundScale  =
    BackgroundScale.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundScale type '$value'.")

fun StatusBarStyle.Companion.decodeJson(value: String): StatusBarStyle  =
    StatusBarStyle.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown StatusBarStyle type '$value'.")

fun TitleBarButtons.Companion.decodeJson(value: String): TitleBarButtons  =
    TitleBarButtons.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown StatusBTitleBarButtonsarStyle type '$value'.")

fun ButtonState.Companion.decodeJson(json: JSONObject): ButtonState {
    return ButtonState(
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        textAlignment = TextAlignment.decodeJson(json.getString("textAlignment")),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        textFont = Font.decodeJson(json.getJSONObject("textFont")),
        textValue = json.getString("textValue")
    )
}

fun BarcodeBlock.Companion.decodeJson(json: JSONObject): BarcodeBlock {
    return BarcodeBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        barcodeScale = json.getInt("barcodeScale"),
        barcodeText = json.getString("barcodeText"),
        barcodeFormat = BarcodeFormat.decodeJson(json.getString("barcodeFormat")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width"))
    )
}

fun ButtonBlock.Companion.decodeJson(json: JSONObject): ButtonBlock {
    return ButtonBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
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
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
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
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        isScrollingEnabled = json.getBoolean("isScrollingEnabled"),
        url = URL(json.getString("url"))
    )
}

fun TextBlock.Companion.decodeJson(json: JSONObject): TextBlock {
    return TextBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        textAlignment = TextAlignment.decodeJson(json.getString("textAlignment")),
        textColor = Color.decodeJson(json.getJSONObject("textColor")),
        textFont = Font.decodeJson(json.getJSONObject("textFont")),
        textValue = json.getString("textValue")
    )
}

fun ImageBlock.Companion.decodeJson(json: JSONObject): ImageBlock {
    return ImageBlock(
        action = BlockAction.optDecodeJson(json.optJSONObject("action")),
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        borderColor = Color.decodeJson(json.getJSONObject("borderColor")),
        borderRadius = json.getInt("borderRadius"),
        borderWidth = json.getInt("borderWidth"),
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        insets = Insets.decodeJson(json.getJSONObject("insets")),
        horizontalAlignment = HorizontalAlignment.decodeJson(json.getString("horizontalAlignment")),
        offsets = Offsets.decodeJson(json.getJSONObject("offsets")),
        opacity = json.getDouble("opacity"),
        position = Position.decodeJson(json.getString("position")),
        rowID = ID(json.getString("rowID")),
        screenID = ID(json.getString("screenID")),
        verticalAlignment = VerticalAlignment.decodeJson(json.getString("verticalAlignment")),
        width = Length.decodeJson(json.getJSONObject("width")),
        image = Image.optDecodeJSON(json.optJSONObject("image"))
    )
}

fun BlockAction.Companion.optDecodeJson(json: JSONObject?): BlockAction? {
    if(json == null) return null
    // BlockAction has subclasses, so we need to delegate to the appropriate deserializer for each
    // block action type.

    val typeName = json.getString("__typename")

    return when(typeName) {
        BlockAction.OpenUrlAction::class.java.simpleName -> BlockAction.OpenUrlAction(
            experienceID = ID(json.getString("experienceID")),
            screenID = ID(json.getString("screenID"))
        )
        BlockAction.GoToScreenAction::class.java.simpleName -> BlockAction.GoToScreenAction(
            url = URL(json.getString("url"))
        )
        else -> throw RuntimeException("Unsupported Block Action type '$typeName'.")
    }
}

fun Block.Companion.decodeJson(json: JSONObject): Block {
    // Block has subclasses, so we need to delegate to the appropriate deserializer for each
    // block type.
    val typeName = json.getString("__typename")

    return when(typeName) {
        BarcodeBlock::class.java.simpleName -> BarcodeBlock.decodeJson(json)
        ButtonBlock::class.java.simpleName -> ButtonBlock.decodeJson(json)
        RectangleBlock::class.java.simpleName -> RectangleBlock.decodeJson(json)
        WebViewBlock::class.java.simpleName -> WebViewBlock.decodeJson(json)
        TextBlock::class.java.simpleName -> TextBlock.decodeJson(json)
        ImageBlock::class.java.simpleName -> ImageBlock.decodeJson(json)
        else -> throw RuntimeException("Unsupported Block type '$typeName'.")
    }
}

fun BackgroundScale.Companion.decodeJSON(value: String): BackgroundScale =
    BackgroundScale.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown BackgroundScale type $value")

fun Row.Companion.decodeJSON(json: JSONObject, namedField: String? = null): Row {
    return Row(
        autoHeight = json.getBoolean("autoHeight"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJSON(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJSON(json.getString("backgroundScale")),
        blocks = json.getJSONArray("blocks").getObjectIterable().map { Block.decodeJson(it) },
        experienceID = ID(json.getString("experienceID")),
        height = Length.decodeJson(json.getJSONObject("height")),
        id = ID(json.getString("id")),
        screenID = ID(json.getString("screenID"))
    )
}

fun Screen.Companion.decodeJson(json: JSONObject): Screen {
    return Screen(
        autoColorStatusBar = json.getBoolean("autoColorStatusBar"),
        backgroundColor = Color.decodeJson(json.getJSONObject("backgroundColor")),
        backgroundContentMode = BackgroundContentMode.decodeJson(json.getString("backgroundContentMode")),
        backgroundImage = Image.optDecodeJSON(json.optJSONObject("backgroundImage")),
        backgroundScale = BackgroundScale.decodeJson(json.getString("backgroundScale")),
        experienceID = ID(json.getString("experienceID")),
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

fun JSONArray.getObjectIterable(): Iterable<JSONObject> {
    return object : Iterable<JSONObject> {
        private var counter = 0
        override fun iterator(): Iterator<JSONObject> {
            return object : Iterator<JSONObject> {
                override fun hasNext(): Boolean = counter < this@getObjectIterable.length()

                override fun next(): JSONObject {
                    if(counter >= this@getObjectIterable.length()) {
                        throw Exception("Iterator ran past the end!")
                    }
                    val jsonObject = this@getObjectIterable.getJSONObject(counter)
                    counter++
                    return jsonObject
                }

            }
        }

    }
}

fun JSONArray.getStringIterable(): Iterable<String> {
    return object : Iterable<String> {
        private var counter = 0
        override fun iterator(): Iterator<String> {
            return object : Iterator<String> {
                override fun hasNext(): Boolean = counter < this@getStringIterable.length()

                override fun next(): String {
                    if(counter >= this@getStringIterable.length()) {
                        throw Exception("Iterator ran past the end!")
                    }
                    val string = this@getStringIterable.getString(counter)
                    counter++
                    return string
                }

            }
        }

    }
}