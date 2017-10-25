package io.rover.rover

import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Color
import io.rover.rover.core.domain.HorizontalAlignment
import io.rover.rover.core.domain.ID
import io.rover.rover.core.domain.Image
import io.rover.rover.core.domain.Insets
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Offsets
import io.rover.rover.core.domain.Position
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.domain.StatusBarStyle
import io.rover.rover.core.domain.TitleBarButtons
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment

class ModelFactories {
    companion object {
        /**
         * Construct an empty [Screen].
         */
        fun emptyScreen(): Screen {
            return Screen(
                autoColorStatusBar = false,
                backgroundColor = Black,
                backgroundContentMode = BackgroundContentMode.Original,
                backgroundImage = null,
                backgroundScale = BackgroundScale.X1,
                id = ID("0"),
                isStretchyHeaderEnabled = false,
                rows = listOf(),
                statusBarStyle = StatusBarStyle.Dark,
                statusBarColor = Color(0, 0, 0x7f, 1.0),
                titleBarBackgroundColor = Transparent,
                titleBarButtons = TitleBarButtons.Both,
                titleBarButtonColor = DarkBlue,
                titleBarText = "Title bar",
                titleBarTextColor = Color(0xff, 0xff, 0xff, 1.0),
                useDefaultTitleBarStyle = true
            ).copy()
        }

        fun emptyRow(): Row {
            return Row(
                autoHeight = false,
                backgroundColor = Transparent,
                backgroundContentMode = BackgroundContentMode.Original,
                backgroundImage = null,
                backgroundScale = BackgroundScale.X1,
                blocks = listOf(),
                height = Length(UnitOfMeasure.Points, 0.0),
                id = ID("0")
            )
        }

        fun emptyRectangleBlock(): RectangleBlock {
            return RectangleBlock(
                action = null,
                autoHeight = false,
                backgroundColor = Transparent,
                backgroundContentMode = BackgroundContentMode.Original,
                backgroundImage = null,
                backgroundScale = BackgroundScale.X1,
                borderColor = Black,
                borderWidth = 1,
                borderRadius = 0,
                height = Length(UnitOfMeasure.Points, 0.0),
                id = ID(""),
                insets = Insets(0, 0, 0, 0),
                horizontalAlignment = HorizontalAlignment.Fill,
                offsets = Offsets(
                    Length(UnitOfMeasure.Points, 0.0),
                    Length(UnitOfMeasure.Points, 0.0),
                    Length(UnitOfMeasure.Points, 0.0),
                    Length(UnitOfMeasure.Points, 0.0),
                    Length(UnitOfMeasure.Points, 0.0),
                    Length(UnitOfMeasure.Points, 0.0)
                ),
                position = Position.Floating,
                verticalAlignment = VerticalAlignment.Top,
                opacity = 1.0,
                width = Length(UnitOfMeasure.Points, 0.0)
            )
        }

        fun zeroOffsets(): Offsets {
            return Offsets(
                Length(UnitOfMeasure.Points, 0.0),
                Length(UnitOfMeasure.Points, 0.0),
                Length(UnitOfMeasure.Points, 0.0),
                Length(UnitOfMeasure.Points, 0.0),
                Length(UnitOfMeasure.Points, 0.0),
                Length(UnitOfMeasure.Points, 0.0)
            )
        }

        val White = Color(0xff, 0xff, 0xff, 1.0)
        val Black = Color(0, 0, 0, 1.0)
        val Transparent = Color(0xff, 0xff, 0xff, 0.0)
        val DarkBlue = Color(0, 0, 0x7f, 1.0)
    }
}
