package io.rover.sdk.data.domain

import android.util.DisplayMetrics
import android.view.Gravity
import io.rover.sdk.data.domain.BackgroundScale.X3
import java.net.URI
import java.net.URL

/**
 * A Rover experience.
 */
data class Experience(
    val id: String,
    val homeScreenId: String,
    val screens: List<Screen>,
    val keys: Map<String, String>,
    val tags: List<String>,
    val name: String
) {
    companion object
}

data class Background(
    val color: Color,
    val contentMode: BackgroundContentMode,
    val image: Image?,
    val scale: BackgroundScale
) {
    companion object
}

enum class BackgroundContentMode(
    val wireFormat: String
) {
    Original("ORIGINAL"),
    Stretch("STRETCH"),
    Tile("TILE"),
    Fill("FILL"),
    Fit("FIT");

    companion object
}

/**
 * Scale values define the relation between the image's pixels and logical pixels (DP on
 * Android, points on iOS), in terms of an historic iOS-centric value, 160 dpi.  This is an
 * iOS naming convention.  So, if your image asset is a HiDPI/Retina/xxhdpi one, use [X3].
 * See the documentation for the three values for specifics.
 */
enum class BackgroundScale(
    val wireFormat: String
) {
    /**
     * Lowest density image at 160 dpi.
     *
     * Equivalent to Android's [DisplayMetrics.DENSITY_MEDIUM].
     */
    X1("X1"),

    /**
     * Medium density image at 320 dpi.
     *
     * Equivalent to Android's [DisplayMetrics.DENSITY_XHIGH].
     */
    X2("X2"),

    /**
     * Highest density image at 480 dpi.
     *
     * Equivalent to Android's [DisplayMetrics.DENSITY_XXHIGH].
     */
    X3("X3");

    companion object
}

data class Position(
    val horizontalAlignment: HorizontalAlignment,
    val verticalAlignment: VerticalAlignment
) {
    companion object
}

interface Block {
    val tapBehavior: TapBehavior
    val background: Background
    val border: Border
    val id: String
    val insets: Insets
    val opacity: Double
    val position: Position
    val keys: Map<String, String>
    val tags: List<String>
    val name: String

    sealed class TapBehavior {
        /**
         * Tapping the block should navigate to the given screen in the experience.
         */
        data class GoToScreen(val screenId: String) : TapBehavior()

        /**
         * Tapping the block should open the following URI.  The URI may be a Rover deep link URI,
         * so it will be run through the [Router] before it should be opened on Android as an
         * Intent.
         */
        data class OpenUri(val uri: URI) : TapBehavior()

        data class PresentWebsite(val url: URI) : TapBehavior()

        object None : TapBehavior()

        object Custom : TapBehavior()

        companion object
    }

    companion object
}

data class ImageBlockOption(
    val text: String,
    val image: Image
) {
    companion object
}

data class QuestionStyle(
    val color: Color,
    val font: Font,
    val textAlignment: TextAlignment
) {
    companion object
}

data class ImagePollBlockOptionStyle(
    val opacity: Double,
    val color: Color,
    val border: Border,
    val font: Font,
    val textAlignment: TextAlignment,
    val resultFillColor: Color,
    val verticalSpacing: Int,
    val horizontalSpacing: Int
) {
    companion object
}

sealed class PollBlock : Block {
    companion object
}

data class ImagePollBlock(
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val tapBehavior: Block.TapBehavior,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val question: String,
    val options: List<ImageBlockOption>,
    val questionStyle: QuestionStyle,
    val optionStyle: ImagePollBlockOptionStyle
) : PollBlock() {
    companion object
}

data class TextPollBlockOptionStyle(
    val height: Int,
    val opacity: Double,
    val border: Border,
    val color: Color,
    val font: Font,
    val textAlignment: TextAlignment,
    val resultFillColor: Color,
    val background: Background,
    val verticalSpacing: Int
) {
    companion object
}

data class TextPollBlock(
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val tapBehavior: Block.TapBehavior,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val question: String,
    val options: List<String>,
    val questionStyle: QuestionStyle,
    val optionStyle: TextPollBlockOptionStyle
) : PollBlock() {
    companion object
}

data class BarcodeBlock(
    override val tapBehavior: Block.TapBehavior,
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    @Deprecated("BarcodeBlock does not have a background.")
    override val background: Background,
    @Deprecated("BarcodeBlock does not have a border.")
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val barcode: Barcode
) : Block {
    companion object
}

data class ButtonBlock(
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val tapBehavior: Block.TapBehavior,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val text: Text
) : Block {
    companion object
}

data class ImageBlock(
    override val tapBehavior: Block.TapBehavior,
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val image: Image?
) : Block {
    companion object
}

data class RectangleBlock(
    override val tapBehavior: Block.TapBehavior,
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>
) : Block {
    companion object
}

data class TextBlock(
    override val tapBehavior: Block.TapBehavior,
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val text: Text
) : Block {
    companion object
}

data class WebViewBlock(
    override val tapBehavior: Block.TapBehavior,
    override val id: String,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Map<String, String>,
    override val background: Background,
    override val border: Border,
    override val name: String,
    override val tags: List<String>,
    val webView: WebView
) : Block {
    companion object
}

