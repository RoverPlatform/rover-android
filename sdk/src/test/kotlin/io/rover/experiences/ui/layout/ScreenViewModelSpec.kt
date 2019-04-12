package io.rover.experiences.ui.layout

import io.rover.sdk.ViewModels
import io.rover.sdk.data.domain.ID
import io.rover.sdk.data.domain.Height
import io.rover.sdk.data.domain.HorizontalAlignment
import io.rover.sdk.data.domain.Position
import io.rover.sdk.data.domain.VerticalAlignment
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockViewModel
import io.rover.sdk.ui.layout.row.RowViewModel
import org.amshove.kluent.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ScreenViewModelSpec : Spek({
    describe("integration tests with real row view models") {
        val viewModels = ViewModels(
            apiService = mock(),
            mainScheduler = mock(),
            eventEmitter = mock(),
            sessionTracker = mock(),
            imageOptimizationService = mock(),
            assetService = mock(),
            measurementService = mock()
        )
        context("a basic screen with one row with a rectangle block") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        height = Height.Static(10.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(0.0, 0.0),
                                    VerticalAlignment.Top(0.0, Height.Static(10.0))
                                )
                            )
                        )
                    )
                )
            )

            val screenViewModel = viewModels.screenViewModel(screen)

            context("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("puts the block directly on top of the row") {
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

        context("a screen with two rows") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        id = ID("1"),
                        height = Height.Static(10.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(0.0, 0.0),
                                    VerticalAlignment.Top(0.0, Height.Static(10.0))
                                )
                            )
                        )
                    ),
                    ModelFactories.emptyRow().copy(
                        id = ID("2"),
                        height = Height.Static(42.0)
                    )
                )
            )
            val screenViewModel = viewModels.screenViewModel(screen)

            context("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("puts the block directly on top of the row") {
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

        context("a screen with a row and a block with a vertical fill offset and a horizontal left offset") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        id = ID("1"),
                        height = Height.Static(100.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Left(
                                        5.0,
                                        20.0
                                    ),
                                    VerticalAlignment.Fill(
                                        10.0,
                                        10.0
                                    )
                                )
                            )
                        )
                    ),
                    ModelFactories.emptyRow().copy(
                        id = ID("2"),
                        height = Height.Static(42.0)
                    )
                )
            )
            val screenViewModel = viewModels.screenViewModel(screen)

            context("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("renders the block with the offsets") {
                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(5f, 10f, 25f, 90f),
                        RectangleBlockViewModel::class.java
                    )
                }
            }
        }

        context("a screen with a row with a vertically centered block within") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        height = Height.Static(100.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(
                                        0.0, 0.0
                                    ),
                                    VerticalAlignment.Middle(
                                        0.0, Height.Static(20.0)
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val screenViewModel = viewModels.screenViewModel(screen)

            context("rendering") {
                val rendered = screenViewModel.render(
                    300f
                )

                it("centers the block inside the row") {
                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(0f, 40f, 300f, 60f),
                        RectangleBlockViewModel::class.java
                    )
                }
            }
        }
    }
})
