package io.rover.rover.ui.viewmodels

import android.graphics.RectF
import io.rover.rover.ModelFactories
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Position
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.ui.BlockViewModelFactory
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

class RowViewModelSpec: Spek({
    given("an autoheight row with stacked blocks") {
        val rowViewModel = RowViewModel(
            ModelFactories
                .emptyRow()
                .copy(
                    autoHeight = true,
                    height = Length(UnitOfMeasure.Points, 0.0),
                    blocks = listOf(
                        ModelFactories.emptyRectangleBlock().copy(
                            position = Position.Stacked,
                            height = Length(UnitOfMeasure.Points, 20.0)
                        ),
                        ModelFactories.emptyRectangleBlock().copy(
                            position = Position.Stacked,
                            height = Length(UnitOfMeasure.Points, 70.0)
                        )
                    )
                ),
            BlockViewModelFactory()
        )

        on("frame()") {
            val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))
            it("should expand its height to contain the blocks") {
                frame.bottom.shouldEqual(90f)
            }
        }

        on("render") {
            val layout = rowViewModel.mapBlocksToRectDisplayList(
                RectF(0f, 0f, 60f, 0f)
            )


            it("should lay out the blocks in vertical order") {
                layout.first().shouldMatch(
                    RectF(0f, 0f, 60f, 20f),
                    RectangleBlockViewModelInterface::class.java
                )
                layout[1].shouldMatch(
                    RectF(0f, 20f, 60f, 90f),
                    RectangleBlockViewModelInterface::class.java
                )
            }
        }
    }

    // given("a fixed row with floating blocks")
})
