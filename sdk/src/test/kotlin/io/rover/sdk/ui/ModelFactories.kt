package io.rover.sdk.ui

import io.rover.helpers.shouldBeInstanceOf
import io.rover.helpers.shouldEqual
import io.rover.sdk.data.domain.Background
import io.rover.sdk.data.domain.BackgroundContentMode
import io.rover.sdk.data.domain.BackgroundScale
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Border
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.Height
import io.rover.sdk.data.domain.HorizontalAlignment
import io.rover.sdk.data.domain.Insets
import io.rover.sdk.data.domain.Position
import io.rover.sdk.data.domain.RectangleBlock
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.domain.StatusBar
import io.rover.sdk.data.domain.StatusBarStyle
import io.rover.sdk.data.domain.TitleBar
import io.rover.sdk.data.domain.TitleBarButtons
import io.rover.sdk.data.domain.VerticalAlignment
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.layout.DisplayItem

internal class ModelFactories {
    companion object {
        /**
         * Construct an empty [Screen].
         */
        fun emptyScreen(): Screen {
            return Screen(
                background = Background(
                    color = Black,
                    contentMode = BackgroundContentMode.Original,
                    image = null,
                    scale = BackgroundScale.X1
                ),
                id = "0",
                isStretchyHeaderEnabled = false,
                rows = listOf(),
                statusBar = StatusBar(
                    style = StatusBarStyle.Dark,
                    color = Color(0, 0, 0x7f, 1.0)
                ),
                titleBar = TitleBar(
                    backgroundColor = Transparent,
                    buttons = TitleBarButtons.Both,
                    buttonColor = DarkBlue,
                    text = "Title bar",
                    textColor = Color(0xff, 0xff, 0xff, 1.0),
                    useDefaultStyle = true
                ),
                keys = emptyMap(),
                name = "An empty screen",
                tags = emptyList()
            ).copy()
        }

        fun emptyRow(): Row {
            return Row(
                height = Height.Static(
                    0.0
                ),
                background = Background(
                    color = Black,
                    contentMode = BackgroundContentMode.Original,
                    image = null,
                    scale = BackgroundScale.X1
                ),
                blocks = listOf(),
                id = "0",
                keys = emptyMap(),
                name = "Row 1",
                tags = emptyList()
            )
        }

        fun emptyRectangleBlock(): RectangleBlock {
            return RectangleBlock(
                tapBehavior = Block.TapBehavior.None(),
                position = Position(
                    horizontalAlignment = HorizontalAlignment.Fill(
                        0.0, 0.0
                    ),
                    verticalAlignment = VerticalAlignment.Top(
                        0.0, Height.Static(0.0)
                    )
                ),
                background = Background(
                    color = Black,
                    contentMode = BackgroundContentMode.Original,
                    image = null,
                    scale = BackgroundScale.X1
                ),
                border = Border(
                    color = Black,
                    radius = 0,
                    width = 1
                ),
                id = "",
                insets = Insets(0, 0, 0, 0),
                opacity = 1.0,
                keys = emptyMap(),
                name = "Example Rectangle Block",
                tags = emptyList()
            )
        }

        val White = Color(0xff, 0xff, 0xff, 1.0)
        val Black = Color(0, 0, 0, 1.0)
        val Transparent = Color(0xff, 0xff, 0xff, 0.0)
        val DarkBlue = Color(0, 0, 0x7f, 1.0)
    }
}

internal fun DisplayItem.shouldMatch(
    position: RectF,
    type: Class<out LayoutableViewModel>,
    clip: RectF? = null
) {
    this.position shouldEqual position
    this.viewModel shouldBeInstanceOf type
    this.clip shouldEqual clip
}