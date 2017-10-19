package io.rover.rover.ui

import android.graphics.Rect
import android.graphics.RectF
import io.rover.rover.ModelFactories
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment
import io.rover.rover.ui.viewmodels.LayoutableViewModel
import io.rover.rover.ui.viewmodels.RectangleBlockViewModel
import io.rover.rover.ui.viewmodels.RowViewModel
import io.rover.rover.ui.viewmodels.ScreenViewModel
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

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
                40f
            )

            it("should put the directly on top of the row") {
                // we have two
                rendered.coordinatesAndViewModels[0].shouldMatch(
                    RectF(0f, 0f, 40f, 10f),
                    RowViewModel::class.java
                )

                rendered.coordinatesAndViewModels[1].shouldMatch(
                    RectF(0f, 0f, 40f, 10f),
                    RectangleBlockViewModel::class.java
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
                40f
            )

            it("should put the block directly on top of the row") {
                // we have two
                rendered.coordinatesAndViewModels[0].shouldMatch(
                    RectF(0f, 0f, 40f, 10f),
                    RowViewModel::class.java
                )

                rendered.coordinatesAndViewModels[1].shouldMatch(
                    RectF(0f, 0f, 40f, 10f),
                    RectangleBlockViewModel::class.java
                )

                rendered.coordinatesAndViewModels[2].shouldMatch(
                    // check that the rows are stacked properly: 0 + 10 + 42 = 52
                    RectF(0f, 10f, 40f, 52f),
                    RowViewModel::class.java
                )
            }
        }
    }

    given("a screen with a row and some offsets") {
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
    }

    given("a screen with a row with a vertically centered block within") {
        val screen = ModelFactories.emptyScreen().copy(
            rows = listOf(
                ModelFactories.emptyRow().copy(
                    height = Length(UnitOfMeasure.Points, 100.0),
                    blocks = listOf(
                        ModelFactories.emptyRectangleBlock().copy(
                            width = Length(UnitOfMeasure.Points, 10.0),
                            height = Length(UnitOfMeasure.Points, 20.0),
                            verticalAlignment = VerticalAlignment.Middle
                        )
                    )
                )
            )
        )
        val screenViewModel = ScreenViewModel(screen)

        on("rendering") {
            val rendered = screenViewModel.render(
                300f
            )

            it("should center the block inside the row") {
                rendered.coordinatesAndViewModels.first().shouldMatch(
                    RectF(0f, 40f, 300f, 60f),
                    RectangleBlockViewModel::class.java
                )
            }
        }
    }
})

fun Pair<RectF, LayoutableViewModel>.shouldMatch(
    position: RectF,
    type: Class<out LayoutableViewModel>
) {
    this.first.shouldEqual(position)
    this.second.javaClass.shouldEqual(type)
}