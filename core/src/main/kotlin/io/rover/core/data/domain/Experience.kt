package io.rover.core.data.domain

import android.util.DisplayMetrics
import io.rover.core.data.domain.BackgroundScale.X3
import io.rover.core.routing.Router
import java.net.URI
import java.net.URL

/**
 * A Rover experience.
 */
data class Experience(
    val id: ID,
    val homeScreenId: ID,
    val screens: List<Screen>,
    val keys: Attributes,
    val campaignId: String?
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
    val tapBehavior: TapBehavior?
    val background: Background
    val border: Border
    val id: ID
    val insets: Insets
    val opacity: Double
    val position: Position
    val keys: Attributes

    companion object;

    sealed class TapBehavior {
        /**
         * Tapping the block should navigate to the given screen in the experience.
         */
        data class GoToScreen(val screenId: ID): TapBehavior()

        /**
         * Tapping the block should open the following URI.  The URI may be a Rover deep link URI,
         * so it will be run through the [Router] before it should be opened on Android as an
         * Intent.
         */
        data class OpenUri(val uri: URI): TapBehavior()

        data class PresentWebsite(val url: URI): TapBehavior()

        class None: TapBehavior()

        companion object
    }
}

data class BarcodeBlock(
    override val tapBehavior: Block.TapBehavior?,
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val background: Background,
    override val border: Border,
    val barcode: Barcode
) : Block {
    companion object
}

data class ButtonBlock(
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val tapBehavior: Block.TapBehavior?,
    override val background: Background,
    override val border: Border,
    val text: Text
) : Block {
    companion object
}

data class ImageBlock(
    override val tapBehavior: Block.TapBehavior?,
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val background: Background,
    override val border: Border,
    val image: Image?
) : Block {
    companion object
}

data class RectangleBlock(
    override val tapBehavior: Block.TapBehavior?,
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val background: Background,
    override val border: Border
) : Block {
    companion object
}

data class TextBlock(
    override val tapBehavior: Block.TapBehavior?,
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val background: Background,
    override val border: Border,
    val text: Text
) : Block {
    companion object
}

data class WebViewBlock(
    override val tapBehavior: Block.TapBehavior?,
    override val id: ID,
    override val insets: Insets,
    override val opacity: Double,
    override val position: Position,
    override val keys: Attributes,
    override val background: Background,
    override val border: Border,
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
    data class Center(val offset: Double, override val width: Double): HorizontalAlignment(), Measured
    data class Left(val offset: Double, override val width: Double): HorizontalAlignment(), Measured
    data class Right(val offset: Double, override val width: Double): HorizontalAlignment(), Measured
    data class Fill(val leftOffset: Double, val rightOffset: Double): HorizontalAlignment()

    companion object
}

sealed class Height {
    /**
     * The height of the given block is determined by its contents.
     */
    class Intrinsic: Height()

    /**
     * The height of the given block is specified explicitly.
     */
    data class Static(val value: Double): Height()

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

    data class Bottom(val offset: Double, override val height: Height): VerticalAlignment(), Measured
    data class Middle(val offset: Double, override val height: Height): VerticalAlignment(), Measured
    data class Fill(val topOffset: Double, val bottomOffset: Double): VerticalAlignment()
    data class Stacked(val topOffset: Double, val bottomOffset: Double, override val height: Height): VerticalAlignment(), Measured
    data class Top(val offset: Double, override val height: Height): VerticalAlignment(), Measured

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
    val id: ID
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
    val id: ID,
    val isStretchyHeaderEnabled: Boolean,
    val rows: List<Row>,
    val background: Background,
    val titleBar: TitleBar,
    val statusBar: StatusBar,
    val keys: Attributes
)  {
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
    val scale: Int,
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
