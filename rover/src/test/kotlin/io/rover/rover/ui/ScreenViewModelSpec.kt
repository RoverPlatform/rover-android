package io.rover.rover.ui

import android.graphics.Rect
import io.rover.rover.ModelFactories
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.domain.UnitOfMeasure
import org.amshove.kluent.any
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.junit.runner.RunWith

class ScreenViewModelSpec: Spek({
    given("a basic screen with one row with a rectangle block") {
        val screen = ModelFactories.emptyScreen().copy(
            rows = listOf(
                ModelFactories.emptyRow().copy(
                    height = Length(UnitOfMeasure.Points, 10.0),
                    blocks = listOf(
                        ModelFactories.emptyRectangleBlock().copy(
                            height = Length(UnitOfMeasure.Points, 10.0)
                        )
                    )
                )
            )
        )
        val screenViewModel = ScreenViewModel(screen)

        on("rendering") {
            val rendered = screenViewModel.render(
                40
            )

            it("should put the directly on top of the row") {
                // we have two
                rendered[0].shouldMatch(
                    Rect(0, 0, 40, -10),
                    RowViewModel::class.java
                )

                rendered[1].shouldMatch(
                    Rect(0, 0, 40, -10),
                    // TODO: change to RectangleBlockViewModel
                    BlockViewModel::class.java
                )
            }
        }
    }

    given("a screen with two rows") {
        val screen = ModelFactories.emptyScreen().copy(
            rows = listOf(
                ModelFactories.emptyRow().copy(
                    height = Length(UnitOfMeasure.Points, 10.0),
                    blocks = listOf(
                        ModelFactories.emptyRectangleBlock().copy(
                            height = Length(UnitOfMeasure.Points, 10.0)
                        )
                    )
                ),
                ModelFactories.emptyRow().copy(
                    height = Length(UnitOfMeasure.Points, 42.0)
                )
            )
        )
        val screenViewModel = ScreenViewModel(screen)

        on("rendering") {
            val rendered = screenViewModel.render(
                40
            )

            it("should put the directly on top of the row") {
                // we have two
                rendered[0].shouldMatch(
                    Rect(0, 0, 40, -10),
                    RowViewModel::class.java
                )

                rendered[1].shouldMatch(
                    Rect(0, 0, 40, -10),
                    // TODO: change to RectangleBlockViewModel
                    BlockViewModel::class.java
                )

                rendered[2].shouldMatch(
                    // check that the rows are stacked properly: 0 - 10 - 42 = -52
                    Rect(0, -10, 40, -52),
                    RowViewModel::class.java
                )
            }
        }
    }
})

fun Pair<Rect, LayoutableViewModel>.shouldMatch(
    position: Rect,
    type: Class<out LayoutableViewModel>
) {
    this.first.shouldEqual(position)
    this.second.javaClass.shouldEqual(type)
}