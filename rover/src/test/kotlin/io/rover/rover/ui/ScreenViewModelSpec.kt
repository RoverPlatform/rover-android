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
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
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

        given("a screen view model") {
            val screenViewModel = ScreenViewModel(screen)

            on("rendering") {
                val rendered = screenViewModel.render(
                    40
                )

                it("should have laid out the row and block") {
                    System.out.println(rendered.toString())

                    rendered[0].shouldMatch(
                        Rect(0, 0, 40, -10),
                        RowViewModel::class.java
                    )

                    rendered[1].shouldMatch(
                        Rect(0, 0, 40, -10),
                        BlockViewModel::class.java
                    )
//                    rendered.shouldEqual(
//                        listOf(
//                            Pair(
//                                Rect(0, 0, 40, -10),
//                                any()
//                            ),
//                            Pair(
//                                Rect(0, 0, 40, 10),
//                                any()
//                            )
//                        )
//                    )

                    System.out.println("lay out passed")
                }
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