data class Border(
    val color: Color,
    val radius: Int,
    val width: Int
) {
    companion object
}

data class Color(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Double
) {
    companion object
}

data class Font(
    val size: Int,
    val weight: FontWeight
) {
    companion object
}

enum class FontWeight(
    val wireFormat: String
) {
    /**
     * Font weight 100.
     */
    UltraLight("ULTRA_LIGHT"),

    /**
     * Font weight 200.
     */
    Thin("THIN"),

    /**
     * Font weight 300.
     */
    Light("LIGHT"),

    /**
     * Font weight 400.
     */
    Regular("REGULAR"),

    /**
     * Font weight 500.
     */
    Medium("MEDIUM"),

    /**
     * Font weight 600.
     */
    SemiBold("SEMI_BOLD"),

    /**
     * Font weight 700.
     */
    Bold("BOLD"),

    /**
     * Font weight 800.
     */
    Heavy("HEAVY"),

    /**
     * Font weight 900.
     */
    Black("BLACK");

    companion object
}

sealed class HorizontalAlignment {
    interface Measured {
        val width: Double
    }
    data class Center(val offset: Double, override val width: Double) : HorizontalAlignment(), Measured
    data class Left(val offset: Double, override val width: Double) : HorizontalAlignment(), Measured
    data class Right(val offset: Double, override val width: Double) : HorizontalAlignment(), Measured
    data class Fill(val leftOffset: Double, val rightOffset: Double) : HorizontalAlignment()

    companion object
}

sealed class Height {
    /**
     * The height of the given block is determined by its contents.
     */
    class Intrinsic : Height()

    /**
     * The height of the given block is specified explicitly.
     */
    data class Static(val value: Double) : Height()

    companion object
}

sealed class VerticalAlignment {
    /**
     * This alignment explicitly specifies a height.  Otherwise, the height is dependent on the
     * other contents of the row.
     */
    interface Measured {
        val height: Height
    }

    data class Bottom(val offset: Double, override val height: Height) : VerticalAlignment(), Measured
    data class Middle(val offset: Double, override val height: Height) : VerticalAlignment(), Measured
    data class Fill(val topOffset: Double, val bottomOffset: Double) : VerticalAlignment()
    data class Stacked(val topOffset: Double, val bottomOffset: Double, override val height: Height) : VerticalAlignment(), Measured
    data class Top(val offset: Double, override val height: Height) : VerticalAlignment(), Measured

    companion object
}

data class Image(
    val width: Int,
    val height: Int,
    val name: String,
    val size: Int,
    val url: URI
) {
    companion object
}

data class Insets(
    val bottom: Int,
    val left: Int,
    val right: Int,
    val top: Int
) {
    companion object
}

data class Row(
    val background: Background,
    val blocks: List<Block>,
    val height: Height,
    val keys: Map<String, String>,
    val id: String,
    val name: String,
    val tags: List<String>
) {
    companion object
}

data class StatusBar(
    val style: StatusBarStyle,
    val color: Color
) {
    companion object
}

data class TitleBar(
    val backgroundColor: Color,
    val buttons: TitleBarButtons,
    val buttonColor: Color,
    val text: String,
    val textColor: Color,
    val useDefaultStyle: Boolean
) {
    companion object
}

data class Screen(
    val id: String,
    val isStretchyHeaderEnabled: Boolean,
    val rows: List<Row>,
    val background: Background,
    val titleBar: TitleBar,
    val statusBar: StatusBar,
    val keys: Map<String, String>,
    val tags: List<String>,
    val name: String
) {
    companion object
}

enum class StatusBarStyle(
    val wireFormat: String
) {
    Dark("DARK"),
    Light("LIGHT");

    companion object
}

data class Text(
    val rawValue: String,
    val alignment: TextAlignment,
    val color: Color,
    val font: Font
) {
    companion object
}

enum class TextAlignment(
    val wireFormat: String
) {
    Center("CENTER"),
    Left("LEFT"),
    Right("RIGHT");

    fun convertToGravity(): Int {
        return when (this) {
            Right -> Gravity.END
            Left -> Gravity.START
            Center -> Gravity.CENTER_HORIZONTAL
        }
    }

    companion object
}

enum class TitleBarButtons(
    val wireFormat: String
) {
    Close("CLOSE"),
    Back("BACK"),
    Both("BOTH");

    companion object
}

enum class BarcodeFormat(
    val wireFormat: String
) {
    QrCode("QR_CODE"),
    AztecCode("AZTEC_CODE"),
    Pdf417("PDF417"),
    Code128("CODE_128");

    companion object
}

data class Barcode(
    val text: String,
    val format: BarcodeFormat
) {
    companion object
}

data class WebView(
    val isScrollingEnabled: Boolean,
    val url: URL
) {
    companion object
}

enum class UnitOfMeasure(
    val wireFormat: String
) {
    /**
     * The value is an absolute one, in display independent pixels (dp on Android, points on iOS).
     */
    Points("POINTS"),

    /**
     * The value is a proportional one.
     */
    Percentage("PERCENTAGE");

    companion object
}
